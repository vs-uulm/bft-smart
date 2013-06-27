/**
 * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and
 * the authors indicated in the
 *
 * @author tags
 *
 * This file is part of SMaRt.
 *
 * SMaRt is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * SMaRt is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SMaRt. If not, see <http://www.gnu.org/licenses/>.
 */
package navigators.smart.paxosatwar.roles;

import java.security.SignedObject;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import navigators.smart.communication.ServerCommunicationSystem;
import static navigators.smart.paxosatwar.executionmanager.Round.ROUND_ZERO;
import navigators.smart.paxosatwar.executionmanager.*;
import navigators.smart.paxosatwar.messages.*;
import navigators.smart.paxosatwar.requesthandler.RequestHandler;
import navigators.smart.tom.core.TOMLayer;
import navigators.smart.tom.core.timer.messages.RTCollect;
import navigators.smart.tom.util.Statistics;
import navigators.smart.tom.util.TOMConfiguration;

/**
 * This class represents the acceptor role in the paxos protocol. This class
 * work together with the TOMulticastLayer class in order to supply a atomic
 * multicast service.
 *
 * @author Alysson Bessani
 */
@SuppressWarnings({"LoggerStringConcat", "ClassWithMultipleLoggers"})
public class Acceptor {

    public static final Logger msclog = Logger.getLogger("MSCLogger");
    public static final Logger msctlog = Logger.getLogger("MSCTracer");
    private static final Logger log = Logger.getLogger(Acceptor.class.getCanonicalName());
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

    /**
     * Creates a new instance of Acceptor.
     *
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
    }

    /**
     * Makes a RTCollect object with this process private key
     *
     * @param rtc RTCollect object to be signed
     * @return A SignedObject containing 'rtc'
     */
    public SignedObject sign(RTCollect rtc) {
        return this.verifier.sign(rtc);
    }

    /**
     * Sets the execution manager for this acceptor
     *
     * @param manager Execution manager for this acceptor
     */
    public void setManager(ExecutionManager manager) {
        this.manager = manager;
    }

