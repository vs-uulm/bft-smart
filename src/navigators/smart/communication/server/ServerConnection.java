/**
 * Copyright (c) 2007-2009 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
 * 
 * This file is part of SMaRt.
 * 
 * SMaRt is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * SMaRt is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with SMaRt.  If not, see <http://www.gnu.org/licenses/>.
 */
package navigators.smart.communication.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.communication.MessageHandler;
import navigators.smart.tom.core.messages.SystemMessage;
import navigators.smart.tom.util.TOMConfiguration;
import static navigators.smart.tom.util.Statistics.stats;

/**
 * This class represents a connection with other server.
 *
 * ServerConnections are created by ServerCommunicationLayer.
 *
 * @author alysson
 */
public class ServerConnection {

    private static final Logger log = Logger.getLogger(ServerConnection.class.getName());
    private static final long POOL_TIME = 1000;
    private TOMConfiguration conf;
    private SocketChannel socketchannel;
    private int remoteId;
    private boolean useSenderThread;
    protected BlockingQueue<byte[]> outQueue;// = new LinkedBlockingQueue<byte[]>(SEND_QUEUE_SIZE);
    private BlockingQueue<SystemMessage> inQueue;
    private Lock connectLock = new ReentrantLock();
    /** Only used when there is no sender Thread */
    private Lock sendLock;
    private boolean doWork = true;
    private PTPMessageVerifier ptpverifier;
    @SuppressWarnings("rawtypes")
    private GlobalMessageVerifier globalverifier;
    @SuppressWarnings("rawtypes")
    private final Map<SystemMessage.Type, MessageHandler> msgHandlers;

    private Timer delayTimer;

    @SuppressWarnings("rawtypes")
    public ServerConnection(TOMConfiguration conf, SocketChannel socket, int remoteId,
            BlockingQueue<SystemMessage> inQueue,
            Map<SystemMessage.Type, MessageHandler> msgHandlers,
            PTPMessageVerifier ptpverifier,
            GlobalMessageVerifier verifier) {
        this.msgHandlers = msgHandlers;
        this.conf = conf;
        this.socketchannel = socket;
        this.remoteId = remoteId;
        this.inQueue = inQueue;
        this.outQueue = new ArrayBlockingQueue<byte[]>(this.conf.getOutQueueSize());
        this.ptpverifier = ptpverifier;
        this.globalverifier = verifier;

        if(ptpverifier != null){
            //must be done before the RecieverThread is started because it uses
            //the mac length durin init
            ptpverifier.authenticateAndEstablishAuthKey();
        }
        
        this.useSenderThread = conf.isUseSenderThread();

        if (useSenderThread) {
            //log.log(Level.INFO, "Using sender thread.");
            new SenderThread().start();
        } else {
            sendLock = new ReentrantLock();
        }

        new ReceiverThread().start();
    }

    /**
     * Stop message sending and reception.
     */
    public void shutdown() {
        doWork = false;
        closeSocket();
    }

    /**
     * Used to send packets to the remote server.
     * @param data The data to send
     * @throws InterruptedException
     */
    public final void send(byte[] data) throws InterruptedException {
        if (socketchannel != null) {
            if (useSenderThread) {
                //only enqueue messages if there queue is not full
                if (!outQueue.offer(data)) {
                    if (log.isLoggable(Level.FINE)) {
                        log.fine("out queue for " + remoteId + " full (message discarded).");
                    }
                }
            } else {
                sendLock.lock();
                sendBytes(data);
                sendLock.unlock();
            }
        } else {
            if (log.isLoggable(Level.FINER)) {
                log.finer("Connection to " + remoteId + " currently not established - not sending msg to it");
            }
        }
    }

    /**
     * try to send a message through the socket
     * if some problem is detected, a reconnection is done
     */
    private final void sendBytes(byte[] messageData) {
        int i = 0;
        do {
            if (socketchannel != null /*&& socketOutStream != null*/) {
                try {
                    ByteBuffer buf = ByteBuffer.allocate(4);
                    buf.putInt(messageData.length);
                    buf.flip();
                    socketchannel.write(buf);
                    buf = ByteBuffer.wrap(messageData);
                    while (buf.hasRemaining()) {
                        socketchannel.write(buf);
                    }
                    if (ptpverifier != null) {
                        socketchannel.write(ByteBuffer.wrap(ptpverifier.generateHash(messageData)));
                    }
                    stats.sentMsgToServer(remoteId);
                    return;
                } catch (IOException ex) {
                    log.log(Level.SEVERE, null, ex);

                    closeSocket();

                    waitAndConnect();
                }
            } else {
                waitAndConnect();
            }
            i++;
        } while (true);
    }

