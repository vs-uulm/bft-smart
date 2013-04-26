/**
 * Copyright (c) 2007-2009 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the
 *
 * @author tags
 *
 * This file is part of SMaRt.
 *
 * SMaRt is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * SMaRt is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with SMaRt. If not, see <http://www.gnu.org/licenses/>.
 */
package navigators.smart.clientsmanagement;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.TOMConfiguration;
import navigators.smart.tom.util.TOMUtil;

/**
 * Holds a list of all currently active Clients and their pending Requests. 
 * The contents of this object differ across replicas, as clients may send
 * their requests to one or several replicas. This behaviour is protocolspecific.
 *
 * @author alysson
 * @author Christian Spann <christian.spann at uni-ulm.de>
 */
public class ClientsManager {

	private static final Logger log = Logger.getLogger(ClientsManager.class.getCanonicalName());
	private final TOMConfiguration conf;
	private final SortedMap<Integer, ClientData> clientsData = new TreeMap<Integer, ClientData>();
	private final ReentrantLock clientsLock = new ReentrantLock();
	private final List<ClientRequestListener> reqlisteners = new LinkedList<ClientRequestListener>();
	private final TOMUtil tomutil;
	public final AtomicInteger pendingreqs = new AtomicInteger();
	private volatile int nextClient = 0;

	/**
	 * Creates a new ClientsManager object with the given configuration.
	 *
	 * @param conf The configuration object to load the properties from.
	 */
	public ClientsManager(TOMConfiguration conf) {
		this.conf = conf;
		TOMUtil util = null;
		try {
			util = new TOMUtil();
		} catch (InvalidKeyException e) {
			log.severe(e.getLocalizedMessage());
		} catch (NoSuchAlgorithmException e) {
			log.severe(e.getLocalizedMessage());
		} catch (SignatureException e) {
			log.severe(e.getLocalizedMessage());
		}
		tomutil = util;
	}

	/**
	 * Registers a @see ClientRequestListener at this ClientsManager
	 * @param listener The listener to be registered
	 */
	public void addClientRequestListener(final ClientRequestListener listener) {
		reqlisteners.add(listener);
	}

	/**
	 * We are assuming that no more than one thread will access the same 
	 * clientData during creation.
	 * 
	 * @param clientId
	 * @return the ClientData stored on the manager
	 */
	private ClientData getClientData(Integer clientId) {
		try {
			clientsLock.lock();
			ClientData clientData = clientsData.get(clientId);

			if (clientData == null) {
				if (log.isLoggable(Level.FINEST)) {
					log.finest("Creating new client data for client id=" + clientId);
				}
				clientData = new ClientData(clientId, conf.getMaxPending());
				clientsData.put(clientId, clientData);
			}
			return clientData;
		} finally{
			clientsLock.unlock();
		}
	}

	/**
	 * Get pending requests in a fair way (one request from each client queue 
	 * until the max number of requests is gotten). The last client that was
	 * handled is stored to be really fair and have no side effekts when the 
	 * batch size is below the number of clients.
	 *
	 * @return the set of all pending requests of this system
	 */
	public PendingRequests getPendingRequests() {
		PendingRequests allReq = new PendingRequests();
		try {
		clientsLock.lock();
		
		int noMoreMessages = 0;
		List<ClientData> clients = new ArrayList<ClientData>(clientsData.values());
		do {
			for (; nextClient < clients.size() 
					&& clientsData.size() != noMoreMessages //break if all clients are empty
					&& allReq.size() != conf.getMaxBatchSize() // break if we reach max batch size
					; nextClient++) {
				ClientData clientData = clients.get(nextClient);
				TOMMessage request = null;
				try {
					clientData.clientLock.lock();
					request = clientData.proposeReq();
				} finally {
					clientData.clientLock.unlock();
				}
				if (request != null) {
					if (log.isLoggable(Level.FINEST)) {
						log.log(Level.FINEST, "Adding message from {0}", clientData.getClientId());
					}
					// this client have pending message
					allReq.addLast(request);
				} else {
					// this client do not have more pending requests
					noMoreMessages++;
				}
			}
			if (nextClient >= clients.size() || !conf.isFairClientHandling()) { 
				log.log(Level.FINE,"Resetting nextClient");
				nextClient = 0; //reset nextClient if we handled all or want to be unfair
			}
			// I inserted a message on the batch, now I must verify if the max
			// batch size is reached or no more messages are present
		} while (allReq.size() < conf.getMaxBatchSize() 
				&& clientsData.size() > noMoreMessages);
		} finally {
			clientsLock.unlock();
		}
		if (log.isLoggable(Level.FINER)) {
			log.log(Level.FINER, "Returning {0} messages, maxBatchsize: {1}", 
					new Object[]{allReq.size(), conf.getMaxBatchSize()});
			printPending();
		}
		return allReq;
	}

