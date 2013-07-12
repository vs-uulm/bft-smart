/* * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, 
 * and the authors indicated in the @author tags 
 *  
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 *  
 * http://www.apache.org/licenses/LICENSE-2.0 
 *  
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License. 
 */
package navigators.smart.communication.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.communication.MessageHandler;
import navigators.smart.communication.ServerCommunicationSystem;
import navigators.smart.tom.core.messages.SystemMessage;
import navigators.smart.tom.util.TOMConfiguration;
import static navigators.smart.tom.util.Statistics.stats;

/**
 *
 * @author alysson
 * @author Christian Spann 
 */
public class ServersCommunicationLayer extends Thread implements ConnectionMonitor{

	private static final Logger log = Logger.getLogger(ServersCommunicationLayer.class.getCanonicalName());
	private TOMConfiguration conf;
	private BlockingQueue<SystemMessage> inQueue;
	private ServerConnection[] connections;
	private ServerSocketChannel serverSocket;
	private int me;
	private boolean doWork = true;
	@SuppressWarnings("rawtypes")
	private final Map<SystemMessage.Type, MessageHandler> msgHandlers;
	private MessageVerifierFactory<PTPMessageVerifier> verifierfactory;
	/**
	 * Holds the global verifier reference
	 */
	private GlobalMessageVerifier<SystemMessage> globalverifier;
	private volatile boolean started = false;
	private HashSet<Integer> connectedcons;

	@SuppressWarnings("rawtypes")
	public ServersCommunicationLayer(TOMConfiguration conf,
			BlockingQueue<SystemMessage> inQueue,
			Map<SystemMessage.Type, MessageHandler> msgHandlers,
			MessageVerifierFactory<PTPMessageVerifier> verifierfactory,
			GlobalMessageVerifier<SystemMessage> globalverifier) throws IOException {
		super("ServersCommunicationLayer");
		setDaemon(true);
		this.conf = conf;
		this.inQueue = inQueue;
		this.me = conf.getProcessId();
		this.msgHandlers = msgHandlers;
		this.verifierfactory = verifierfactory;
		this.globalverifier = globalverifier;
		connections = new ServerConnection[conf.getN()];
		connectedcons = new HashSet<Integer>(conf.getN());

		serverSocket = ServerSocketChannel.open();
		serverSocket.socket().setSoTimeout(10000);
		serverSocket.socket().setReuseAddress(true);
		serverSocket.socket().bind(new InetSocketAddress(conf.getPort(conf.getProcessId())));
	}

	@Override
	public void start() {
		ServerCommunicationSystem.setThreadPriority(this);
		super.start();

		synchronized(connectedcons){
			// Wait for all connections to be established once
			while(connectedcons.size()<conf.getN()-1){
				try {
						connectedcons.wait();
				} catch (InterruptedException e) {
					log.severe(e.getMessage() + ": Got interrupted while waiting for other connections!");
				}
			}
			started = true;
		}
	}

	@SuppressWarnings("boxing")
	public final void send(Integer[] targets, SystemMessage sm) {
		stats.sendingMsgToServer(targets, sm);
		byte[] data = sm.getBytes();
		
		//send in random order to prevent lagging behind
		Collections.shuffle(Arrays.asList(targets));
		
		for (int i : targets) {
			if (log.isLoggable(Level.FINEST)) {
				log.finest("Sending " + sm + " to " + i);
			}
			// br.ufsc.das.tom.util.Logger.println("(ServersCommunicationLayer.send) Sending msg to replica "+i);
			try {
				if (i == me) {
					inQueue.put(sm);
				} else {
					connections[i].send(data);
				}
			} catch (InterruptedException ex) {
				ex.printStackTrace();
			}
		}
		// br.ufsc.das.tom.util.Logger.println("(ServersCommunicationLayer.send) Finished sending messages to replicas");
	}

	public void shutdown() {
		doWork = false;

		for (int i = 0; i < connections.length; i++) {
			if (connections[i] != null) {
				connections[i].shutdown();
			}
		}
	}

	@Override
	public void run() {
		// connect to all lower ids than me, the rest will contact us
		for (int i = 0; i < me; i++) {
			PTPMessageVerifier verifier = null;
			// if (i != me) {
			if (verifierfactory != null) {
				verifier = verifierfactory.generateMessageVerifier();
			}
			connections[i] = new ServerConnection(conf, null, i, inQueue,
					msgHandlers, verifier, globalverifier, this);
			// }
		}
		while (doWork) {
			try {
				SocketChannel newSocketChannel = serverSocket.accept();
				newSocketChannel.configureBlocking(true);
				ServersCommunicationLayer.setSocketOptions(newSocketChannel.socket());
				ByteBuffer buf = ByteBuffer.allocate(4);
				newSocketChannel.read(buf);
				buf.flip();
				int remoteId = buf.getInt();
				if (remoteId >= 0 && remoteId < connections.length) {
					if (connections[remoteId] == null) {
						// first time that this connection is being established
						PTPMessageVerifier verifier = null;
						if (verifierfactory != null) {
							verifier = verifierfactory.generateMessageVerifier();
						}
						connections[remoteId] = new ServerConnection(conf,
								newSocketChannel, remoteId, inQueue, msgHandlers,
								verifier, globalverifier,this);
						connected(remoteId);
					} else {
						// reconnection
						connections[remoteId].gotConnection(newSocketChannel);
					}
				} else {
					newSocketChannel.close();
				}
			} catch (SocketTimeoutException ex) {
				// timeout on the accept... do nothing
			} catch (IOException ex) {
				Logger.getLogger(ServersCommunicationLayer.class.getName()).log(Level.SEVERE, null, ex);
			}
		}

		try {
			serverSocket.close();
		} catch (IOException ex) {
			Logger.getLogger(ServersCommunicationLayer.class.getName()).log(
					Level.SEVERE, null, ex);
		}

		Logger.getLogger(ServersCommunicationLayer.class.getName()).log(
				Level.INFO, "Server communication layer stoped.");
	}

	public static void setSocketOptions(Socket socket) {
		try {
			socket.setTcpNoDelay(true);
		} catch (SocketException ex) {
			Logger.getLogger(ServersCommunicationLayer.class.getName()).log(
					Level.SEVERE, null, ex);
		}
	}

	@Override
	public String toString() {
		String str = "inQueue=" + inQueue.toString();

		for (int i = 0; i < connections.length; i++) {
			if (connections[i] != null) {
				str += ", connections[" + i + "]: outQueue="
						+ connections[i].outQueue;
			}
		}

		return str;
	}
	
	public String getQueueLengths() {
		StringBuilder str = new StringBuilder();
		str.append(inQueue.size()).append(' ');
		for (int i = 0; i < connections.length; i++) {
			if (connections[i] != null) {
				str.append(connections[i].outQueue.size()).append(' ');
			} else {
				str.append("N/A ");
			}
		}
		return str.toString();
	}
	
	public String getQueueNames() {
		StringBuilder sb = new StringBuilder("InQueue ");
		for(int i = 0; i<connections.length;i++){
			sb.append(i).append(' ');
		}
		return sb.toString();
	}
	
	@Override
	public void connected(Integer id){
		//If the thread waiting for the comlayer to start was not released yet 
		//check if it could be released
		if(!started){
			synchronized(connectedcons){
				connectedcons.add(id);
				connectedcons.notifyAll();
			}
		}
	}
}
