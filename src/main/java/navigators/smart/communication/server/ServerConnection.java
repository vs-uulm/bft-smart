/* * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa,  * and the authors indicated in the @author tags  *   * Licensed under the Apache License, Version 2.0 (the "License");  * you may not use this file except in compliance with the License.  * You may obtain a copy of the License at  *   * http://www.apache.org/licenses/LICENSE-2.0  *   * Unless required by applicable law or agreed to in writing, software  * distributed under the License is distributed on an "AS IS" BASIS,  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  * See the License for the specific language governing permissions and  * limitations under the License.  */package navigators.smart.communication.server;import static navigators.smart.tom.util.Statistics.stats;import java.io.IOException;import java.net.InetSocketAddress;import java.net.SocketException;import java.net.UnknownHostException;import java.nio.ByteBuffer;import java.nio.channels.AsynchronousCloseException;import java.nio.channels.SocketChannel;import java.util.Map;import java.util.concurrent.ArrayBlockingQueue;import java.util.concurrent.BlockingQueue;import java.util.concurrent.CountDownLatch;import java.util.concurrent.Executors;import java.util.concurrent.LinkedBlockingQueue;import java.util.concurrent.PriorityBlockingQueue;import java.util.concurrent.RejectedExecutionException;import java.util.concurrent.ScheduledExecutorService;import java.util.concurrent.Semaphore;import java.util.concurrent.ThreadFactory;import java.util.concurrent.TimeUnit;import java.util.concurrent.locks.Condition;import java.util.concurrent.locks.Lock;import java.util.concurrent.locks.ReentrantLock;import java.util.logging.Level;import java.util.logging.Logger;import navigators.smart.communication.MessageHandler;import navigators.smart.communication.ServerCommunicationSystem;import navigators.smart.tom.core.messages.SystemMessage;import navigators.smart.tom.core.messages.SystemMessage.Type;import navigators.smart.tom.util.TOMConfiguration;/** * This class represents a connection with other server. * * ServerConnections are created by ServerCommunicationLayer. * * @author alysson */public class ServerConnection {	public static final String DELAY_PROPERTY = "smart.serverconnection.senddelay";	private static final Logger log = Logger.getLogger(ServerConnection.class.getName());	private static final Logger delaylog = Logger.getLogger(ServerConnection.class.getName() + ".delaylogger");//	private static final long POOL_TIME = 1000;	private TOMConfiguration conf;	private SocketChannel socketchannel;	private int remoteId;	private boolean useSenderThread;	protected final BlockingQueue<byte[]> outQueue;// = new LinkedBlockingQueue<byte[]>(SEND_QUEUE_SIZE);	private BlockingQueue<SystemMessage> inQueue;	private final Semaphore msgIndicator;	private final Lock connectLock = new ReentrantLock();	private final Condition reconnIndicator = connectLock.newCondition();	/**	 * Only used when there is no sender Thread	 */	private Lock sendLock;	private volatile boolean doWork = true;	private PTPMessageVerifier ptpverifier;	@SuppressWarnings("rawtypes")	private GlobalMessageVerifier globalverifier;	@SuppressWarnings("rawtypes")	private final Map<SystemMessage.Type, MessageHandler> msgHandlers;	private ScheduledExecutorService delayTimer;	private final BlockingQueue<byte[]> delayQueue = new LinkedBlockingQueue<byte[]>();	private final ConnectionMonitor cm;	private final CountDownLatch shutdownbarrier;	private final SenderThread st;	private final ReceiverThread rt;	@SuppressWarnings("rawtypes")	public ServerConnection(TOMConfiguration conf, SocketChannel socket, int remoteId,			PriorityBlockingQueue<SystemMessage> inQueue, Semaphore msgIndicator,			Map<SystemMessage.Type, MessageHandler> msgHandlers,			PTPMessageVerifier ptpverifier,			GlobalMessageVerifier verifier,			ConnectionMonitor cm) {		this.msgHandlers = msgHandlers;		this.conf = conf;		this.socketchannel = socket;		this.remoteId = remoteId;		this.inQueue = inQueue;		this.msgIndicator = msgIndicator;		this.outQueue = new ArrayBlockingQueue<byte[]>(this.conf.getOutQueueSize());		this.ptpverifier = ptpverifier;		this.globalverifier = verifier;		this.cm = cm;		if (ptpverifier != null) {			//must be done before the RecieverThread is started because it uses			//the mac length durin init			ptpverifier.authenticateAndEstablishAuthKey();		}		this.useSenderThread = conf.isUseSenderThread();		if (useSenderThread) {			shutdownbarrier = new CountDownLatch(2);			//log.log(Level.INFO, "Using sender thread.");			st = new SenderThread();			st.start();		} else {			st = null;			shutdownbarrier = new CountDownLatch(1);			sendLock = new ReentrantLock();		}		rt = new ReceiverThread();		rt.start();	}	/**	 * Stop message sending and reception.	 */	public void shutdown() {		doWork = false;		outQueue.clear();		inQueue.clear();		if (st != null) {			st.interrupt();		}				if(delayTimer != null){			delayTimer.shutdown();		}		try{			connectLock.lock();			reconnIndicator.signalAll();				} finally {		connectLock.unlock();		}		try {			shutdownbarrier.await(100,TimeUnit.MILLISECONDS);		} catch (InterruptedException e) {			log.severe("Interrupted while waiting for threads to finish");			e.printStackTrace();		}		closeSocket();	}	/**	 * Used to send packets to the remote server.	 *	 * @param data The data to send	 * @throws InterruptedException	 */	public final void send(byte[] data) throws InterruptedException {		if (socketchannel != null && doWork) {			if (useSenderThread) {				//only enqueue messages if there queue is not full				if (!outQueue.offer(data)) {					log.log(Level.WARNING, "out queue for {0} full (message discarded).", remoteId);				}			} else {				try {					sendLock.lock();					sendBytes(data);				} finally {					sendLock.unlock();				}			}		} else {			log.log(Level.FINER, "Connection to {0} currently not established - not sending msg to it", remoteId);		}	}	/**	 * try to send a message through the socket if some problem is detected, a reconnection is done	 */	private void sendBytes(byte[] messageData) {		do {			if (socketchannel != null && doWork) {				try {					ByteBuffer buf = ByteBuffer.allocate(4);					buf.putInt(messageData.length);					buf.flip();					socketchannel.write(buf);					buf = ByteBuffer.wrap(messageData);					while (buf.hasRemaining()) {						socketchannel.write(buf);					}					if (ptpverifier != null) {						byte[] hash = ptpverifier.generateHash(messageData);						socketchannel.write(ByteBuffer.wrap(hash));						if (log.isLoggable(Level.FINEST)) {							log.log(Level.FINEST, "sent hash:{0} to {1}", new Object[]{hash, remoteId});						}					}					if (log.isLoggable(Level.FINEST)) {						log.log(Level.FINEST, "sent {0} bytes to {1}", new Object[]{messageData.length, remoteId});					}					stats.sentMsgToServer(remoteId);					return;				} catch (IOException ex) {					log.log(Level.SEVERE, null, ex);					// close and null socketchannel so reconnection is going to 					// occur					closeSocket();				}			} else if(doWork) {								log.info("No connection established, trying reconnection in 1 sec");				try {					Thread.sleep(1000);				} catch (InterruptedException e) {					log.severe(e.getLocalizedMessage());				}				connect();			} else {				//shutdown... nothing to do.			}		} while (doWork);	}	/**	 * Establish connection between peers. If target has higher id, we wait	 * for a connection, otherwise we try to initiate one.	 *	 * @param newSocket socket created when this server accepted the connection 	 * (only used if processId is less than remoteId)	 */	protected void connect() {		try {			connectLock.lock();			log.fine("Connection process started");			if (conf.getProcessId() < remoteId) {				while (doWork && (socketchannel == null || !socketchannel.isConnectionPending()						|| !socketchannel.isConnected())) {					try {						log.info("Awaiting connection from "+remoteId);						reconnIndicator.await();					} catch (InterruptedException ire) {					}				}			} else {				while (doWork && (socketchannel == null || !socketchannel.isConnected())) {					try {						log.info("Connecting to "+remoteId);						initSocketChannel();					} catch (UnknownHostException ex) {						log.log(Level.SEVERE, "Error connecting", ex);					} catch (IOException ex) {//                log.log(Level.SEVERE, "Error connecting", ex); ignore and retry					}				}				if (socketchannel != null) {					log.log(Level.INFO, "Connected to {0}", remoteId);				} else {					log.warning("Connection to "+remoteId + "failed");				}			}		} finally {			connectLock.unlock();		}	}		protected void gotConnection(SocketChannel newSocket){		try {			connectLock.lock();			closeSocket();			socketchannel = newSocket;			reconnIndicator.signalAll();		} finally {			connectLock.unlock();		}	}	private void initSocketChannel() throws IOException {		socketchannel = SocketChannel.open(new InetSocketAddress(conf.getHost(remoteId), conf.getPort(remoteId)));		if (socketchannel != null) {			// Setup channel and send id			socketchannel.configureBlocking(true);			ServersCommunicationLayer.setSocketOptions(socketchannel.socket());			ByteBuffer out = ByteBuffer.allocate(4);			out.putInt(conf.getProcessId());			out.flip();			socketchannel.write(out);			// Tell monitor that we connected.			cm.connected(remoteId);		}	}	private void closeSocket() {		if (socketchannel != null) {			try {				socketchannel.close();			} catch (IOException ex) {				log.log(Level.SEVERE, null, ex);			}			socketchannel = null;//            socketOutStream = null;//            socketInStream = null;		}	}//	private void waitAndConnect() {//		if (doWork) {//			try {//				log.log(Level.FINEST, "Waiting to connect to {0}", remoteId);//				Thread.sleep(POOL_TIME);//			} catch (InterruptedException ie) {//			}////			reconnect(null);//		}//	}	/**	 * Thread used to send packets to the remote server.	 */	private class SenderThread extends Thread {		private boolean delaySending;		private long delay;		public SenderThread() {			super("Sender for " + remoteId);			setDaemon(true);						//It is important that this is a SingleThreadExecutor!			delayTimer = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {				@Override				public Thread newThread(Runnable r) {					Thread t = new Thread(r);					t.setDaemon(true);					t.setPriority(MAX_PRIORITY);					return t;				}			});			String sdelay = System.getProperty(DELAY_PROPERTY);			if( sdelay != null){				delay = Long.parseLong(sdelay);			} else {				delay = conf.getSendDelay();			}			delaySending = delay > 0;			if (delaySending) {				log.info("Senddelay set to "+delay+" us");				log.info("delaylogger: "+delaylog.getName()+" level: "+delaylog.getLevel());								}		}		@Override		public void run() {//			byte[] data = null;			ServerCommunicationSystem.setThreadPriority(this);			while (doWork) {				// get a message to be sent				try {					byte[] data = outQueue.take(); //poll(POOL_TIME, TimeUnit.MILLISECONDS);					if (data != null) {							if (delaySending) {							if (delaylog.isLoggable(Level.FINEST)) {								delaylog.log(										Level.FINEST,										"[{0} Sender] Undelayed sendtime {1}",										new Object[] { remoteId, System.nanoTime() });							}							delayQueue.offer(data);							try {								delayTimer.schedule(new DelayTask(), delay,										TimeUnit.MICROSECONDS);							} catch(RejectedExecutionException ree){								log.info(ree.getLocalizedMessage());							}						} else {							sendBytes(data);						}					}				} catch (InterruptedException ex) {					//Do nothing.				}			}			shutdownbarrier.countDown();			log.log(Level.FINE, "Sender for {0} stopped!", remoteId);		}	}	/**	 * Thread used to receive packets from the remote server.	 */	protected class ReceiverThread extends Thread {		private ByteBuffer receivedHash;    //array to store the received hashes		//will hold the verificationresult when globalverification is used//		private Object verificationresult = null;		public ReceiverThread() {			super("Receiver for " + remoteId);			setDaemon(true);			if (ptpverifier != null) {				receivedHash = ByteBuffer.allocate(ptpverifier.getHashSize());			}		}		@SuppressWarnings("unchecked")		@Override		public void run() {			ByteBuffer buf = ByteBuffer.allocate(2048);			ServerCommunicationSystem.setThreadPriority(this);			while (doWork) {				if (socketchannel != null) {					try {						buf.clear();						buf.limit(4);												while(buf.hasRemaining()){							//read data length							if (socketchannel.read(buf) == -1) {								throw new IOException("Reached eof while waiting for data");							}						}						buf.flip();						int dataLength = buf.getInt();						//						if(dataLength < 0){//							// We are done//							break;//						}						log.log(Level.FINEST, "Receiving msg of size{0} from {1}", new Object[]{dataLength, remoteId});						if (buf.capacity() < dataLength) {							if(log.isLoggable(Level.FINE)){								log.log(Level.FINE,										"{1}: Adjusting buffer to new max datalength: {0}",										new Object[] { dataLength, remoteId });							}							buf = ByteBuffer.allocate(dataLength);						} else {							buf.limit(dataLength);						}						buf.rewind();						//read data						while (buf.hasRemaining()) {							if (socketchannel.read(buf) == -1) {								throw new IOException("Reached eof while waiting for data");							}						}												if(!doWork){							// leave loop here							break;						}												stats.receivedMsg(remoteId);						buf.rewind();												//FIXME store verifier with each msghandler						SystemMessage.Type type = SystemMessage.Type.getByByte(buf.get(0));						buf.rewind();						//check verifcation						Object verified = checkverfication(buf,type);						//						if (verified) {//							assert (msgHandlers.containsKey(type)) : "Messagehandlers does not contain " + type + ". It contains: " + msgHandlers;							if(msgHandlers.containsKey(type)){								log.finest("Starting to deserialise "+type);								SystemMessage sm = msgHandlers.get(type).deserialise(type, buf, verified);								if(sm == null ){									log.warning(remoteId+": Deserialisation for "+type+" failed: discarding");									continue;//									throw new IOException(remoteId+": Deserialisation failed");								}																log.finest(remoteId+": Finished deserialisation");								stats.decodedMsg(remoteId, sm);								if (log.isLoggable(Level.FINE)) {									log.log(Level.FINE, "[{0} Recv] Received {1}", new Object[]{remoteId, sm});								}								if (sm.getSender() == remoteId) {									//Throttle reception if handler thread is slow									try {										inQueue.put(sm);										log.fine("Receiver for "+remoteId+" starts to wait for token");										msgIndicator.acquire();										log.fine("Receiver for "+remoteId+" aquired token");									} catch (InterruptedException ire){										log.info("Receiver for "+remoteId+"got interrupted while waiting to deliver message");									}//									if (!inQueue.offer(sm,)) {//										log.severe("Inqueue full (message from " + remoteId + " discarded).");//									}								}							} else {								log.log(Level.WARNING,"MsgHandler for {0} not found",type);															}//						} else {//							//TODO: violation of authentication... we should do something//							StringBuilder output = new StringBuilder();//							byte[] data = Arrays.copyOfRange(buf.array(), 0, buf.limit() > 100 ? 100 : buf.limit());//							for (byte b : data) {//								String bs = Integer.toBinaryString(b);//								output.append(bs).append(" ");//							}//							log.log(Level.SEVERE, "Received bad {0} from {1}", new Object[]{output, remoteId});//							log.log(Level.SEVERE, "Limit is: {0}", buf.limit());//						}					} catch (ClassNotFoundException ex) {						log.log(Level.SEVERE, "Should never happen,", ex);					} catch (SocketException e) {						log.log(Level.WARNING, "Socket reset. Reconnecting...");						closeSocket();					} catch (AsynchronousCloseException ace){						// Closing, set dowork to false						doWork = false;					} catch (IOException ex) {						if (ex.getMessage() == null								|| !(ex.getMessage().contains("eof") || ex										.getMessage().contains(												"Connection reset by peer"))) {							log.log(Level.WARNING,									"IO Error. Closing socket...", ex);							ex.printStackTrace();						} else {							log.log(Level.INFO, "IO Error. Shutting down thread: "+ex.getMessage(), ex);							doWork = false;						}						closeSocket();					}				} else {					connect();				}							}			shutdownbarrier.countDown();			log.log(Level.FINE, "Receiver for {0} stopped!", remoteId);		}		/**		 * Verifies the received message to be authentic and unmodified. Depending on the selected type of verification the message is verified		 * directly or it is handed to a global external verifier which hands back the verificationresult (sequence numbers e.g.)		 *		 * @param buf The buffer containing the message		 * @return true if the verification succeeded false otherwise		 * @throws IOException When sth fails during reading from the socketchannel		 */		private Object checkverfication(ByteBuffer buf, Type type) throws IOException {			if(log.isLoggable(Level.FINEST)){				log.finest("Starting verification");			}			Object verified = Boolean.FALSE;			switch (conf.getVerifierType()) {				case PTPVerifier:					while (receivedHash.hasRemaining()) {						if (socketchannel.read(receivedHash) == -1) {							receivedHash.rewind();							throw new IOException("Reached eof while waiting for data");						}					}					verified = ptpverifier.verifyHash(buf, receivedHash);					receivedHash.rewind(); // reset hash buffer					break;				case GlobalVerifier:					//sets the verificationresult to the new result or to null					//depending wheter the msg is valid or not					if(type.equals(Type.PAXOS_MSG)){						verified = globalverifier.verifyHash(buf);					}//					if (verificationresult != null) {//						verified = true;//					}					break;				case None:					verified = true;					break;				default:					// if the verificationtype is something unknown deny verification					verified = false;					log.warning("Unknown verificationtype selected! Did you forget to"							+ "update ServerConnection.java?");			}			if(log.isLoggable(Level.FINEST)){				log.finest("Verification done");			}			return verified;		}	}	/**	 * Delays the sending of a packet by a given amount of time to simulate wan conditions. Very basic but better than nothing.	 */	private class DelayTask implements Runnable {		@Override		public void run() {			if(delaylog.isLoggable(Level.FINEST)){				delaylog.log(Level.FINEST, "[{0}]Delayed sendtime {1}", new Object[]{remoteId, System.nanoTime()});			}			sendBytes(delayQueue.poll());		}	}}