    /**
     * (Re-)establish connection between peers.
     *
     * @param newSocket socket created when this server accepted the connection
     * (only used if processId is less than remoteId)
     */
    protected void reconnect(SocketChannel newSocket) {
        connectLock.lock();

        if (socketchannel == null || !socketchannel.isConnected()) {
            try {
                if (conf.getProcessId() > remoteId) {
                    initSocketChannel();
                } else {
                    socketchannel = newSocket;
                }
            } catch (UnknownHostException ex) {
                log.log(Level.SEVERE, "Error connecting", ex);
            } catch (IOException ex) {
//                log.log(Level.SEVERE, "Error connecting", ex); ignore and retry
            }

            if (socketchannel != null) {
                if (log.isLoggable(Level.INFO)) {
                    log.fine("Reconnected to " + remoteId);
                }
            }
        }

        connectLock.unlock();
    }

    private void initSocketChannel() throws IOException {
        this.socketchannel = SocketChannel.open(new InetSocketAddress(conf.getHost(remoteId), conf.getPort(remoteId)));
        if(socketchannel != null){
        socketchannel.configureBlocking(true);
        ServersCommunicationLayer.setSocketOptions(this.socketchannel.socket());
        ByteBuffer out = ByteBuffer.allocate(4);
        out.putInt(conf.getProcessId());
        out.flip();
        socketchannel.write(out);
    }
    }

    private void closeSocket() {
        if (socketchannel != null) {
            try {
                socketchannel.close();
            } catch (IOException ex) {
                log.log(Level.SEVERE, null, ex);
            }

            socketchannel = null;
//            socketOutStream = null;
//            socketInStream = null;
        }
    }

    private void waitAndConnect() {
        if (doWork) {
            try {
                if(log.isLoggable(Level.FINE))
                    log.fine("Waiting to connect to "+remoteId);
                Thread.sleep(POOL_TIME);
            } catch (InterruptedException ie) {
            }

            reconnect(null);
        }
    }

    /**
     * Thread used to send packets to the remote server.
     */
    private class SenderThread extends Thread {
        private boolean delaySending;
        private long delay;

        public SenderThread() {
            super("Sender for " + remoteId);
            delay = conf.getSendDelay();
            delaySending = delay > 0;
            if(delaySending){
                delayTimer = new Timer("DelayTimer for "+remoteId);
                delayTimer.schedule(new TimerTask(){
                    @Override
                    public void run() {
                        Thread.currentThread().setPriority(MAX_PRIORITY);
                    }
                }, 5);
            }
        }

        @Override
        public void run() {
            byte[] data = null;

            while (doWork) {
                //get a message to be sent
                try {
                    data = outQueue.poll(POOL_TIME, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ex) {
                }

                if (data != null) {
                    if(delaySending){
                        System.out.println("["+remoteId+"]Undelayed sendtime "+ System.currentTimeMillis());
                        delayTimer.schedule(new DelayTask(data), delay);
                    } else {
                        sendBytes(data);
                    }
                }
            }

            log.log(Level.INFO, "Sender for " + remoteId + " stopped!");
        }
    }

    /**
     * Thread used to receive packets from the remote server.
     */
    protected class ReceiverThread extends Thread {

        private ByteBuffer receivedHash;    //array to store the received hashes
        //will hold the verificationresult when globalverification is used
        private Object verificationresult = null;