	private void printPending() {
		if(log.isLoggable(Level.FINEST)){
			StringBuilder s = new StringBuilder('\n');
			for (ClientData c : clientsData.values()) {
				s.append("Client: ").append(c.getClientId()).append(" reqs: ")
						.append(c.getPendingRequests()).append(" proposed: ")
						.append(c.getProposedRequests()).append('\n');
			}
			log.log(Level.FINEST,s.toString());
		}
	}

	/**
	 * We've implemented some protection for individual client data, but the 
	 * clients table can change during the operation.
	 *
	 * @return true if there are some pending requests and false otherwise
	 */
	public boolean hasPendingRequests() {
		try {
			clientsLock.lock();
			/**
			 * ***** BEGIN CLIENTS CRITICAL SECTION *****
			 */
			Iterator<Entry<Integer, ClientData>> it = clientsData.entrySet().iterator();

			while (it.hasNext()) {
				if (it.next().getValue().hasPendingRequests()) {
					return true;
				}
			}
			return false;
			/**
			 * ***** END CLIENTS CRITICAL SECTION *****
			 */
		} finally {
			clientsLock.unlock();
		}
	}

	/**
	 * Verifies if some reqId is pending.
	 *
	 * @param reqId the request identifier
	 * @return true if the request is pending
	 */
	public boolean isPending(Integer reqId) {
		return getPending(reqId) != null;
	}

	/**
	 * Get some reqId that is pending.
	 *
	 * @param reqId the request identifier
	 * @return the pending request, or null
	 */
	public TOMMessage getPending(Integer reqId) {
		Integer clientId = TOMMessage.getSenderFromId(reqId);
		TOMMessage pendingMessage = null;
		if (clientId.intValue() >= conf.getN()) {
			ClientData clientData = getClientData(clientId);
			try {
				clientData.clientLock.lock();
				pendingMessage = clientData.getRequestById(reqId);
			} finally {
				clientData.clientLock.unlock();
			}
		}
		return pendingMessage;
	}

	/**
	 * Notifies the ClientsManager that a new request from a client arrived. 
	 * This method updates the ClientData of the client request.getSender().
	 *
	 * @param request the received request
	 * @param fromClient the message was received from client or not?
	 * @param recordRequest the message should be stored or not?
	 * (read-only requests are not stored for example)
	 *
	 * @return true if the request is ok and is added to the pending messages 
	 * for this client, false if there is some problem and the message was not
	 * accounted
	 */
	public boolean checkAndRecordRequest(TOMMessage request, boolean fromClient, 
			boolean recordRequest) {
		request.receptionTime = System.currentTimeMillis();
		ClientData clientData = getClientData(request.getSender());
		boolean valid = false;		// Is this message correct wrt the TOMLayer

		try {
			/** ***** BEGIN CLIENTDATA CRITICAL SECTION ***** */
			clientData.clientLock.lock();
			if (fromClient && conf.getUseControlFlow() != 0) {
				checkAndRecordUsingControlFlow(request, clientData);
			} else {
				// Check if msg is new and signature is valid
				if (checkIfMsgIsNew(request,clientData) 
						&& checkSignature(request, clientData)) {
						// Store request in cdata
						if (recordRequest) {
							clientData.addRequest(request);
							pendingreqs.incrementAndGet();
						}
						clientData.recordRequestInfo(request);
						//inform listeners
						for (ClientRequestListener listener : reqlisteners) {
							listener.requestReceived(request);
						}
						valid = true;
				} else {
					if (	// Replicas can propose requests again, otherwise the TO
							// Mechanism breaks
							!fromClient ||
							//I already have/had this message but this is ok
							clientData.getLastMessageReceived() == request.getSequence()) {
						valid = true;
					} else {
						//it is an invalid message if it's being sent by a client 
						//(sequence number < last received )
						if (fromClient && log.isLoggable(Level.WARNING)) {
							log.warning("Ignoring message " + request 
									+ " from client " + clientData.getClientId() 
									+ "(last received = "
									+ clientData.getLastMessageReceived() 
									+ "), msg sent by client? " + fromClient);
						}
					}
				}
			}
		} finally {
			/** ***** END CLIENTDATA CRITICAL SECTION ***** */
			clientData.clientLock.unlock();
		}
		return valid;
	}

