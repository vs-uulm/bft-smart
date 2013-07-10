/**
 * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the
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
package navigators.smart.paxosatwar.requesthandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.SignedObject;
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import navigators.smart.communication.ServerCommunicationSystem;
import navigators.smart.paxosatwar.executionmanager.*;
import navigators.smart.paxosatwar.requesthandler.timer.RTInfo;
import navigators.smart.tom.core.TOMLayer;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.core.timer.messages.ForwardedMessage;
import navigators.smart.tom.core.timer.messages.RTCollect;
import navigators.smart.tom.core.timer.messages.RTLeaderChange;
import navigators.smart.tom.core.timer.messages.RTMessage;
import navigators.smart.tom.util.TOMConfiguration;
import navigators.smart.tom.util.TOMUtil;

/**
 * This class handles Requests
 *
 * @author Christian Spann 
 */
@SuppressWarnings("LoggerStringConcat")
public class RequestHandler extends Thread {

	private static final Logger log = Logger.getLogger(RequestHandler.class.getCanonicalName());
	private final ExecutionManager execManager; // Execution manager
	private final LeaderModule lm; // Leader module
	private final ProofVerifier verifier; // Acceptor role of the PaW algorithm
	private final TOMConfiguration conf;
	private Map<Integer, RTInfo> timeoutInfo = new HashMap<Integer, RTInfo>();
	private ReentrantLock lockTI = new ReentrantLock();

	/*
	 * The locks and conditions used to wait upon creating a propose
	 */
	private ReentrantLock leaderLock = new ReentrantLock();
	private Condition iAmLeader = leaderLock.newCondition();

	/*
	 * flag that indicates that the lader changed between the last propose and this propose. This flag is changed on updateLeader (to true) and
	 * decided (to false) and used in run.
	 */
	private boolean leaderChanged = false;
	private final TOMLayer tomlayer;
	private final ServerCommunicationSystem communication;

	public RequestHandler(ServerCommunicationSystem com, ExecutionManager execmng, LeaderModule lm, ProofVerifier a, TOMConfiguration conf, TOMLayer tom) {
		super("RequestHandler");
		setDaemon(true);
		this.execManager = execmng;
		this.lm = lm;
		this.verifier = a;
		this.tomlayer = tom;
		this.conf = conf;
		this.communication = com;

	}

//	public void imAmTheLeader() {
//		leaderLock.lock();
//		iAmLeader.signal();
//		leaderLock.unlock();
//	}

//	/**
//	 * Sets which consensus was the last to be executed
//	 *
//	 * @param last ID of the consensus which was last to be executed
//	 */
//	public void setLastExec(Long last) { // TODO:  Condiçao de corrida?
//		this.lastExecuted = last;
//		this.nextExecution = new Long(last.longValue() + 1);
//	}
//
//	/**
//	 * Gets the ID of the consensus which was established as the last executed
//	 *
//	 * @return ID of the consensus which was established as the last executed
//	 */
//	public Long getLastExec() {
//		return this.lastExecuted;
//	}
//
//	/**
//	 * Sets which consensus is being executed at the moment. If the value is set to -1 a new Proposal is triggered if this replica is the leader.
//	 *
//	 * @param inEx ID of the consensus being executed at the moment
//	 */
//	public void setInExec(Long inEx) {
//
//		leaderLock.lock();
//		if (inEx.equals(IDLE) && !tomlayer.isRetrievingState()) { //code of joao for state transfer
//			iAmLeader.signalAll();
//		}
//		leaderLock.unlock();
//	}
//
//
//	/**
//	 * Gets the ID of the consensus currently beign executed
//	 *
//	 * @return ID of the consensus currently beign executed (if no consensus ir executing, -1 is returned)
//	 */
//	public Long getInExec() {
//		return this.inExecution;
//	}
//
//	/**
//	 * Checks whether the given execution is currently executed
//	 *
//	 * @param exec The execution to check
//	 * @return true if it is currently executed, false if not
//	 */
//	public boolean isInExec(Long exec) {
//		return inExecution.equals(exec);
//	}

