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
package navigators.smart.paxosatwar.roles;

import java.security.SignedObject;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.communication.ServerCommunicationSystem;
import navigators.smart.paxosatwar.executionmanager.Execution;
import navigators.smart.paxosatwar.executionmanager.ExecutionManager;
import navigators.smart.paxosatwar.executionmanager.LeaderModule;
import navigators.smart.paxosatwar.executionmanager.ProofVerifier;
import navigators.smart.paxosatwar.executionmanager.Round;
import navigators.smart.paxosatwar.executionmanager.TimeoutTask;
import navigators.smart.paxosatwar.messages.CollectProof;
import navigators.smart.paxosatwar.messages.FreezeProof;
import navigators.smart.paxosatwar.messages.MessageFactory;
import navigators.smart.paxosatwar.messages.PaxosMessage;
import navigators.smart.paxosatwar.messages.Proof;
import navigators.smart.paxosatwar.messages.Propose;
import navigators.smart.paxosatwar.messages.VoteMessage;
import navigators.smart.paxosatwar.requesthandler.RequestHandler;
import navigators.smart.tom.core.TOMLayer;
import navigators.smart.tom.core.timer.messages.RTCollect;
import navigators.smart.tom.util.TOMConfiguration;

import static navigators.smart.paxosatwar.executionmanager.Round.ROUND_ZERO;

import static navigators.smart.paxosatwar.executionmanager.Round.ROUND_ZERO;

/**
 * This class represents the acceptor role in the paxos protocol.
 * This class work together with the TOMulticastLayer class in order to
 * supply a atomic multicast service.
 *
 * @author Alysson Bessani
 */
public class Acceptor {

	private static final Logger log = Logger.getLogger(Acceptor.class.getCanonicalName());
	private ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(5); // scheduler for timeouts
	private Integer me; // This replica ID
	private ExecutionManager manager; // Execution manager of consensus's executions
	private final MessageFactory factory; // Factory for PaW messages
	private final ProofVerifier verifier; // Verifier for proofs
	private final ServerCommunicationSystem communication; // Replicas comunication system
	private final LeaderModule leaderModule; // Manager for information about leaders
	private RequestHandler requesthandler; // requesthandler
	private final TOMLayer tomlayer;
	private AcceptedPropose nextProp = null; // next value to be proposed
	private final TOMConfiguration conf; // TOM configuration
	private final Timer strongtimer;	//timer for delaying strongs

	/**
	 * Creates a new instance of Acceptor.
	 * @param communication Replicas comunication system
	 * @param factory Message factory for PaW messages
	 * @param verifier Proof verifier
	 * @param conf TOM configuration
	 */
	public Acceptor(ServerCommunicationSystem communication, MessageFactory factory,
			ProofVerifier verifier, LeaderModule lm, TOMConfiguration conf, TOMLayer layer) {
		this.communication = communication;
		this.me = conf.getProcessId();
		this.factory = factory;
		this.verifier = verifier;
		this.leaderModule = lm;
		this.conf = conf;
		this.tomlayer = layer;
		if (conf.getStrongDelay() > 0) {
			strongtimer = new Timer("Strong message delay timer");
		} else {
			strongtimer = null;
		}
	}

	/**
	 * Makes a RTCollect object with this process private key
	 * @param rtc RTCollect object to be signed
	 * @return A SignedObject containing 'rtc'
	 */
	public SignedObject sign(RTCollect rtc) {
		return this.verifier.sign(rtc);
	}

	/**
	 * Sets the execution manager for this acceptor
	 * @param manager Execution manager for this acceptor
	 */
	public void setManager(ExecutionManager manager) {
		this.manager = manager;
	}

	/**
	 * Sets the TOM layer for this acceptor
	 * @param tom TOM layer for this acceptor
	 */
	public void setRequesthandler(RequestHandler tom) {
		this.requesthandler = tom;
	}

	/**
	 * Called by communication layer to delivery paxos messages. This method
	 * only verifies if the message can be executed and calls process message
	 * (storing it on an out of context message buffer if this is not the case)
	 * 
	 * @param msg Paxos messages delivered by the comunication layer
	 */
	public final void deliver(PaxosMessage msg) {
		if (manager.checkLimits(msg)) {
			processMessage(msg);
		}
	}