	/**
	 * Notifies the ClientsManager that the request was executed. It cleans all 
	 * state for this request (e.g., removes it from the pending requests
	 * queue and stop any timer for it).
	 *
	 * @param request the request executed by the application
	 * @param reply the resulting reply of the request execution
	 */
	public void requestOrdered(TOMMessage request) {
		ClientData clientData = getClientData(request.getSender());

		for (ClientRequestListener listener : reqlisteners) {
			listener.requestOrdered(request);
		}
		try {
			clientData.clientLock.lock();
			if (clientData.removeRequest(request) == false) {
				if (log.isLoggable(Level.FINE)) {
					log.fine("(ClientsManager.requestOrdered) Request " + request
							+ " does not exist in pending requests");
				}
			} else {
				if (log.isLoggable(Level.FINEST)) {
					log.finest("(ClientsManager.requestOrdered) Request " + request
							+ " finished");
				}
				pendingreqs.decrementAndGet();
			}
		} finally {
			clientData.clientLock.unlock();
		}
	}

	/**
	 * Resets all client related information. All incoming client requests will 
	 * be treated as new. This is used to reset replicas between tests.
	 */
	public void resetClients() {
		try {
			clientsLock.lock();
			pendingreqs.set(0);
			clientsData.clear();
		} finally {
			clientsLock.unlock();
		}
	}

	/**
	 * Checks if this request is a valid request using the preset maximum of
	 * pending requests defined via the control flow parameter. 
	 * 
	 * @synchronize via the cdata.lock
	 * 
	 * @param request The request to check
	 * @param cdata The data for this client
	 */
	private void checkAndRecordUsingControlFlow(TOMMessage request, ClientData cdata) {
		//pjsousa: added simple flow control mechanism to avoid out of memory exception
		// TODO no sequence enforcement is made here, rework this when needed
		//clients should not have more than 1000 outstanding messages, otherwise they will be dropped 
		if (cdata.getAllPendingRequests() > conf.getUseControlFlow()) {
			cdata.recordRequestInfo(request);
		}
	}
	
	/**
	 * Checks if the signature of this request is valid. 
	 *
	 * @synchronize via the cdata.lock
	 * 
	 * @param request The request to check
	 * @param cdata The data for this client
	 * @return true if signatures are disabled or the request is valid
	 */
	private boolean checkSignature(TOMMessage request, ClientData cdata){
		if(!request.signed || tomutil.verifySignature(cdata.getPublicKey(), 
				request.getBytes(), request.serializedMessageSignature)){
			return true;
		} else {
			log.log(Level.WARNING,"Received incorrectly signed message: {0}", request);
			return false;
		}
	}

	/**
	 * Checks if the message is new or newer than the id of the last one accounted
	 *
	 * @synchronize via the cdata.lock
	 * 
	 * @param request The request to check
	 * @param cdata The data for this client
	 * @return true if this is the first message from this client or if it is
	 * younger than the last one received
	 */
	private boolean checkIfMsgIsNew(TOMMessage request, ClientData cdata) {
		// -1 indicates that this message is new
		boolean isNew = (cdata.getLastMessageReceived() == -1)
				//this is the next message in the sequence of the client (gaps allowed)
				|| (request.getSequence() > cdata.getLastMessageReceived());
		//this is an out of order message that was forwarded/decided - we don't care about older messages any more
//	            || ((request.getSequence() > clientData.getLastMessageReceived()) && !fromClient)
		if(isNew){
			log.log(Level.FINER, "{0} is NEW",request);
		} else {
			log.log(Level.FINER, "{0} is NOT NEW, current seq is {1}",
					new Object[]{request,cdata.getLastMessageReceived()});
		}
		return isNew;
	}
}