	/**
	 * This is the main code for this thread. It basically waits until this replica becomes the leader, and when so, proposes a value to the other
	 * acceptors
	 */
	@Override
	public void run() {
		/*
		 * Storage st = new Storage(BENCHMARK_PERIOD/2); long start=-1; int counter =0;
		 */
		if (log.isLoggable(Level.INFO)) {
			log.info("Running."); // TODO: isto n podia passar para fora do ciclo?
		}

		while (true) {
				byte[] value = null;
			// blocks until this replica learns to be the leader for the current round of the current consensus
			try {
				leaderLock.lock();
				
				if (log.isLoggable(Level.FINER)) {
					log.finer("Next leader for eid=" + (execManager.state.getNextExecID()) + ": " + lm.getLeader(execManager.state.getNextExecID()));
				}
				while (!canPropose() || (value = tomlayer.createPropose()) == null){
					iAmLeader.awaitUninterruptibly();	
				}

				if (log.isLoggable(Level.FINER)) {
					log.finer("I can propose.");
				}
	
				
				leaderChanged = false;
			} finally {
				leaderLock.unlock();
			}
				execManager.startNextExecution(value);
		}
	}
	
	/**
	 * Must be called within leaderlock! Returns if this replica is leader and can propose.
	 * @return True if eligible to propose, false if not.
	 */
	private boolean canPropose(){
		boolean leader, ready;
		//Check if i'm the leader
		leader = lm.checkLeader(execManager.state.getInExec(), Round.ROUND_ZERO, conf.getProcessId());
//		Integer nextLeader = lm.getLeader(execManager.getNextExecID());
//		leader = nextLeader != null && nextLeader.equals(conf.getProcessId());
		//no consensus is in execution 
		ready = /*tomlayer.clientsManager.hasPendingRequests() && */
				execManager.isIdle()
				// and we are not retrieving a state
				&& !tomlayer.isRetrievingState();

		if (log.isLoggable(Level.FINER)) {
			log.log(Level.FINER,"Requesthandler checking: leader: {0}, "
					+ "ready:  {1}(isIdle) && {2}(no statetx), changed:{3}",
					new Object[]{leader, execManager.isIdle(), 
						!tomlayer.isRetrievingState(), leaderChanged});
		} 
		return leader && ready ;
	}
	
	/**
	 * Invoked when a timeout for a TOM message is triggered.
	 *
	 * @param requestList
	 * @return True if the request is still pending and the timeout was not triggered before, false otherwise
	 */
	public boolean requestTimeout(List<TOMMessage> requestList) {
		List<byte[][]> serializedRequestList = new LinkedList<byte[][]>();

		//verify if the request is still pending
		for (Iterator<TOMMessage> i = requestList.listIterator(); i.hasNext();) {
			TOMMessage request = i.next();
			if (tomlayer.clientsManager.isPending(request.getId())) {
				RTInfo rti = getTimeoutInfo(request.getId());
				if (!rti.isTimeout(conf.getProcessId().intValue())) {
					serializedRequestList.add(
							new byte[][]{request.getBytes(), request.serializedMessageSignature});
					timeout(conf.getProcessId().intValue(), request, rti);
					if (log.isLoggable(Level.FINE)) {
						log.fine("Must send timeout for reqId=" + request.getId());
					}
				}
			}
		}

		if (!requestList.isEmpty()) {
			sendTimeoutMessage(serializedRequestList);
			return true;
		} else {
			return false;
		}
	}

	public void forwardRequestToLeader(TOMMessage request) {
		@SuppressWarnings("boxing")
		Integer leaderId = lm.getLeader(execManager.state.getLastExec() + 1, 0);
		if (log.isLoggable(Level.FINE)) {
			log.fine("Forwarding " + request + " to " + leaderId);
		}
		communication.send(new Integer[]{leaderId}, new ForwardedMessage(conf.getProcessId(), request));
	}