	/**
	 * Called when a paxos message is received or when a out of context message must be processed.
	 * It processes the received messsage acording to its type
	 *
	 * @param msg The message to be processed
	 */
	public void processMessage(PaxosMessage msg) {
		Execution execution = manager.getExecution(msg.getNumber());

		try {
			execution.lock.lock();

			Round round = execution.getRound(msg.getRound());

			switch (msg.getPaxosType()) {
				case MessageFactory.PROPOSE:
					proposeReceived(round, (Propose) msg);
					break;
				case MessageFactory.WEAK:
					weakAcceptReceived(round, msg.getSender(), ((VoteMessage) msg).getValue());
					break;
				case MessageFactory.STRONG:
					strongAcceptReceived(round, msg.getSender(), ((VoteMessage) msg).getValue());
					break;
				case MessageFactory.DECIDE:
					decideReceived(round, msg.getSender(), ((VoteMessage) msg).getValue());
					break;
				case MessageFactory.FREEZE:
					freezeReceived(round, msg.getSender());
			}
		} finally {
			execution.lock.unlock();
		}
	}

	/**
	 * Called when a PROPOSE message is received or when processing a formerly out of context propose which
	 * is know belongs to the current execution.
	 * 
	 * @param msg The PROPOSE message to by processed
	 */
	@SuppressWarnings("boxing")
	public void proposeReceived(Round round, Propose msg) {
		byte[] value = msg.getValue();
		Integer sender = msg.getSender();
		Long eid = round.getExecution().getId();

		if (log.isLoggable(Level.FINER)) {
			log.finer("PROPOSE for " + round.getNumber() + "," + round.getExecution().getId() + " received from " + sender);
		}

		// If message's round is 0, and the sender is the leader for the message's round,
		// execute the propose
		Integer leader = leaderModule.getLeader(eid, msg.getRound());
		// TODO why is the leader here null sometimes when a state transfer occurred
		if (msg.getRound().equals(ROUND_ZERO) && leader != null && leader.equals(sender)) {
			executePropose(round, value);
		} else {
			Proof proof = msg.getProof();
			if (proof != null) {

				// Get valid proofs
				CollectProof[] collected = verifier.checkValid(eid, msg.getRound() - 1, proof.getProofs());

				if (verifier.isTheLeader(sender, collected)) { // Is the replica that sent this message the leader?

					leaderModule.addLeaderInfo(eid, msg.getRound(), sender);

					// Is the proposed value good according to the PaW algorithm?
					if (value != null && (verifier.good(value, collected, true))) {
						executePropose(round, value);
					} else if (checkAndDiscardConsensus(eid, collected, true)) {
						leaderModule.addLeaderInfo(eid, 0, sender);
					}

					//Is there a next value to be proposed, and is it good
					//according to the PaW algorithm
					if (proof.getNextPropose() != null && verifier.good(proof.getNextPropose(), collected, false)) {
						Integer nextRoundNumber = verifier.getNextExecRound(collected);
						if (requesthandler.getInExec().equals(eid + 1)) { // Is this message from the previous execution?
							Execution nextExecution = manager.getExecution(eid + 1);
							nextExecution.removeRounds(nextRoundNumber - 1);

							executePropose(nextExecution.getRound(nextRoundNumber), value);
						} else {
							nextProp = new AcceptedPropose(eid + 1, round.getNumber(), value, proof);
						}
					} else {
						if (checkAndDiscardConsensus(eid + 1, collected, false)) {
							leaderModule.addLeaderInfo(eid + 1, 0, sender);
						}
					}
				}
			}
		}
	}

