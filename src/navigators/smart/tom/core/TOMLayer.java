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
 * This class implements a thread that uses the PaW algorithm to provide the application a layer of total ordered messages
 *
 * TODO There is a bug when we request a statetransfer for eid 0 -> statemanager.waiting is set to -1 then which means we are not waiting for a state
 */
@SuppressWarnings("LoggerStringConcat")
public class TOMLayer implements RequestReceiver {

	private static final Logger log = Logger.getLogger(TOMLayer.class.getCanonicalName());
	//other components used by the TOMLayer (they are never changed)
	private ServerCommunicationSystem communication; // Communication system between replicas
	private final DeliveryThread dt; // Thread which delivers total ordered messages to the appication
	private TOMConfiguration conf; // TOM configuration
	/**
	 * Store requests received but still not ordered
	 */
	public final ClientsManager clientsManager;
	/**
	 * Interface to the consensus
	 */
	private ConsensusService consensusService;
	private TOMRequestReceiver receiver;
	private BatchBuilder bb = new BatchBuilder();
	private MessageDigest md;
	/**
	 * Sync used to synchronize between forwarded and normal messages
	 */
	private final Object requestsync = new Object();
	/**
	 * Marker msg indicating a reset request
	 */
	private byte[] RESET = {2, 6, 1, 2, 8, 0};

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

		try {
			this.md = MessageDigest.getInstance("MD5"); // TODO: nao devia ser antes SHA?
		} catch (Exception e) {
			e.printStackTrace(System.out);
		}

		this.clientsManager = new ClientsManager(conf); // Create clients manager