	/**
	 * Sends a RT-TIMEOUT message to other processes.
	 *
	 * @param request the message that caused the timeout
	 */
	@SuppressWarnings("boxing")
	public void sendTimeoutMessage(List<byte[][]> serializedRequestList) {
		communication.send(execManager.getOtherAcceptors(),
				new RTMessage(TOMUtil.RT_TIMEOUT, -1, conf.getProcessId(), serializedRequestList));
	}

	/**
	 * Sends a RT-COLLECT message to other processes 
	 * TODO: Se se o novo leader for este processo, nao e enviada nenhuma mensagem. Isto estara bem
	 * feito?
	 *
	 * @param reqId ID of the message which triggered the timeout
	 * @param collect Proof for the timeout
	 */
	public void sendCollectMessage(Integer reqId, RTCollect collect) {
		RTMessage rtm = new RTMessage(TOMUtil.RT_COLLECT, reqId,
				conf.getProcessId(), verifier.sign(collect));

		if (collect.getNewLeader().equals(conf.getProcessId())) {
			RTInfo rti = getTimeoutInfo(reqId);
			collect((SignedObject) rtm.getContent(), conf.getProcessId(), rti);
		} else {
			Integer[] target = {collect.getNewLeader()};
			this.communication.send(target, rtm);
		}

	}

	/**
	 * Sends a RT-LEADER message to other processes. It also updates the leader
	 *
	 * @param reqId ID of the message which triggered the timeout
	 * @param timeout Timeout number
	 * @param rtLC Proofs for the leader change
	 */
	public void sendNewLeaderMessage(Integer reqId, RTLeaderChange rtLC) {
		RTMessage rtm = new RTMessage(TOMUtil.RT_LEADER, reqId, conf.getProcessId(), rtLC);
		//br.ufsc.das.util.Logger.println("Atualizando leader para "+rtLC.newLeader+" a partir de "+rtLC.start);
		updateLeader(reqId, rtLC.start, rtLC.newLeader);

		communication.send(execManager.getOtherAcceptors(), rtm);
	}

	/**
	 * Updates the leader of the PaW algorithm. This is triggered upon a timeout for a pending message.
	 *
	 * @param reqId ID of the message which triggered the timeout
	 * @param start Consensus where the new leader belongs
	 * @param newLeader Replica ID of the new leader
	 * @param timeout Timeout number
	 */
	private void updateLeader(Integer reqId, Long start, Integer newLeader) {

		leaderLock.lock(); // Signal the TOMlayer thread, if this replica is the leader
		lm.setLeaderInfo(start, Round.ROUND_ZERO, newLeader); // update the leader
		leaderChanged = true;
		if (lm.getLeader(execManager.state.getNextExecID()).equals(conf.getProcessId())) {
			iAmLeader.signal();
		}
		leaderLock.unlock();

		removeTimeoutInfo(reqId); // remove timeout infos
		//requestsTimer.startTimer(clientsManager.getPending(reqId)); // restarts the timer
		execManager.restart(); // restarts the execution manager
	}

