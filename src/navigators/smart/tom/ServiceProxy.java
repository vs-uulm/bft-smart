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
package navigators.smart.tom;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.communication.client.CommunicationSystemClientSideFactory;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.Statistics;
import navigators.smart.tom.util.TOMConfiguration;

/**
 * This class implements a TOMReplyReceiver, and a proxy to be used on the client side of the application.
 * It sends a request to the replicas, receives the reply, and delivers it to the
 * application
 *
 */
public class ServiceProxy extends TOMSender {
	
	public static final Logger log = Logger.getLogger(ServiceProxy.class.getCanonicalName());

	private int n; // Number of total replicas in the system
	private int f; // Number of maximum faulty replicas assumed to occur
	private final Object sync = new Object();
	private int reqId = -1; // request id
	private TOMMessage replies[] = null; // Replies from replicas are stored here
	private byte[] response = null; // Pointer to the reply that is actually delivered to the application
	boolean decided = false;
	long timeout = 0; //timeout to wait for the client request

	/**
	 * Constructor
	 * 
	 * @param id Process id for this client
	 */
	public ServiceProxy(int id) {
		TOMConfiguration conf = new TOMConfiguration(id, "./config");
		init(conf);
	}
	
	/**
	 * Constructor
	 * 
	 * @param id Process id for this client
	 * @param timeout The timeout to wait for the request to finish before printing some logging info.
	 */
	public ServiceProxy(int id,long timeout) {
		TOMConfiguration conf = new TOMConfiguration(id, "./config");
		this.timeout = timeout;
		init(conf);
	}

	/**
	 * Constructor
	 * 
	 * @param id Process id for this client
	 */
	public ServiceProxy(int id, String configdir) {
		TOMConfiguration conf = new TOMConfiguration(id, configdir);
		init(conf);
	}

	// This method initializes the object
	private void init(TOMConfiguration conf) {
		init(CommunicationSystemClientSideFactory.getCommunicationSystemClientSide(conf), conf);
		n = conf.getN();
		f = conf.getF();
		replies = new TOMMessage[n];
		Statistics.init(conf);
	}

	/**
	 * This method sends a request to the replicas, and returns the related reply. This method is
	 * thread-safe.
	 *
	 * @param request Request to be sent
	 * @return The reply from the replicas related to request
	 */
	public byte[] invoke(byte[] request) {
		return invoke(request, false,false);
	}
	
	public byte[] invoke(byte[] request, boolean readonly) {
		return invoke(request, readonly, false);
	}

	/**
	 * This method sends a request to the replicas, and returns the related reply. This method is
	 * thread-safe.
	 *
	 * @param request Request to be sent
	 * @param readOnly it is a read only request (will not be ordered)
	 * @return The reply from the replicas related to request
	 */
	public byte[] invoke(byte[] request, boolean readOnly, boolean random) {

		// Ahead lies a critical section.
		// This ensures the thread-safety by means of a semaphore
		synchronized (sync) {
			try {
				// Discard previous replies
				Arrays.fill(replies, null);
				response = null;
				// Send the request to the replicas, and get its ID
				if (random){
					doRandomTOUnicast(request, readOnly);
				} else {
					doTOMulticast(request,readOnly);	
				}
				reqId = getLastSequenceNumber();
				while (!decided){
					sync.wait(timeout);
					if(!decided){
						StringBuilder s = new StringBuilder("Timeout while waiting for replies, got replies from: \n");
						s.append(Arrays.toString(replies));
						log.warning(s.toString());
					}
				}
				decided = false; //reset
			} catch (InterruptedException ex) {
				Logger.getLogger(ServiceProxy.class.getName()).log(Level.SEVERE, null, ex);
			}

		}

		return response; // return the response
	}
	
	/**
	 * This method sends a request to the replicas, and returns the related reply. This method is
	 * thread-safe.
	 *
	 * @param request Request to be sent
	 * @param readOnly it is a read only request (will not be ordered)
	 * @return The reply from the replicas related to request
	 */
	public byte[] invoke(TOMMessage request) {

		// Ahead lies a critical section.
		// This ensures the thread-safety by means of a semaphore
		synchronized (sync) {
			try {
				// Discard previous replies
				Arrays.fill(replies, null);
				response = null;
				// Send the request to the replicas, and get its ID
				doTOMulticast( request);
				reqId = request.getSequence();
				while (!decided){
					sync.wait(timeout);
					if(!decided){
						StringBuilder s = new StringBuilder("Timeout while waiting for replies, got replies from: \n");
						s.append(Arrays.toString(replies));
						log.warning(s.toString());
					}
				}
				decided = false; //reset decided
			} catch (InterruptedException ex) {
				Logger.getLogger(ServiceProxy.class.getName()).log(Level.SEVERE, null, ex);
			}

		}

		return response; // return the response
	}

	/**
	 * This is the method invoked by the client side comunication system.
	 *
	 * @param reply The reply delivered by the client side comunication system
	 */
	public void replyReceived(TOMMessage reply) {
		synchronized (sync) {
			int sender = reply.getSender();
			if (sender >= n) { //ignore messages that don't come from replicas
				return;
			}

			// Ahead lies a critical section.
			// This ensures the thread-safety by means of a semaphore
			if (reply.getSequence() == reqId) { // Is this a reply for the last request sent?
				replies[sender] = reply;
				// Compare the reply just received, to the others
				for (int i = 0; i < replies.length; i++) {
					if (replies[i] != null) {
						int sameContent = 1;
						byte[] content = replies[i].getContent();

						for (int j = i + 1; j < replies.length; j++) {
							if (replies[j] != null && Arrays.equals(replies[j].getContent(), content)) {
								sameContent++;

								// if there are more than f equal replies, this cycle can end
								if (sameContent >= f + 1) {
									break;
								}
							}
						}

						// Is there already more than f equal replies?
						if (sameContent >= f + 1) {
							response = content;
							reqId = -1;
							decided = true;
							sync.notify(); // unblocks the thread that is executing the "invoke" method,
							// so it can deliver the reply to the application
							break;
						}
					}
				}
			}
		}

		// Critical section ends here. The semaphore can be released
	}
}