    /**
     * Sets the TOM layer for this acceptor
     *
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
     * Called when a paxos message is received or when a out of context message
     * must be processed. It processes the received messsage acording to its
     * type
     *
     * @param msg The message to be processed
     */
    public void processMessage(PaxosMessage msg) {
        Execution execution = manager.getExecution(msg.getEid());

        try {
            execution.lock.lock();

            Round round = execution.getRound(msg.getRound());

            if (log.isLoggable(Level.FINER)) {
                log.finer(msg.toString() + " | PROCESSING");
            }

            // Messages must also be processed when the round is frozen, otherwise we would need decide messages to prevent single frozen
            // replicas from beeing blocked
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
                    break;
                default:
                    log.severe("Unknowm Messagetype received: " + msg);
            }

        } finally {
            execution.lock.unlock();
        }
    }

    /**
     * Called when a PROPOSE message is received or when processing a formerly
     * out of context propose which belongs to the current execution.
     *
     * @param msg The PROPOSE message to by processed
     */
    @SuppressWarnings("boxing")
    public void proposeReceived(Round round, Propose msg) {
        
        Integer sender = msg.getSender();
        Long eid = round.getExecution().getId();
//        Integer leader = leaderModule.getLeader(eid, msg.getRound());

        // Log reception
        if (sender != conf.getProcessId()) {
            msclog.log(Level.INFO, "{0} --> {1} P{2}-{3}", new Object[]{sender,
                        conf.getProcessId(), eid, round.getNumber()});
            String id = String.format("P%1$d-%2$d-%3$d-%4$d", sender, conf.getProcessId(), eid, round.getNumber());

            msctlog.log(Level.INFO, "mr| -t #time| -i {0,number,integer}| 0x{1}| 0| {2}|",
                    new Object[]{Math.abs(id.hashCode()), conf.getProcessId(), id});
        }
		
		// check if the leader is correct or unkown
		if (msg.getRound() == 0 && !leaderModule.checkAndSetLeader(eid,msg.getRound(),sender)) {
			round.storedProposes.add(msg);
			return;
		}

        handlePropose(round,  msg);
    }
	
	private void handlePropose(Round round, Propose msg){
		byte[] value = msg.getValue();
		// Proposals in round 0 are always valid and admissible
        if (msg.getRound().equals(ROUND_ZERO)) {
            log.log(Level.FINE, "Processing propose for {0}-{1} normally", new Object[]{round.getExecution().getId(), round.getNumber()});
            executePropose(round, value);
        } else {
            log.log(Level.FINE, "Checking propose for {0}-{1} for goodness", new Object[]{round.getExecution().getId(), round.getNumber()});
            checkPropose(round, msg);
        }
	}

    private void checkPropose(Round round, Propose msg) {
        Proof proof = msg.getProof();
        Long eid = round.getExecution().getId();

        if (proof != null) {

            // Get valid proofs
            CollectProof[] collected = verifier.checkValid(eid, msg.getRound() - 1, proof.getProofs());

            // check if proposer is valid leader
            if (verifier.isTheLeader(msg.getSender(), collected)) {
                leaderModule.setLeaderInfo(eid, msg.getRound(), msg.getSender());

                // Is the proposed value good according to the PaW algorithm?
                if (msg.getValue() != null && (verifier.good(msg.getValue(), collected, msg.getRound()))) {
                    executePropose(round, msg.getValue());
                } else {
					if (msg.getValue() == null ) {
						log.info(msg + " | Proposed value null");
					} else {
						log.info(msg + " | Proposed value NOT GOOD");
					}
				}

//				else if (checkAndDiscardConsensus(eid, collected, msg.getRound())) {
//					leaderModule.addLeaderInfo(eid, 0, msg.getSender());
//				}

//				TODO Why did they handle the next round here ??
//				//Is there a next value to be proposed, and is it good
//				//according to the PaW algorithm
//				if (proof.getNextPropose() != null && verifier.good(proof.getNextPropose(), collected, false)) {
//					Integer nextRoundNumber = verifier.getNextExecRound(collected);
//					if (requesthandler.getInExec().equals(eid + 1)) { // Is this message from the previous execution?
//						Execution nextExecution = manager.getExecution(eid + 1);
//						nextExecution.removeRounds(nextRoundNumber - 1);
//
//						executePropose(nextExecution.getRound(nextRoundNumber), msg.getValue());
//					} else {
//						nextProp = new AcceptedPropose(eid + 1, round.getNumber(), value, proof);
//					}
//				} else {
//					if (checkAndDiscardConsensus(eid + 1, collected, false)) {
//						leaderModule.addLeaderInfo(eid + 1, 0, msg.getSender());
//					}
//				}
            } else {
				log.log(Level.INFO,"{0} | invalid leader for this proposal", new Object[]{msg });
			}
        } else {
			log.log(Level.INFO,"{0} | {1} | no proofs provided", new Object[]{eid, round.getNumber()});
		}
    }

