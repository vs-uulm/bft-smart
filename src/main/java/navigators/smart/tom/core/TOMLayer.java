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
package navigators.smart.tom.core;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import navigators.smart.clientsmanagement.ClientsManager;
import navigators.smart.clientsmanagement.PendingRequests;
import navigators.smart.communication.ServerCommunicationSystem;
import navigators.smart.communication.client.RequestReceiver;
import navigators.smart.consensus.Consensus;
import navigators.smart.consensus.ConsensusService;
import navigators.smart.statemanagment.SMMessage;
import navigators.smart.statemanagment.StateManager;
import navigators.smart.statemanagment.TransferableState;
import navigators.smart.tom.TOMReceiver;
import navigators.smart.tom.TOMRequestReceiver;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.*;

/**
 * This class implements a thread that uses the PaW algorithm to provide the 
 * application a layer of total ordered messages.
 * 
 * @author  Christian Spann 
 */
@SuppressWarnings("LoggerStringConcat")
public class TOMLayer implements RequestReceiver {

	private static final Logger log = Logger.getLogger(TOMLayer.class.getCanonicalName());
	//other components used by the TOMLayer (they are never changed)
	private ServerCommunicationSystem communication; // Communication system between replicas
	private final DeliveryThread dt; // Thread which delivers total ordered messages to the appication
	private TOMConfiguration conf; // TOM configuration
	/** Store requests received but still not ordered */
	public final ClientsManager clientsManager;
	/** Interface to the consensus */
	private ConsensusService consensusService;
	private TOMRequestReceiver receiver;
	private BatchBuilder bb = new BatchBuilder();
	private MessageDigest md;
	/** Sync used to synchronize between forwarded and normal messages */
	private final Object requestsync = new Object();
	/** Marker msg indicating a reset request */
	private byte[] RESET = {2, 6, 1, 2, 8, 0};
	/** The statemanager to handle state transfers */
	private StateManager stateManager = null;


	/**
	 * Creates a new instance of TOMulticastLayer
	 *
	 * @param receiver Object that receives requests from clients
	 * @param cs Communication system between replicas
	 * @param conf TOM configuration
	 */
	public TOMLayer(TOMReceiver receiver,
			ServerCommunicationSystem cs,
			TOMConfiguration conf) {
		Statistics.init(conf);
		this.receiver = receiver;
		this.communication = cs;
		this.conf = conf;
		MessageDigest state_md = null;
		try {
			// TODO: Shouldn't we use SHA
			this.md = MessageDigest.getInstance("MD5"); 
			state_md = MessageDigest.getInstance("MD5");
		} catch (Exception e) {
			e.printStackTrace(System.out);
		}

		this.clientsManager = new ClientsManager(conf); // Create clients manager
		stateManager = new StateManager(conf.getCheckpoint_period(), conf.getF(),
				conf.getN(), conf.getProcessId().intValue(), state_md);

		this.dt = new DeliveryThread(this, receiver, conf); // Create delivery thread
		this.dt.start();
		//DONT DO ANYTHING BEYOND THIS POINT! This leaked into dt from now on
	}

	/**
	 * Retrieve TOM configuration
	 *
	 * @return TOM configuration
	 */
	public TOMConfiguration getConf() {
		return this.conf;
	}

	/**
	 * Computes an hash for a TOM message
	 *
	 * @param data The data to hash
	 * @return Hash for teh specified TOM message
	 */
	public final byte[] computeHash(byte[] data) {
		return md.digest(data);
	}

	/**
	 * Retrieve Communication system between replicas
	 *
	 * @return Communication system between replicas
	 */
	public ServerCommunicationSystem getCommunication() {
		return this.communication;
	}