        public ReceiverThread() {
            super("Receiver for " + remoteId);
            if (ptpverifier != null) {
                receivedHash = ByteBuffer.allocate(ptpverifier.getHashSize());
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public void run() {
            ByteBuffer buf = ByteBuffer.allocate(256);

            while (doWork) {
                if (socketchannel != null /*&& socketInStream != null*/) {
                    try {
                        buf.clear();
                        buf.limit(4);
                        //read data length
                        if (socketchannel.read(buf) == -1) {
                            throw new IOException("Reached eof while waiting for data");
                        }

                        buf.flip();
                        int dataLength = buf.getInt();

                        if (log.isLoggable(Level.FINEST)) {
                            log.finest("Receiving msg of size" + dataLength + " from " + remoteId);
                        }

//                        if(dataLength>1024){
//                        	log.severe("Datalength got huge: "+dataLength);
//                        }
                        if (buf.capacity() < dataLength) {
                            if (log.isLoggable(Level.FINE)) {
                                log.fine("Adjusting buffer to new max datalength: " + dataLength);
                            }
                            buf = ByteBuffer.allocate(dataLength);
                        } else {
                            buf.limit(dataLength);
                        }
                        stats.receivedMsg(remoteId);
                        buf.rewind();
                        //read data
                        while (buf.hasRemaining()) {
                            if (socketchannel.read(buf) == -1) {
                                throw new IOException("Reached eof while waiting for data");
                            }
                        }
                        buf.rewind();

                        //check verifcation
                        boolean verified = checkverfication(buf);

                        if (verified) {
                            SystemMessage.Type type = SystemMessage.Type.getByByte(buf.get(0));
                            assert (msgHandlers.containsKey(type)) : "Messagehandlers does not contain " + type + ". It contains: " + msgHandlers;
                            SystemMessage sm = msgHandlers.get(type).deserialise(type, buf, verificationresult);
                            stats.decodedMsg(remoteId,sm);

                            if (log.isLoggable(Level.FINEST)) {
                                log.finest("Received " + sm);
                            }

                            if (sm.getSender() == remoteId) {
                                if (!inQueue.offer(sm)) {
                                    navigators.smart.tom.util.Logger.println("(ReceiverThread.run) in queue full (message from " + remoteId + " discarded).");
                                }
                            }
                        } else {
                            //TODO: violation of authentication... we should do something
                            log.severe("Received bad " + Arrays.toString(Arrays.copyOfRange(buf.array(), 0, buf.limit() > 100 ? 100 : buf.limit())) + " from " + remoteId);
                            log.severe("Limit is: " + buf.limit());
                        }

                    } catch (ClassNotFoundException ex) {
                        log.log(Level.SEVERE, "Should never happen,", ex);
                    } catch (SocketException e) {
                        log.log(Level.FINE, "Socket reset. Reconnecting...");

                        closeSocket();

                        waitAndConnect();
                    } catch (IOException ex) {
                        log.log(Level.SEVERE, "IO Error. Reconnecting...", ex);

                        closeSocket();

                        waitAndConnect();
                    }
                } else {
                    waitAndConnect();
                }
            }

            log.log(Level.INFO, "Receiver for " + remoteId + " stopped!");
        }

        /**
         * Verifies the received message to be authentic and unmodified.
         * Depending on the selected type of verification the message is
         * verified directly or it is handed to a global external verifier
         * which hands back the verificationresult (sequence numbers e.g.)
         * @param buf The buffer containing the message
         * @return true if the verification succeeded false otherwise
         * @throws IOException When sth fails during reading from the socketchannel
         */
        private boolean checkverfication(ByteBuffer buf) throws IOException{
            boolean verified = false;
            switch (conf.getVerifierType()) {
                case PTPVerifier:
                    socketchannel.read(receivedHash);
                    verified = ptpverifier.verifyHash(buf, receivedHash);
                    receivedHash.rewind(); // reset hash buffer
                    break;
                case GlobalVerifier:
                    //sets the verificationresult to the new result or to null
                    //depending wheter the msg is valid or not
                    verificationresult = globalverifier.verifyHash(buf);
                    if (verificationresult != null) {
                        verified = true;
                    }
                    break;
                case None:
                    verified = true;
                    break;
                default:
                    // if the verificationtype is something unknown deny verification
                    verified = false;
                    log.warning("Unknown verificationtype selected! Did you forget to" +
                            "update ServerConnection.java?");
            }
            return verified;
        }
    }

    /**
     * Delays the sending of a packet by a given amount of time to simulate
     * wan conditions. Very basic but better than nothing.
     */
    private class DelayTask extends TimerTask{
        private final byte [] data;
        public DelayTask(byte[] data){
            this.data = data;
        }

        @Override
        public void run() {System.out.println(
            "["+remoteId+"]Delayed sendtime "+ System.currentTimeMillis());
            sendBytes(data);
        }
    }
}
