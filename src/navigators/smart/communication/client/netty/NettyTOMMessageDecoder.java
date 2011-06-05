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
package navigators.smart.communication.client.netty;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.Configuration;
import navigators.smart.tom.util.TOMConfiguration;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.handler.codec.frame.FrameDecoder;

/**
 *
 * @author Paulo Sousa
 */
@ChannelPipelineCoverage("one")
public class NettyTOMMessageDecoder extends FrameDecoder {

    private static final Logger log = Logger.getLogger(NettyTOMMessageDecoder.class.getCanonicalName());

    private boolean isClient;
    private Hashtable<Integer, NettyClientServerSession> sessionTable;
    private SecretKey authKey;
    private int macSize;
    private int signatureSize;
    private Configuration conf;
    private ReentrantReadWriteLock rl;
    private Signature signatureEngine;
    private boolean useMAC;
    private boolean connected;

    public NettyTOMMessageDecoder(boolean isClient, Hashtable<Integer, NettyClientServerSession> sessionTable, SecretKey authKey, int macLength, Configuration conf, ReentrantReadWriteLock rl, int signatureLength, boolean useMAC) {
        this.isClient = isClient;
        this.sessionTable = sessionTable;
        this.authKey = authKey;
        //this.st = new Storage(benchmarkPeriod);
        this.macSize = macLength;
        this.conf = conf;
        this.rl = rl;
        this.signatureSize = signatureLength;
        this.useMAC = useMAC;
        connected = isClient; //servers wait for connections, clients establish them and are connected by default
        if(log.isLoggable(Level.FINEST))
            log.finest("new NettyTOMMessageDecoder!!, isClient=" + isClient);
    }

    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer) {

        // Wait until the length prefix is available.
        if (buffer.readableBytes() < 4) {
            return null;
        }
        buffer.markReaderIndex();

        int dataLength = buffer.readInt();
        //establish connection
        if (!connected) {
            // we got id of the client, skip bytes, init connection and finish
            //the dataLength field holds the id of the sender in this case
            connected = initConnection(dataLength, channel);
            return null;
        }

        // Wait until the whole data is available.
        if (buffer.readableBytes() < dataLength) {
            buffer.resetReaderIndex();

            return null;
        }
        //calculate lenght without signed indication bit
        int totalLength = dataLength - 1;

        //read control byte indicating if message is signed
        byte signed = buffer.readByte();

        int authLength = 0;

        if (signed == 1) {
            authLength += signatureSize;
        }
        if (useMAC) {
            authLength += macSize;
        }

        byte[] data = new byte[totalLength - authLength];
        buffer.readBytes(data);

        TOMMessage sm = new TOMMessage(ByteBuffer.wrap(data));

        byte[] digest = null;
        if (useMAC) {
            digest = new byte[macSize];
            buffer.readBytes(digest);
        }

        byte[] signature = null;
        if (signed == 1) {
            signature = new byte[signatureSize];
            buffer.readBytes(signature);
        }
        sm.setBytes(data);
        if (signed == 1) {
            sm.serializedMessageSignature = signature;
            sm.signed = true;
        }
        if (useMAC) {
            sm.serializedMessageMAC = digest;
        }

        if (isClient) {
            //verify MAC
            if (useMAC) {
                if (!verifyMAC(sm.getSender(), data, digest)) {
                    Logger.getLogger(NettyTOMMessageDecoder.class.getName()).log(Level.WARNING, "MAC error: message discarded");
                    return null;
                }
            }
        } else { /* it's a server */
            //verifies MAC if it's not the first message received from the client
            rl.readLock().lock();
            if (sessionTable.containsKey(sm.getSender())) {
                rl.readLock().unlock();
                if (useMAC) {
                    if (!verifyMAC(sm.getSender(), data, digest)) {
                        Logger.getLogger(NettyTOMMessageDecoder.class.getName()).log(Level.WARNING, "MAC error: message discarded");
                        return null;
                    }
                }
            } else {
                rl.readLock().unlock();
                Logger.getLogger(NettyTOMMessageDecoder.class.getName()).log(Level.WARNING, "Got message from unestablished connection");
            }
        }
        return sm;
    }

    public boolean initConnection(int sender, Channel channel) {
        try {
            rl.readLock().lock();
            boolean connectedalready = sessionTable.containsKey(sender);
            rl.readLock().unlock();
            if(connectedalready){
                log.severe("Somebody else with this id is already connected!");
                ChannelFuture f = channel.close();
                try {
                    f.await();
                } catch (InterruptedException ex) {
                    Logger.getLogger(NettyTOMMessageDecoder.class.getName()).log(Level.SEVERE, null, ex);
                }
                return false;
            }
            // creates MAC/publick key stuff if it's the first message received
            // from the client
            navigators.smart.tom.util.Logger.println("Creating MAC/public key stuff, first message from client" + sender);
            navigators.smart.tom.util.Logger.println("sessionTable size=" + sessionTable.size());
            Mac macSend = Mac.getInstance(conf.getHmacAlgorithm());
            macSend.init(authKey);
            Mac macReceive = Mac.getInstance(conf.getHmacAlgorithm());
            macReceive.init(authKey);
            NettyClientServerSession cs = new NettyClientServerSession(channel, macSend, macReceive, sender, TOMConfiguration.getRSAPublicKey(sender), new ReentrantLock());
            rl.writeLock().lock();
            sessionTable.put(sender, cs);
            rl.writeLock().unlock();
            if(log.isLoggable(Level.FINER))
                    log.fine("#active clients " + sessionTable.size());
            return true;
        } catch (InvalidKeyException ex) {
            Logger.getLogger(NettyTOMMessageDecoder.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(NettyTOMMessageDecoder.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }

    boolean verifyMAC(int id, byte[] data, byte[] digest) {
        rl.readLock().lock();
        Mac macReceive = sessionTable.get(id).getMacReceive();
        rl.readLock().unlock();
        synchronized(macReceive){
            return Arrays.equals(macReceive.doFinal(data), digest);
        }
    }

    boolean verifySignature(int id, byte[] data, byte[] digest) {
        //long startInstant = System.nanoTime();
        rl.readLock().lock();
        PublicKey pk = sessionTable.get(id).getPublicKey();
        rl.readLock().unlock();
        //long duration = System.nanoTime() - startInstant;
        //st.store(duration);
        return verifySignatureAux(pk, data, digest);
    }

    /**
     * Verify the signature of a message.
     *
     * @param key the public key to be used to verify the signature
     * @param message the signed message
     * @param signature the signature to be verified
     * @return the signature
     */
    public boolean verifySignatureAux(PublicKey key, byte[] message, byte[] signature) {
//        long startTime = System.nanoTime();
        try {
            if (signatureEngine == null) {
                signatureEngine = Signature.getInstance("SHA1withRSA");
            }

            signatureEngine.initVerify(key);

            signatureEngine.update(message);

            boolean result = signatureEngine.verify(signature);
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