	/**
	 * This method is invoked by the comunication system to deliver a request. 
	 * It assumes that the communication system delivers the message in FIFO
	 * order.
	 *
	 * @param msg The request being received
	 */
	public void requestReceived(TOMMessage msg) {
		//synchronize forwareded and client received msg access
		synchronized (requestsync) {
			// check if reset is triggered
			// TODO Add property to disable this for "live" usage if ever intended
			if (Arrays.equals(msg.getContent(), RESET)) {
				clientsManager.resetClients();
			}
			// check if this request is valid
			if (clientsManager.checkAndRecordRequest(msg, true, !msg.isReadOnlyRequest())) {
				if (msg.isReadOnlyRequest()) {
					receiver.receiveUnorderedMessage(msg);
				} else {
					consensusService.notifyNewRequest(msg);
				}
			} else {
				if (log.isLoggable(Level.FINER)) {
					log.finer(" the received TOMMessage " + msg + " was discarded.");
				}
			}
		}
	}

	/**
	 * Creates a value to be proposed to the acceptors. Invoked if this replica is the leader
	 *
	 * @return A value to be proposed to the acceptors or null if no requests
	 * are pending.
	 */
	@SuppressWarnings("null")
	public byte[] createPropose() {
		// Retrieve a set of pending requests from the clients manager
		PendingRequests pendingReqs = clientsManager.getPendingRequests();
		
		if(pendingReqs.size() == 0){
			return null;
		}

		int numberOfMessages = pendingReqs.size(); // number of messages retrieved
		int numberOfNonces = conf.getNumberOfNonces(); // ammount of nonces to be generated

		if (log.isLoggable(Level.FINER) && pendingReqs.size() > 0) {
			log.finer(" creating a PROPOSE with " + numberOfMessages +
					" msgs from " + pendingReqs.getFirst() + " to " 
					+ pendingReqs.getLast());
		}

		int totalMessageSize = 0; //total size of the messages being batched
		byte[][] messages = new byte[numberOfMessages][]; //bytes of the message (or its hash)
		byte[][] signatures = null;
		if (conf.getUseSignatures() == 1) {
			signatures = new byte[numberOfMessages][]; //bytes of the message (or its hash)
		}

		// Fill the array of bytes for the messages/signatures being batched
		int i = 0;
		for (Iterator<TOMMessage> li = pendingReqs.iterator(); li.hasNext(); i++) {
			TOMMessage msg = li.next();
			if (log.isLoggable(Level.FINEST)) {
				log.finest(" adding req " + msg + " to PROPOSE");
			}
			messages[i] = msg.getBytes();
			if (conf.getUseSignatures() == 1) {
				signatures[i] = msg.serializedMessageSignature;
			}

			totalMessageSize += messages[i].length;
		}

		// return the batch
		return bb.createBatch(System.currentTimeMillis(), numberOfNonces, 
				numberOfMessages, totalMessageSize, messages, signatures);
	}

	/**
	 * Called by the current consensus's execution, to notify the TOM layer that
	 * a value was decided
	 *
	 * @param cons The decided consensus
	 */
	public void decided(Consensus<TOMMessage[]> cons) {
		this.dt.delivery(cons); // Delivers the consensus to the delivery thread
	}

	/**
	 * Verify if the value being proposed for a round is valid. It verifies the 
	 * client signature of all batch requests.
	 *
	 * @param proposedValue the value being proposed
	 * @return	null if the value is not correct, a List of TOMMessages if it is.
	 */
	public TOMMessage[] checkProposedValue(byte[] proposedValue) {
		if (log.isLoggable(Level.FINER)) {
			log.finer("Checking proposed values");
		}
		try {
			//deserialize the message
			//TODO: verify Timestamps and Nonces
			TOMMessage[] requests = new BatchReader(proposedValue,
					conf.getUseSignatures() == 1,
					conf.getSignatureSize()).deserialiseRequests();

			for (int i = 0; i < requests.length; i++) {
				
				//check all proposed requests with the client manager
				if (!clientsManager.checkAndRecordRequest(requests[i], false, true)) {
					log.log(Level.WARNING,"Something is wrong with {0}, "
							+ "returning null",requests[i]);
					return null;
				}
			}
			return requests;
		} catch (Exception e) {
			log.log(Level.SEVERE, "Error while checking proposed value", e);
			return null;
		} 
	}
	
	public void saveBatch(byte[] batch, Long lastEid, Integer decisionRound, int leader) {
		stateManager.saveBatch(batch, lastEid, decisionRound, leader);
	}