	/**
	 * This method is invoked when the comunication system needs to deliver a message related to timeouts for a pending TOM message
	 *
	 * @param msg The timeout related message being delivered
	 */
	@SuppressWarnings("unchecked")
	public void deliverTimeoutRequest(RTMessage msg) {
		switch (msg.getRTType()) {
			case TOMUtil.RT_TIMEOUT: {
				if (log.isLoggable(Level.FINE)) {
					log.fine("Receiving timeout message from " + msg.getSender());
				}
				List<byte[][]> serializedRequestList = (List<byte[][]>) msg.getContent();

				for (Iterator<byte[][]> i = serializedRequestList.iterator(); i.hasNext();) {
					byte[][] serializedRequest = i.next();

					if (serializedRequest == null || serializedRequest.length != 2) {
						return;
					}

					TOMMessage request = null;

					//deserialize the message
					try {
						ByteBuffer buf = ByteBuffer.wrap(serializedRequest[0]);
						request = new TOMMessage(buf);
					} catch (Exception e) {
						e.printStackTrace();
						if (log.isLoggable(Level.WARNING)) {
							log.warning("Invalid request.");
						}
						return;
					}

					request.setBytes(serializedRequest[0]);
					request.serializedMessageSignature = serializedRequest[1];

					if (tomlayer.clientsManager.checkAndRecordRequest(request, false, true)) { //Is this a pending message?
						RTInfo rti = getTimeoutInfo(request.getId());
						timeout(msg.getSender().intValue(), request, rti);
					} else {
						log.log(Level.FINE, "Ignoring timeout for request {0}", request);
					}
				}
			}
			break;
			case TOMUtil.RT_COLLECT: {
				if (log.isLoggable(Level.FINE)) {
					log.fine("Receiving collect for message " + msg.getReqId() + " from " + msg.getSender());
				}
				SignedObject so = (SignedObject) msg.getContent();
				if (verifier.verifySignature(so, msg.getSender().intValue())) { // valid signature?
					try {
						RTCollect rtc = (RTCollect) so.getObject();
						Integer reqId = rtc.getReqId();

						Integer nl = chooseNewLeader();

						if (conf.getProcessId().equals(nl) && nl.equals(rtc.getNewLeader())) { // If this is process the new leader?
							RTInfo rti = getTimeoutInfo(reqId);
							collect(so, msg.getSender(), rti);
						}
					} catch (ClassNotFoundException cnfe) {
						cnfe.printStackTrace(System.err);
					} catch (IOException ioe) {
						ioe.printStackTrace(System.err);
					}
				}
			}
			break;
			case TOMUtil.RT_LEADER: {
				if (log.isLoggable(Level.FINE)) {
					log.fine("I received newLeader from " + msg.getSender());
				}
				RTLeaderChange rtLC = (RTLeaderChange) msg.getContent();
				RTCollect[] rtc = getValidProofs(msg.getReqId(), rtLC.proof);

				if (rtLC.isAGoodStartLeader(rtc, conf.getF())) { // Is it a legitm and valid leader?
					if (log.isLoggable(Level.FINE)) {
						log.fine("Updating leader to " + rtLC.newLeader + " onwards from from round " + rtLC.start);
					}
					updateLeader(msg.getReqId(), rtLC.start, rtLC.newLeader);
					//FALTA... eliminar dados referentes a consensos maiores q start.
				}
			}
			break;
		}
	}

	/**
	 * Retrieves the timeout information for a given timeout. If the timeout info does not exist, we create one.
	 *
	 * @param reqId ID of the message which triggered the timeout
	 * @return The timeout information
	 */
	public RTInfo getTimeoutInfo(Integer reqId) {
		lockTI.lock();
		RTInfo ti = timeoutInfo.get(reqId);
		if (ti == null) {
			ti = new RTInfo(this.conf, reqId);
			timeoutInfo.put(reqId, ti);
		}
		lockTI.unlock();
		return ti;
	}

	/**
	 * Removes the timeout information for a given timeout.
	 *
	 * @param reqId ID of the message which triggered the timeout
	 * @return The timeout information
	 */
	private void removeTimeoutInfo(Integer reqId) {
		lockTI.lock();
		timeoutInfo.remove(reqId);
		lockTI.unlock();
	}

	/**
	 * Invoked by the TOM layer to notify that a timeout ocurred in a replica, and to compute the necessary tasks
	 *
	 * @param a Replica ID where this timeout occurred
	 * @param request the request that provoked the timeout
	 * @param rti the timeout info for this request
	 */
	public void timeout(int acceptor, TOMMessage request, RTInfo rti) {
		rti.setTimeout(acceptor);

		Integer reqId = rti.getRequestId();

		if (rti.countTimeouts() > execManager.quorumF && !rti.isTimeout(conf.getProcessId().intValue())) {
			rti.setTimeout(conf.getProcessId().intValue());

			List<byte[][]> serializedRequestList = new LinkedList<byte[][]>();
			serializedRequestList.add(
					new byte[][]{request.getBytes(), request.serializedMessageSignature});

			sendTimeoutMessage(serializedRequestList);
			/*
			 * if (requestsTimer != null) { requestsTimer.startTimer(clientsManager.getPending(reqId)); }
			 */
		}

		if (rti.countTimeouts() > execManager.quorumStrong && !rti.isCollected()) {
			rti.setCollected();
			/*
			 * requestsTimer.stopTimer(clientsManager.getPending(reqId));
			 */
			execManager.stop();

			Integer newLeader = chooseNewLeader();

			Long last = execManager.state.getLastExec();

			if (log.isLoggable(Level.FINE)) {
				log.fine("Sending COLLECT to " + newLeader
						+ " for " + reqId + " with last execution = " + last);
			}
			sendCollectMessage(reqId, new RTCollect(newLeader, last, reqId));
		}
	}