//	/**
//	 * Discards information related to a consensus
//	 * TODO Remove this
//	 * @param eid Consensus execution ID
//	 * @param proof
//	 * @param in
//	 * @return true if the leader have to be changed and false otherwise
//	 */
//	@SuppressWarnings("boxing")
//	private boolean checkAndDiscardConsensus(Long eid, CollectProof[] proof, Integer round) {
//		if (requesthandler.getLastExec() < eid) {
//			if (verifier.getGoodValue(proof,round) == null) {
//				//br.ufsc.das.util.//Logger.println("Descartando o consenso "+eid);
//				if (requesthandler.isInExec(eid)) {
//					requesthandler.setIdle();
//				}
//				Execution exec = manager.removeExecution(eid);
//				if (exec != null) {
//					
//					exec.removeRoundsandCancelTO(-1);//cancela os timeouts dos rounds
//				}
//				if (requesthandler.getNextExec().equals(eid)) {
//					return true;
//				}
//			}
//		}
//
//		return false;
//	}
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
            try {
                execution.lock.lock();

                Round round = execution.getRound(nextProp.r);
                executePropose(round, nextProp.value);
                nextProp = null;
                return true;
            } finally {
                execution.lock.unlock();
            }
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
            log.finer( eid + " | " + round.getNumber() + " | executing PROPOSE with value "+Arrays.toString(value));
        }
		
		if(!round.isProposed()){
			round.setProposed();
			round.scheduleTimeout();
			byte[] hash = null;
			if (round.getPropValue() == null) {
				 hash = tomlayer.computeHash(value);
				round.setpropValue(value, hash);
			} else {
				hash = round.getPropValueHash();
			}
			
			if (log.isLoggable(Level.FINEST)) {
				log.finest( eid + " | " + round.getNumber() + " | Hash is "+Arrays.toString(hash));
			}

			//TODO Check if this was needed.
//			if(round.getExecution().getDecisionRound().equals(round)){
//				round.getExecution().decided(round);
//			}

			//start this execution if it is not already running
			if (eid.intValue() == manager.getNextExecID()) {
				manager.setInExec(eid);
			}
			Object deserialised = tomlayer.checkProposedValue(value);
			if (deserialised != null) {
				round.getExecution().getConsensus().setDeserialisedDecision(deserialised);

				//Only send msg when not frozen
				if (!round.isFrozen()) {
					if (log.isLoggable(Level.FINER)) {
						log.finer( eid + " | " + round.getNumber() + " | sending WEAK");
					}

					int weakcount = round.setWeak(me.intValue(), hash);		//set myself as weak acceptor
					communication.send(manager.getOtherAcceptors(),
							factory.createWeak(eid, round.getNumber(), hash));
					computeWeak(eid, round, hash, weakcount);		//compute weak if i just sent a weak

					if (Acceptor.msclog.isLoggable(Level.INFO)) {
						Integer[] acc = manager.getOtherAcceptors();
						for (int i = 0; i < acc.length; i++) {
							msclog.log(Level.INFO, "{0} >-- {1} W{2}-{3}", new Object[]{conf.getProcessId(), acc[i], eid, round.getNumber()});
						}
					}
					if (Acceptor.msctlog.isLoggable(Level.INFO)) {
						Integer[] acc = manager.getOtherAcceptors();
						for (int i = 0; i < acc.length; i++) {
							String id = String.format("W%1$d-%2$d-%3$d-%4$d",
									conf.getProcessId(), acc[i], eid, round.getNumber());
							msctlog.log(Level.INFO, "ms| -t #time|"
									+ " -i {1,number,integer}| 0x{0}| 1| {2}|",
									new Object[]{conf.getProcessId(), Math.abs(id.hashCode()), id});
						}
					}
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

        if (msclog.isLoggable(Level.INFO) && sender != conf.getProcessId()) {
            msclog.log(Level.INFO, "{0} --> {1} W{2}-{3}", new Object[]{sender,
                        conf.getProcessId(), eid, round.getNumber()});
        }
        if (msctlog.isLoggable(Level.INFO) && sender != conf.getProcessId()) {
            String id = String.format("W%1$d-%2$d-%3$d-%4$d", sender,
                    conf.getProcessId(), eid, round.getNumber());
            msctlog.log(Level.INFO, "mr| -t #time| -i {0,number,integer}| 0x{1}| 1| {2}|",
                    new Object[]{Math.abs(id.hashCode()), conf.getProcessId(), id});
        }
        int count = round.setWeak(sender, value);
        computeWeak(eid, round, value, count);
    }

    /**
     * Computes weakly accepted values according to the standard PaW
     * specification (sends STRONG/DECIDE messages, according to the number of
     * weakly accepted values received).
     * 
	 * @param eid The id of the execution of this message
     * @param round Round of the receives message
     * @param valuehash Value sent in the message
	 * @param weakAccepted  The number of accepted values
     */
    private void computeWeak(final Long eid, final Round round, final byte[] valuehash, final int weakAccepted) {

        if (log.isLoggable(Level.FINER)) {
            log.finer( eid + " | " + round.getNumber() + " | " + weakAccepted
                    + " WEAKS");
        }

        //Schedule timeout if not yet scheduled when one correct replica indicates
        //the existance of this round
        if (weakAccepted > manager.quorumF) {
			 //start this execution if it is not already running
            if (eid.intValue() == manager.getNextExecID()) {
                manager.setInExec(eid);
            }
			// We have no proposed value even though we get equal weaks for some
			// check if we got one from a now invalid leader due to
			// a freeze that matches
			if(round.getPropValue() == null){
				for(Propose p:round.storedProposes){
					if(Arrays.equals(valuehash,tomlayer.computeHash(p.getValue()))){
						round.setpropValue(p.getValue(), valuehash);
						handlePropose(round, p);
					}
				}
			}
            round.scheduleTimeout();
        }

        // Can I go straight to decided state?
        if (weakAccepted > manager.quorumFastDecide && !round.isDecided()) {
            if (log.isLoggable(Level.FINE)) {
                log.fine( eid + " | " + round.getNumber() + " | DECIDE (WEAK)");
            }
            decide(eid, round, valuehash);
        }

        // shall I send a STRONG message?
        if (weakAccepted > manager.quorumStrong) {
            if (!(round.isStrongSetted(me.intValue()) || round.isFrozen())) {
                int count = round.setStrong(me, valuehash);
                sendStrong(eid, round, valuehash);
                computeStrong(eid, round, valuehash,count);
            }
        }
    }

    /**
     * Sends a strong message. Depending on the setup of the replica, the
     * sending is delayed to suppress unnecessary strong messages.
     *
     * @param eid The current execution id
     * @param round The current round
	 * @param valuehash The message to send
     */
    private void sendStrong(final Long eid, final Round round, final byte[] valuehash) {
        if (log.isLoggable(Level.FINER)) {
            log.finer( eid + " | " + round.getNumber() + " | Sending STRONG");
        }
        if (msclog.isLoggable(Level.INFO)) {
            Integer[] acc = manager.getOtherAcceptors();
            for (int i = 0; i < acc.length; i++) {
                msclog.log(Level.INFO, "{0} >-- {1} S{2}-{3}", new Object[]{conf.getProcessId(), acc[i], eid, round.getNumber()});
            }
        }
        if (msctlog.isLoggable(Level.INFO)) {
            Integer[] acc = manager.getOtherAcceptors();
            for (int i = 0; i < acc.length; i++) {
                String id = String.format("S%1$d-%2$d-%3$d-%4$d",
                        conf.getProcessId(), acc[i], eid, round.getNumber());
                msctlog.log(Level.INFO, "ms| -t #time| -i {1,number,integer}| "
                        + "0x{0}| 2| {2}|", new Object[]{conf.getProcessId(),
                            Math.abs(id.hashCode()), id});
            }
        }
        communication.send(manager.getOtherAcceptors(),
                factory.createStrong(eid, round.getNumber(), valuehash));
    }

    /**
     * Called when a STRONG message is received
     *
     * @param round Round of the receives message
     * @param sender Replica that sent the message
     * @param valuehash Value sent in the message
     */
    @SuppressWarnings("boxing")
    private void strongAcceptReceived(Round round, Integer sender, byte[] valuehash) {
        Long eid = round.getExecution().getId();

        if (msclog.isLoggable(Level.INFO) && sender != conf.getProcessId()) {
            msclog.log(Level.INFO, "{0} --> {1} S{2}-{3}", new Object[]{sender, conf.getProcessId(), eid, round.getNumber()});
        }
        if (msctlog.isLoggable(Level.INFO) && sender != conf.getProcessId()) {
            String id = String.format("S%1$d-%2$d-%3$d-%4$d", sender,
                    conf.getProcessId(), eid, round.getNumber());
            msctlog.log(Level.INFO, "mr| -t #time| -i {0,number,integer}| 0x{1}|"
                    + " 2| {2}|", new Object[]{Math.abs(id.hashCode()),
                        conf.getProcessId(), id});
        }
        int count = round.setStrong(sender, valuehash);
        computeStrong(eid, round, valuehash, count);
    }

    /**
     * Computes strongly accepted values according to the standard PaW
     * specification (sends DECIDE messages, according to the number of strongly
     * accepted values received)
     * 
	 * @param eid The id of the execution of this message
     * @param round Round of the receives message
     * @param valuehash Value sent in the message
	 * @param strongAccepted The number of accepted values
     */
    private void computeStrong(Long eid, Round round, byte[] valuehash, int strongAccepted) {

        if (log.isLoggable(Level.FINER)) {
            log.finer( eid + " | " + round.getNumber() + " | " + strongAccepted
                    + " STRONGS");
        }

        if (strongAccepted > manager.quorum2F && !round.isDecided()) {

            if (log.isLoggable(Level.FINE)) {
                log.fine( eid + " | " + round.getNumber() + " | DECIDE(STRONG)");
            }
            decide(eid, round, valuehash);
        }
    }

    /**
     * Called when a DECIDE message is received. Computes decided values
     * according to the standard PaW specification
     *
     * @param round Round of the receives message
     * @param sender Replica that sent the message
     * @param valuehash Value sent in the message
     */
    @SuppressWarnings("boxing")
    private void decideReceived(Round round, Integer sender, byte[] valuehash) {
        Long eid = round.getExecution().getId();
        round.setDecide(sender, valuehash);

        if (round.countDecide(valuehash) > manager.quorumF && !round.isDecided()) {
            if (log.isLoggable(Level.FINER)) {
                log.fine( eid + " | " + round.getNumber() + " | DECIDE MSG DECIDE");
            }
            decide(eid, round, valuehash);
        } else if (round.isDecided()) {
            if (log.isLoggable(Level.FINER)) {
                log.fine( eid + " | " + round.getNumber() + " | already decided.");
            }
        }
    }

    /**
     * This mehod is called by timertasks associated with rounds. It will
     * locally freeze a round, given that is not already frozen, its not
     * decided, and is not removed from its execution
     *
     * @param round
     */
    public void timeout(Round round) {
        Execution execution = round.getExecution();
        execution.lock.lock();

        if (log.isLoggable(Level.INFO)) {
            log.info( round.getExecution() + " | " + round.getNumber() + " | TIMEOUT");
        }

        if (!round.isDecided()) {
            // Send freeze msg to all acceptors including me
            checkFreezeMsg(round);
            doFreeze(round);
        }

        execution.lock.unlock();
    }

    /**
     * Called when a FREEZE message is received.
     *
     * @param round Round of the receives message
     * @param sender Replica that sent the message
     */
    private void freezeReceived(Round round, Integer sender) {
        
        if (msclog.isLoggable(Level.INFO) && sender != conf.getProcessId()) {
            msclog.log(Level.INFO, "{0} --> {1} F{2}-{3}", new Object[]{sender, conf.getProcessId(), round.getExecution().getId(), round.getNumber()});
        }
        if (msctlog.isLoggable(Level.INFO) && sender != conf.getProcessId()) {
            String id = String.format("F%1$d-%2$d-%3$d-%4$d",
                    sender, conf.getProcessId(), round.getExecution().getId(),
                    round.getNumber());
            msctlog.log(Level.INFO, "mr| -t #time| -i {0,number,integer}| 0x{1}|"
                    + " 3| {2}|", new Object[]{Math.abs(id.hashCode()), conf.getProcessId(), id});
        }
        round.addFreeze(sender);
        if (round.countFreeze() > manager.quorumF) {
            if (!round.isFrozen()) {
                doFreeze(round);
            }
            checkFreezeMsg(round);
        }
		// Retry stored proposes if the leader for later rounds changed
        if(computeFreeze(round)){
			for(Propose p:round.storedProposes){
				proposeReceived(round, p);
			}
		}
    }

    private void checkFreezeMsg(Round round) {
        if (!round.isTimeout()) {
            Statistics.stats.timeout();
            round.setTimeout();
            if (msclog.isLoggable(Level.INFO)) {
                Integer[] acc = manager.getOtherAcceptors();
                for (int i = 0; i < acc.length; i++) {
                    msclog.log(Level.INFO, "{0} >-- {1} F{2}-{3}",
                            new Object[]{conf.getProcessId(), acc[i],
                                round.getExecution().getId(), round.getNumber()});
                }
            }
            if (msctlog.isLoggable(Level.INFO)) {
                Integer[] acc = manager.getOtherAcceptors();
                for (int i = 0; i < acc.length; i++) {
                    String id = String.format("F%1$d-%2$d-%3$d-%4$d",
                            conf.getProcessId(), acc[i], round.getExecution().getId(),
                            round.getNumber());
                    msctlog.log(Level.INFO, "ms| -t #time| -i {1,number,integer}|"
                            + " 0x{0}| 3| {2}|", new Object[]{conf.getProcessId(),
                                Math.abs(id.hashCode()), id});
                }
            }
            communication.send(manager.getAcceptors(),
                    factory.createFreeze(round.getExecution().getId(), round.getNumber()));
        }
    }

    private void doFreeze(Round round) {
        if (log.isLoggable(Level.FINER)) {
            log.finer( round.getExecution() + " | " + round.getNumber() + " | FREEZING round");
        }

        msclog.log(Level.INFO, "{0} note: freezing Round: {1}-{2}", new Object[]{me, round.getExecution().getId(), round.getNumber()});
        msctlog.log(Level.INFO, "ps| -t #time| 0x{0}| freezing Round: {1}-{2}|", new Object[]{me, round.getExecution().getId(), round.getNumber()});

        round.freeze();

    }

    /**
     * Invoked when a timeout for a round is triggered, or when a FREEZE message
     * is received. Computes wether or not to locally freeze this round
     * according to the standard PaW specification
     *
     * @param round Round of the receives message
     * @param value Value sent in the message
	 * @return true if the round was collected, false otherwise
     */
    @SuppressWarnings("boxing")
    private boolean computeFreeze(Round round) {
        if (log.isLoggable(Level.FINER)) {
            log.finer( round.getExecution() + " | " + round.getNumber() + " | " + round.countFreeze() + " FREEZES");
        }
        //if there is more than f+1 timeouts
        if (round.countFreeze() > manager.quorumF && !round.isCollected()) {
            Execution exec = round.getExecution();
            Round nextRound = exec.getRound(round.getNumber() + 1);

            round.collect();

            exec.nextRound();	//Set active round to next round

            // schedule TO if not scheduled yet
            nextRound.scheduleTimeout();
            leaderModule.freezeRound(exec.getId(), round.getNumber());

            //Create signed W_s and S_s for all rounds up to this one in order to send them to the new proposer.
            LinkedList<FreezeProof> proofs = new LinkedList<FreezeProof>();
            for (Round r : exec.getRounds()) {
                if (r.getNumber() < nextRound.getNumber()) {	// add only smaller rounds
                    proofs.add(createProof(exec.getId(), r));
                }
            }

            CollectProof clProof = new CollectProof(proofs, newNextLeader);

            verifier.sign(clProof);
			
            msclog.log(Level.INFO, "{0} >-- {1} C{2}-{3}", new Object[]{conf.getProcessId(), 
                newNextLeader, exec.getId(), round.getNumber()});
			if (msctlog.isLoggable(Level.INFO)) {
				String id = String.format("C%1$d-%2$d-%3$d-%4$d", conf.getProcessId(),
						newNextLeader, exec.getId(), round.getNumber());
				msctlog.log(Level.INFO, "ms| -t #time| -i {1,number,integer}| 0x{0}|"
						+ " 4| {2}|", new Object[]{conf.getProcessId(), Math.abs(id.hashCode()), id});
			}
			
            communication.send(new Integer[]{newNextLeader},
                    factory.createCollect(exec.getId(), round.getNumber(), clProof));
			return true;
        } else {
            log.log(Level.FINEST,"{0} | {1} | nothing to do - freezes: {2} collected: {3}",
                    new Object[]{round.getExecution(), round.getNumber(),
                        round.countFreeze(),round.isCollected()});
			return false;
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
        return new FreezeProof(me, eid, r.getNumber(), r.getPropValue(), r.isWeakSetted(me),
                r.isStrongSetted(me), r.getDecide(me.intValue()) != null);
    }

    /**
     * This is the method invoked when a value is decided by this process
     *
     * @param round Round at which the decision is made
     * @param value The decided value (got from WEAK or STRONG messages)
     */
    private void decide(Long eid, Round round, byte[] value) {
        if (msclog.isLoggable(Level.INFO)) {
            msclog.log(Level.INFO, "{0} note: {1}-{2} decided", new Object[]{me, eid, round.getNumber()});
        }

        msctlog.log(Level.INFO, "ps| -t #time| 0x{0}| Deciding Round {1}-{2}|", new Object[]{me, round.getExecution().getId(), round.getNumber()});
		
		if (log.isLoggable(Level.FINER)){
			log.log(Level.FINER,"{0} | {1} DECIDED",new Object[]{eid,round.getNumber()});
		}

        if (conf.isDecideMessagesEnabled()) {
            round.setDecide(me.intValue(), value);
            communication.send(manager.getOtherAcceptors(),
                    factory.createDecide(eid, round.getNumber(), round.getPropValueHash()));
        }
        //Set next leader to be the same as this round if not frozen
        if (!round.isFrozen()) {
            leaderModule.decided(round.getExecution().getId(),
                    leaderModule.getLeader(round.getExecution().getId(), round.getNumber()));
			leaderModule.decided(round);
        }
        
        round.decided();
      
    }

    /**
     * This class is a data structure for a propose that was accepted
     */
    private class AcceptedPropose {

        public Long eid;
        public Integer r;
        public byte[] value;
        public Proof p;

        public AcceptedPropose(Long eid, Integer r, byte[] value, Proof p) {
            this.eid = eid;
            this.r = r;
            this.value = value;
            this.p = p;
        }
    }
}