	/**
	 * Requests a statetransfer to this replica if state transfer is enabled and
	 * this replica is <b>not</b> currently waiting for state.
	 */
	@SuppressWarnings("boxing")
	public void requestStateTransfer(Integer me, Integer[] otherAcceptors,
			Integer sender, Long eid) {
		if (conf.isStateTransferEnabled()) {
			if (!stateManager.isWaitingForState()) {
				log.log(Level.FINER, "Checking state transfer request due to for"
						+ " exec {1} from {0}", new Object[]{sender, eid});
				if (stateManager.addEIDAndCheckStateTransfer(sender, eid)) {
					log.log(Level.FINE, "Sending staterequest for {1} to {0}",
							new Object[]{sender, eid});
					Statistics.stats.stateTransferRequested();

					// Create the statemessage and send it.
					SMMessage smsg = new SMMessage(me, eid - 1,
							TOMUtil.SM_REQUEST, stateManager.getReplica(), null);
					communication.send(otherAcceptors, smsg);
				} else {
					log.log(Level.FINER, "Not yet requesting state for {1} from {0}", new Object[]{sender, eid});
				}
			} else {
				log.fine("I'm already waiting for a state - not starting state transfer");
			}
		} else {
			if (log.isLoggable(Level.WARNING)) {
				log.warning(" The state transfer protocol is disabled: /n"
						+ "################################################################################## /n"
						+ "- Ahead-of-time message discarded/n"
						+ "- If many messages of the same consensus are discarded, the replica can halt!/n"
						+ "- Try to increase the 'system.paxos.highMarc' configuration parameter./n"
						+ "- Last consensus executed: " + consensusService.getLastExecuted() + "/n"
						+ "##################################################################################");
			}
		}
	}

	public void SMRequestDeliver(SMMessage msg) {
		if (conf.isStateTransferEnabled()) {
			Statistics.stats.stateTransferReqReceived();
			boolean sendState = msg.getReplica() == conf.getProcessId().intValue();
			if (log.isLoggable(Level.FINE)) {
				if (sendState) {
					log.fine("Received " + msg + " - sending full state");
				} else {
					log.fine("Received " + msg + " - sending hash");
				}
			}
			TransferableState state = stateManager.getTransferableState(msg.getEid(), sendState);
			if (state == null) {
				if (log.isLoggable(Level.FINE)) {
					log.log(Level.FINE, "State for id {0} not present on this node", msg.getEid());
				}
				state = new TransferableState();
			}

			Integer[] targets = {msg.getSender()};
			SMMessage smsg = new SMMessage(consensusService.getId(),
					msg.getEid(), TOMUtil.SM_REPLY, -1, state);
			communication.send(targets, smsg);

			if (log.isLoggable(Level.FINER)) {
				log.fine(" I sent the state for checkpoint " 
						+ state.lastCheckpointEid + " with batches until EID " + state.lastEid);
			}
		}
	}

	public void SMReplyDeliver(SMMessage msg) {
		if (conf.isStateTransferEnabled()) {
			if (log.isLoggable(Level.FINER)) {
				log.finer(" The state transfer protocol is enabled");
				log.finer(" I received a state reply for EID " + msg.getEid() 
						+ " from replica " + msg.getSender());
			}

			TransferableState state = stateManager.registerSMMessage(msg);
			if (state != null) {
				dt.updateState(state);
				stateManager.resetWaiting();
			}
		}
	}

	public boolean isRetrievingState() {
		return stateManager.isWaitingForState();
	}

	public void setConsensusService(ConsensusService manager) {
		this.consensusService = manager;
		dt.setConsensusservice(consensusService);
	}

//	public boolean hasPendingRequests() {
//		return clientsManager.hasPendingRequests();
//	}

	public byte[] getState() {
		return receiver.getState();
	}

	/**
	 * Waits until the currently running Statetransfer is finished. If no State-
	 * transfer is currently running this method returns immediatly
	 */
	public void checkAndWaitForState() {
		stateManager.checkAndWaitForSTF();
	}

	public StateManager getStateManager() {
		return stateManager;
	}	public void shutdown() {		dt.shutdown();			}
}