	/**
	 * Invoked by the TOM layer when a collect message is received, and to compute the necessary tasks
	 *
	 * @param c Proof from the replica that sent the message
	 * @param sender ID of the replica which sent the message
	 */
	public void collect(SignedObject c, Integer sender, RTInfo rti) {
		rti.setCollect(sender.intValue(), c);

		if (rti.countCollect() > 2 * conf.getF() && !rti.isNewLeaderSent()) {
			rti.setNewLeaderSent();

			SignedObject collect[] = rti.getCollect();

			RTCollect[] rtc = new RTCollect[collect.length];
			for (int i = 0; i < collect.length; i++) {
				if (collect[i] != null) {
					try {
						rtc[i] = (RTCollect) collect[i].getObject();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			RTInfo.NextLeaderAndConsensusInfo nextLeaderCons =
					rti.getStartLeader(rtc, conf.getF());
			RTLeaderChange rtLC = new RTLeaderChange(collect, nextLeaderCons.leader,
					nextLeaderCons.cons);

			sendNewLeaderMessage(rti.getRequestId(), rtLC);
		}
	}

	@SuppressWarnings("boxing")
	private Integer chooseNewLeader() {
		Integer lastRoundNumber = Round.ROUND_ZERO; //the number of the last round successfully executed

		Execution lastExec = execManager.getExecution(execManager.state.getLastExec());
		if (lastExec != null) {
			Round lastRound = lastExec.getDecisionRound();
			if (lastRound != null) {
				lastRoundNumber = lastRound.getNumber();
			}
		}
		return (lm.getLeader(execManager.state.getLastExec(), lastRoundNumber) + 1) % conf.getN();
	}

	/**
	 * Gets an array of valid RTCollect proofs
	 *
	 * @param reqId ID of the message which triggered the timeout
	 * @param timeout Timeout number
	 * @param proof Array of signed objects containing the proofs to be verified
	 * @return The sub-set of proofs that are valid
	 */
	private RTCollect[] getValidProofs(Integer reqId, SignedObject[] proof) {
		Collection<RTCollect> valid = new HashSet<RTCollect>();
		try {
			for (int i = 0; i < proof.length; i++) {
				if (proof[i] != null && verifier.verifySignature(proof[i], i)) { // is the signature valid?
					RTCollect rtc = (RTCollect) proof[i].getObject();
					// Does this proof refers to the specified message id and timeout?
					if (rtc != null && rtc.getReqId().equals(reqId)) {
						valid.add(rtc);
					}

				}
			}
		} catch (Exception e) {
			e.printStackTrace(System.out);
		}

		return valid.toArray(new RTCollect[0]); // return the valid proofs ans an array
	}
//
//	public void setIdle() {
//		if (log.isLoggable(Level.FINEST)) {
//			log.finest("Setting Requesthandler to idle after " + this.inExecution);
//		}
//
//		leaderLock.lock();
//		this.inExecution = IDLE;
//		//ot.addUpdate();
//		iAmLeader.signalAll();
//		leaderLock.unlock();
//	}

	public void notifyChangedConditions() {
		leaderLock.lock();
		iAmLeader.signalAll();
		leaderLock.unlock();
	}

//	public boolean isIdle() {
//		try {
//			leaderLock.lock();
//			return inExecution.equals(IDLE) 
//					&& !execManager.getExecution(lastExecuted).isActive();
//		} finally {
//			leaderLock.unlock();
//		}
//	}
	
}