	/**
	 * Discards information related to a consensus
	 *
	 * @param eid Consensus execution ID
	 * @param proof
	 * @param in
	 * @return true if the leader have to be changed and false otherwise
	 */
	@SuppressWarnings("boxing")
	private boolean checkAndDiscardConsensus(Long eid, CollectProof[] proof, boolean in) {
		if (requesthandler.getLastExec() < eid) {
			if (verifier.getGoodValue(proof, in) == null) {
				//br.ufsc.das.util.//Logger.println("Descartando o consenso "+eid);
				if (requesthandler.isInExec(eid)) {
					requesthandler.setIdle();
				}
				Execution exec = manager.removeExecution(eid);
				if (exec != null) {
					exec.removeRounds(-1);//cancela os timeouts dos rounds
				}
				if (requesthandler.getNextExec().equals(eid)) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * Called by the delivery thread. Executes the next accepted propose.
	 *
	 * @param eid Consensus's execution ID
	 * @return True if there is a next value to be proposed and it belongs to
	 * the specified execution, false otherwise
	 */
	public boolean executeAcceptedPendent(Long eid) {
		if (nextProp != null && nextProp.eid.equals(eid)) {
			if (log.isLoggable(Level.FINER)) {
				log.finer("Executing accepted propose for " + eid);
			}
			Execution execution = manager.getExecution(eid);
			execution.lock.lock();

			Round round = execution.getRound(nextProp.r);
			executePropose(round, nextProp.value);
			nextProp = null;

			execution.lock.unlock();
			return true;
		} else {
			nextProp = null;
			return false;
		}
	}

	/**
	 * Executes actions related to a proposed value.
	 *
	 * @param round the current round of the execution
	 * @param value Value that is proposed
	 */
	@SuppressWarnings("unchecked")
	private void executePropose(Round round, byte[] value) {
		Long eid = round.getExecution().getId();
		if (log.isLoggable(Level.FINER)) {
			log.finer("executing propose for " + eid + "," + round.getNumber());
		}

		if (round.propValue == null) {
			round.propValue = value;
			round.propValueHash = tomlayer.computeHash(value);

			//start this execution if it is not already running
			if (eid.intValue() == requesthandler.getLastExec().intValue() + 1) {
				requesthandler.setInExec(eid);
			}
			Object deserialised = tomlayer.checkProposedValue(value);
			if (deserialised != null) {
				round.getExecution().getConsensus().setDeserialisedDecision(deserialised);

				if (checkAndSendWeak(eid, round)) {             	//send weak if necessary 
					computeWeak(eid, round, value);	    //compute weak if i just sent a weak
				} else if (round.getExecution().isDecided()) {
					round.getExecution().decided(round);
				}
			}
		}
	}

	/**
	 * Called when a WEAK message is received
	 *
	 * @param round Round of the receives message
	 * @param sender Replica that sent the message
	 * @param value Value sent in the message
	 */
	@SuppressWarnings("boxing")
	private void weakAcceptReceived(Round round, Integer sender, byte[] value) {
		Long eid = round.getExecution().getId();
		if (log.isLoggable(Level.FINER)) {
			log.finer("WEAK from " + sender + " for consensus " + eid);
		}
		round.setWeak(sender, value);

		//if this value was not accepted yet and there is a quorum after i accepted it and 
		//the current id is not yet set to this value set the current exec
		if (checkAndSendWeak(eid, round) && round.countWeak(value) > manager.quorumF && eid.equals(requesthandler.getNextExec())) {
			requesthandler.setInExec(eid);
		}

		computeWeak(eid, round, value);
	}

	/**
	 * Checks if the value for this execution and round was already accepted weakly. If not
	 * it is accepted and a weak message is sent.
	 * @param eid The execution id to check for
	 * @param round The round to check for
	 * @param valuehash The hash of the value to propose
	 * @return true if i newly accepted the value, false if it was already accepted before
	 */
	private boolean checkAndSendWeak(Long eid, Round round) {
		if (!round.isWeakSetted(me.intValue())) { //send weak if necessary
			if (log.isLoggable(Level.FINER)) {
				log.finer("sending weak for " + eid);
			}

			round.setWeak(me.intValue(), round.propValueHash); //set myself as weak acceptor
			communication.send(manager.getOtherAcceptors(), factory.createWeak(eid, round.getNumber(), round.propValueHash));
			return true;
		}
		return false;
	}

	/**
	 * Computes weakly accepted values according to the standard PaW specification
	 * (sends STRONG/DECIDE messages, according to the number of weakly accepted
	 * values received).
	 *
	 * @param eid Execution ID of the received message
	 * @param round Round of the receives message
	 * @param valuehash Has of the value sent in the message
	 */
	private void computeWeak(final Long eid, final Round round, final byte[] valuehash) {

		int weakAccepted = round.countWeak(valuehash);

		if (log.isLoggable(Level.FINER)) {
			log.finer("I have " + weakAccepted
					+ " weaks for " + eid + "," + round.getNumber());
		}

		// Can I go straight to a DECIDE message?
		if (weakAccepted > manager.quorumFastDecide && !round.getExecution().isDecided()) {
			if (log.isLoggable(Level.FINE) && eid.intValue() % 250 == 0) {
				log.fine("Deciding " + eid + " with weaks");
			}
			decide(eid, round, valuehash);
		}

		if (weakAccepted > manager.quorumStrong) { // shall I send a STRONG message?
			if (!round.isStrongSetted(me.intValue())) {
				if (log.isLoggable(Level.FINER)) {
					log.finer("sending STRONG for " + eid);
				}

				round.setStrong(me, valuehash);
				if (conf.getStrongDelay() > 0) {
					round.setStrongtask(new TimerTask() {

						@Override
						public void run() {
							communication.send(manager.getOtherAcceptors(),
									factory.createStrong(eid, round.getNumber(), valuehash));
						}
					});
					strongtimer.schedule(round.getStrongtask(), conf.getStrongDelay());
				} else {
					communication.send(manager.getOtherAcceptors(),
							factory.createStrong(eid, round.getNumber(), valuehash));
				}
				computeStrong(eid, round, valuehash);
			}

		}
	}

	/**
	 * Called when a STRONG message is received
	 * @param eid Execution ID of the received message
	 * @param round Round of the receives message
	 * @param sender Replica that sent the message
	 * @param value Value sent in the message
	 */
	@SuppressWarnings("boxing")
	private void strongAcceptReceived(Round round, Integer sender, byte[] value) {
		Long eid = round.getExecution().getId();
		if (log.isLoggable(Level.FINER)) {
			log.finer("STRONG from " + sender + " for consensus " + eid);
		}
		round.setStrong(sender, value);
		computeStrong(eid, round, value);
	}

	/**
	 * Computes strongly accepted values according to the standard PaW specification (sends
	 * DECIDE messages, according to the number of strongly accepted values received)
	 * @param round Round of the receives message
	 * @param value Value sent in the message
	 */
	private void computeStrong(Long eid, Round round, byte[] value) {
		if (log.isLoggable(Level.FINER)) {
			log.finer("I have " + round.countStrong(value)
					+ " strongs for " + eid + "," + round.getNumber());
		}

		if (round.countStrong(value) > manager.quorum2F && !round.getExecution().isDecided()) {

			if (log.isLoggable(Level.FINE) && eid.intValue() % 250 == 0) {
				log.fine("Deciding " + eid + " with strongs");
			}
			decide(eid, round, value);
		}
	}

	/**
	 * Called when a DECIDE message is received. Computes decided values
	 * according to the standard PaW specification
	 * @param round Round of the receives message
	 * @param sender Replica that sent the message
	 * @param value Value sent in the message
	 */
	@SuppressWarnings("boxing")
	private void decideReceived(Round round, Integer sender, byte[] value) {
		Long eid = round.getExecution().getId();
		if (log.isLoggable(Level.FINER)) {
			log.finer("DECIDE from " + sender + " for consensus " + eid);
		}
		round.setDecide(sender, value);

		if (round.countDecide(value) > manager.quorumF && !round.getExecution().isDecided()) {
			if (log.isLoggable(Level.FINER)) {
				log.finer("Deciding " + eid);
			}
			decide(eid, round, value);
		} else if (round.getExecution().isDecided()) {
			if (log.isLoggable(Level.FINER)) {
				log.finer("consensus " + eid + " already decided.");
			}
		}
	}

	/**
	 * Schedules a timeout for a given round. It is called by an Execution when a new round is created.
	 * @param round Round to be associated with the timeout
	 */
	public void scheduleTimeout(Round round) {
		if (log.isLoggable(Level.FINER)) {
			log.finer("scheduling timeout of " + round.getTimeout() + " ms for round " + round.getNumber() + " of consensus " + round.getExecution().getId());
		}
		TimeoutTask task = new TimeoutTask(this, round);
		ScheduledFuture<?> future = timer.schedule(task, round.getTimeout(), TimeUnit.MILLISECONDS);
		round.setTimeoutTask(future);
		//purge timer every 100 timeouts
		if (round.getExecution().getId().longValue() % 100 == 0) {
			timer.purge();
		}
	}

	/**
	 * This mehod is called by timertasks associated with rounds. It will locally freeze
	 * a round, given that is not already frozen, its not decided, and is not removed from
	 * its execution
	 *
	 * @param round
	 */
	public void timeout(Round round) {
		Execution execution = round.getExecution();
		execution.lock.lock();

		if (log.isLoggable(Level.FINER)) {
			log.finer("timeout for round " + round.getNumber() + " of consensus " + execution.getId());
		}
		//System.out.println(round);

		if (!round.getExecution().isDecided() && !round.isFrozen() && !round.isRemoved()) {
			doFreeze(round);
			computeFreeze(round);
		}

		execution.lock.unlock();
	}

	/**
	 * Called when a FREEZE message is received.
	 * @param round Round of the receives message
	 * @param sender Replica that sent the message
	 */
	private void freezeReceived(Round round, Integer sender) {
		if (log.isLoggable(Level.FINER)) {
			log.finer("received freeze from " + sender + " for " + round.getNumber() + " of consensus " + round.getExecution().getId());
		}
		round.addFreeze(sender);
		if (round.countFreeze() > manager.quorumF && !round.isFrozen()) {
			doFreeze(round);
		}
		computeFreeze(round);
	}

	private void doFreeze(Round round) {
		if (log.isLoggable(Level.FINER)) {
			log.finer("freezing round " + round.getNumber() + " of execution " + round.getExecution().getId());
		}
		round.freeze();
		communication.send(manager.getOtherAcceptors(),
				factory.createFreeze(round.getExecution().getId(), round.getNumber()));
	}

	/**
	 * Invoked when a timeout for a round is triggered, or when a FREEZE message is received.
	 * Computes wether or not to locally freeze this round according to the standard PaW specification
	 *
	 * @param round Round of the receives message
	 * @param value Value sent in the message
	 */
	@SuppressWarnings("boxing")
	private void computeFreeze(Round round) {
		if (log.isLoggable(Level.FINER)) {
			log.finer("received " + round.countFreeze() + " freezes for round " + round.getNumber());
		}
		//if there is more than 2f+1 timeouts
		if (round.countFreeze() > manager.quorum2F && !round.isCollected()) {
			round.collect();
			round.getTimeoutTask().cancel(false);

			Execution exec = round.getExecution();
			Round nextRound = exec.getRound(round.getNumber() + 1, false);

			if (nextRound == null) { //If the next ro
				//create the next round
				nextRound = exec.getRound(round.getNumber() + 1);
				//define the leader for the next round: (previous_leader + 1) % N
				Integer newLeader = (leaderModule.getLeader(exec.getId(), round.getNumber()) + 1) % conf.getN();
				leaderModule.addLeaderInfo(exec.getId(), nextRound.getNumber() + 1, newLeader);
				if (log.isLoggable(Level.FINER)) {
					log.finer("new leader for the next round of consensus is " + newLeader);
				}

				if (exec.isDecided()) { //Does this process already decided a value?
					//Even if I already decided, I should move to the next round to prevent
					//process that not decided yet from blocking

					Round decisionRound = exec.getDecisionRound();

					//If the decision was reached on a previous round
					if (round.getNumber() > decisionRound.getNumber()) {
						Execution nextExec = manager.getExecution(exec.getId() + 1);
						Round last = nextExec.getLastRound();
						last.freeze();

						CollectProof clProof = new CollectProof(createProof(exec.getId(), round),
								createProof(exec.getId() + 1l, last), newLeader);

						verifier.sign(clProof);

						communication.send(new Integer[]{newLeader},
								factory.createCollect(exec.getId(), round.getNumber(), clProof));
					}
				} else {
					CollectProof clProof = new CollectProof(createProof(exec.getId(), round), null, newLeader);
					verifier.sign(clProof);
					communication.send(new Integer[]{newLeader}, factory.createCollect(exec.getId(), round.getNumber(), clProof));
				}
			} else {
				if (log.isLoggable(Level.FINER)) {
					log.finer("I'm already executing round " + round.getNumber() + 1);
				}
			}
		}
	}

	/**
	 * Creates a freeze proof for the given execution ID and round
	 *
	 * @param eid Consensus's execution ID
	 * @param r Round of the execution
	 * @return A freez proof
	 */
	private FreezeProof createProof(Long eid, Round r) {
		return new FreezeProof(me, eid, r.getNumber(), r.getWeak(me.intValue()),
				r.getStrong(me.intValue()), r.getDecide(me.intValue()));
	}

	/**
	 * This is the method invoked when a value is decided by this process
	 * @param round Round at which the decision is made
	 * @param value The decided value (got from WEAK or STRONG messages)
	 */
	private void decide(Long eid, Round round, byte[] value) {
		if (conf.isDecideMessagesEnabled()) {
			round.setDecide(me.intValue(), value);
			communication.send(manager.getOtherAcceptors(),
					factory.createDecide(eid, round.getNumber(), round.propValue));
		}
		//we are decided, cancel sending of strong
		if (round.getStrongtask() != null) {
			round.getStrongtask().cancel();
			strongtimer.purge();
		}

		leaderModule.decided(round.getExecution().getId(), leaderModule.getLeader(round.getExecution().getId(), round.getNumber()));
		round.getTimeoutTask().cancel(false);
		round.getExecution().decided(round/* , value */);
	}

	/**
	 * This class is a data structure for a propose that was accepted
	 */
	private class AcceptedPropose {

		public Long eid;
		public Integer r;
		public byte[] value;
		@SuppressWarnings("unused")
		public Proof p;

		public AcceptedPropose(Long eid, Integer r, byte[] value, Proof p) {
			this.eid = eid;
			this.r = r;
			this.value = value;
			this.p = p;
		}
	}
}