		stateManager = new StateManager(conf.getCheckpoint_period(), conf.getF(), conf.getN(), conf.getProcessId().intValue(), md);

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
	 * This method is invoked by the comunication system to deliver a request. It assumes that the communication system delivers the message in FIFO
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
			if (clientsManager.requestReceived(msg, true, !msg.isReadOnlyRequest())) {
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
	 * @return A value to be proposed to the acceptors
	 */
	@SuppressWarnings("null")
	public byte[] createPropose() {
		// Retrieve a set of pending requests from the clients manager
		PendingRequests pendingRequests = clientsManager.getPendingRequests();

		int numberOfMessages = pendingRequests.size(); // number of messages retrieved
		int numberOfNonces = conf.getNumberOfNonces(); // ammount of nonces to be generated

		if (log.isLoggable(Level.FINER) && pendingRequests.size() > 0) {
			log.finer(" creating a PROPOSE with " + numberOfMessages + " msgs from " + pendingRequests.getFirst() + " to " + pendingRequests.getLast());
		}

		int totalMessageSize = 0; //total size of the messages being batched
		byte[][] messages = new byte[numberOfMessages][]; //bytes of the message (or its hash)
		byte[][] signatures = null;
		if (conf.getUseSignatures() == 1) {
			signatures = new byte[numberOfMessages][]; //bytes of the message (or its hash)
		}

		// Fill the array of bytes for the messages/signatures being batched
		int i = 0;
		for (Iterator<TOMMessage> li = pendingRequests.iterator(); li.hasNext(); i++) {
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
		return bb.createBatch(System.currentTimeMillis(), numberOfNonces, numberOfMessages, totalMessageSize, messages, signatures);
	}

	/**
	 * Called by the current consensus's execution, to notify the TOM layer that a value was decided
	 *
	 * @param cons The decided consensus
	 */
	public void decided(Consensus<TOMMessage[]> cons) {
		this.dt.delivery(cons); // Delivers the consensus to the delivery thread
	}

	/**
	 * Verify if the value being proposed for a round is valid. It verifies the client signature of all batch requests.
	 *
	 * TODO: verify timestamps and nonces
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
			TOMMessage[] requests = new BatchReader(proposedValue, conf.getUseSignatures() == 1, conf.getSignatureSize()).deserialiseRequests();

			//log.finer(" Got clientsManager lock");
			for (int i = 0; i < requests.length; i++) {
				//notifies the client manager that this request was received and get
				//the result of its validation
				if (!clientsManager.requestReceived(requests[i], false, true)) {
					if (log.isLoggable(Level.FINER)) {
						log.finer("Something is wrong with this batch, returning null");
					}
					return null;
				}
			}
			return requests;
		} catch (Exception e) {
			log.log(Level.SEVERE, "Error while checking proposed value", e);
			return null;
		} 
	}
	
	/**
	 * ISTO E CODIGO DO JOAO, PARA TRATAR DOS CHECKPOINTS
	 */
	private StateManager stateManager = null;

	public void saveBatch(byte[] batch, Long lastEid, Integer decisionRound, int leader) {
		stateManager.saveBatch(batch, lastEid, decisionRound, leader);
	}

	/**
	 * ISTO E CODIGO DO JOAO, PARA TRATAR DA TRANSFERENCIA DE ESTADO
	 */
	@SuppressWarnings("boxing")
	public void requestStateTransfer(Integer me, Integer[] otherAcceptors, Integer sender, Long eid) {

		/**
		 * *********************** TESTE ************************* System.out.println("[TOMLayer.requestState]"); System.out.println("Mensagem
		 * adiantada! (eid " + eid + " vindo de " + sender + ") "); /************************* TESTE ************************
		 */
		if (conf.isStateTransferEnabled()) {
			if (!stateManager.isWaitingForState()) {

				log.log(Level.FINER, "Checking state transfer request due to for exec {1} from {0}", new Object[]{sender, eid});

				if (stateManager.addEIDAndCheckStateTransfer(sender, eid)) {
//					try {
//						MBeanServer server = ManagementFactory.getPlatformMBeanServer();
//						HotSpotDiagnosticMXBean bean = 
//							ManagementFactory.newPlatformMXBeanProxy(server,
//							"com.sun.management:type=HotSpotDiagnostic", HotSpotDiagnosticMXBean.class);
//						bean.dumpHeap("/tmp/heapDumplive", true);
					   log.log(Level.FINE, "Sending staterequest for {1} to {0}", new Object[]{sender, eid});
					   Statistics.stats.stateTransferRequested();
					   SMMessage smsg = new SMMessage(me, eid - 1, TOMUtil.SM_REQUEST, stateManager.getReplica(), null);
					   communication.send(otherAcceptors, smsg);
//					} catch (IOException ex) {
//						Logger.getLogger(TOMLayer.class.getName()).log(Level.SEVERE, null, ex);
//					}
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
		/**
		 * *********************** TESTE ************************* log.finer("[/TOMLayer.requestState]"); /************************* TESTE
		 * ************************
		 */
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
			SMMessage smsg = new SMMessage(consensusService.getId(), msg.getEid(), TOMUtil.SM_REPLY, -1, state);
			communication.send(targets, smsg);

			if (log.isLoggable(Level.FINER)) {
				log.fine(" I sent the state for checkpoint " + state.lastCheckpointEid + " with batches until EID " + state.lastEid);
			}
		}
	}

	public void SMReplyDeliver(SMMessage msg) {

		if (conf.isStateTransferEnabled()) {

			if (log.isLoggable(Level.FINER)) {
				log.finer(" The state transfer protocol is enabled");
				log.finer(" I received a state reply for EID " + msg.getEid() + " from replica " + msg.getSender());
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

	public boolean hasPendingRequests() {
		return clientsManager.hasPendingRequests();
	}

	public byte[] getState() {
		return receiver.getState();
	}

	/**
	 * Waits until the currently running Statetransfer is finished. If no State- transfer is currently running this method returns immediatly
	 */
	public void checkAndWaitForState() {
		stateManager.checkAndWaitForSTF();
	}

	public StateManager getStateManager() {
		return stateManager;
	}
}
