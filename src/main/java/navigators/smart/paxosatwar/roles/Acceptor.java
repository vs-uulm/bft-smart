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
package navigators.smart.paxosatwar.roles;

import java.security.SignedObject;
import java.util.Arrays;
import java.util.LinkedList;
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
    private final TOMLayer tomlayer;
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
     * Called by communication layer to delivery paxos messages. This method
     * only verifies if the message can be executed and calls process message
     * (storing it on an out of context message buffer if this is not the case)
     *
     * @param msg Paxos messages delivered by the comunication layer
     */
    public final void deliver(PaxosMessage msg) {
        if (manager.checkLimits(msg)) {
            processMessage(msg);
        } else {
        	if(log.isLoggable(Level.FINER)){
        		log.log(Level.FINER,"{0} | {1} Failed checkLimits: not processing now"
        				+ " message {2}", new Object[]{msg.eid, msg.round, msg});
        	}
        }
    }

    /**
     * Called when a paxos message is received or when a out of context message
     * must be processed. It processes the received messsage acording to its
     * type. It also checks if the current round number is less than this round,
	 * if so, the message is still not processed. This prevents malicious
	 * replicas from exploiting the round number space unnecessary.
     *
     * @param msg The message to be processed
     */
    public void processMessage(PaxosMessage msg) {
        Execution execution = manager.getExecution(msg.eid);
		if(execution == null){
			log.warning("Accepted message for unknown execution: "+msg.eid);
			return;
		}
        try {
            execution.lock.lock();
            if(execution.isRemoved()){
            	return;
            }

            Round round = execution.getRound(msg.round);
            
//            if(round.isCollected()){
//            	if(log.isLoggable(Level.FINER)){
//            		log.log(Level.FINER,"{0} | {1} Round collected, dropping "
//            				+ "message {2}", new Object[]{msg.eid, msg.round, msg});
//            	}
//            	return;
//            }

			// Do not handle message if the corresponding round
			// is not started yet.
			if(execution.getCurrentRoundNumber()<msg.round){
				if(log.isLoggable(Level.FINER)){
            		log.log(Level.FINER,"{0} | {1} Round not yet active, storing "
            				+ "message {2}", new Object[]{msg.eid, msg.round, msg});
            	}
				round.pending.add(msg);
				return;
			}
			
            if (log.isLoggable(Level.FINER)) {
                log.finer(msg.toString() + " | PROCESSING");
            }
            
            /*
             *  Messages must also be processed when the round is frozen,
             *  otherwise we would need decide messages to prevent 
             *  single frozen replicas from beeing blocked. 
             */
            switch (msg.paxosType) {
	            case MessageFactory.FREEZE:
	            	freezeReceived(round, msg.getSender());
	            	break;
                case MessageFactory.PROPOSE:
                    proposeReceived(round, (Propose) msg);
                    break;
                case MessageFactory.WEAK:
                    weakAcceptReceived(round, (VoteMessage)msg);
                    break;
                case MessageFactory.STRONG:
                    strongAcceptReceived(round, (VoteMessage)msg);
                    break;
                case MessageFactory.DECIDE:
                    decideReceived(round, (VoteMessage)msg);
                    break;
                default:
                    log.severe("Unknowm Messagetype received: " + msg);
            }

        } catch  (NullPointerException npe){
			log.log(Level.SEVERE, "NullPointer occurred in proces msg", npe);
			npe.printStackTrace();
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
//        Integer leader = leaderModule.getLeader(eid, msg.round);

        // Log reception
        if (sender != conf.getProcessId()) {
            msclog.log(Level.INFO, "{0} --> {1} P{2}-{3}", new Object[]{sender,
                        conf.getProcessId(), eid, round.getNumber()});
            String id = String.format("P%1$d-%2$d-%3$d-%4$d", sender, conf.getProcessId(), eid, round.getNumber());

            msctlog.log(Level.INFO, "mr| -t #time| -i {0,number,integer}| 0x{1}| 0| {2}|",
                    new Object[]{Math.abs(id.hashCode()), conf.getProcessId(), id});
        }
		

        handlePropose(round,  msg);
    }
	
	/**
	 * Handles a propose message for the given round. If the round is 0 several
	 * options are present.
	 * 
	 * 1. If the number of weaks received for the propose is > f, we accept 
	 * this propose as one correct acceptor did so either. This enables
	 * replicas that froze the last round alone to join the normal execution path
	 * again. It does not violate safety as the leader will either succeed with 
	 * this proposal or a new leader will choose a safe proposal and start
	 * round 1
	 * 
	 * If the weak count is still less than f but the leader appears to be valid,
	 * we also accept this proposal. Remember that the leader can become invalid
	 * at any time due to a timeout in the previous execution. If we got not
	 * enough weaks and the leader does not fit, we store the proposal for
	 * later checking.
	 * 
	 * @param round The round that this propose is for
	 * @param propose The actual propose message
	 */
	private void handlePropose(Round round, Propose propose){
		// Proposals in round 0 are not always... valid and admissible
        if (propose.round.equals(ROUND_ZERO)) {
			
			/*
			 * check if the leader is correct or unkown  or If we got f or more 
			 * weaks for this proposal, lets stick to its leader.
			 */
			if ( leaderModule.checkLeader(propose.eid,propose.round,propose.getSender())){
//					|| round.countWeak(propose.value) > manager.quorumF) {
				log.log(Level.FINE, "Processing propose for {0}-{1} normally", 
						new Object[]{round.getExecution().getId(), round.getNumber()});
				executePropose(round, propose);
			} else {
				log.log(Level.FINE, "Storing propose for {0}-{1} from invalid leader", 
						new Object[]{round.getExecution().getId(), round.getNumber()});
				round.storedProposes.add(propose);
				return;
			}
        } else {
            log.log(Level.FINE, "Checking propose for {0}-{1} for goodness", 
					new Object[]{round.getExecution().getId(), round.getNumber()});
            checkPropose(round, propose);
        }
	}

	/*
	 * Checks a propose message for rounds > 1 for correctness. If they
	 * are correct and the proposed value is not null, they are processed 
	 * normally, otherwise they are denied.
	 */
    private void checkPropose(Round round, Propose msg) {
        Proof proof = msg.getProof();
        Long eid = round.getExecution().getId();

        if (proof != null) {

            // Get valid proofs
            CollectProof[] collected = verifier.checkValid(eid, msg.round - 1, proof.getProofs());

            // check if proposer is valid leader
            if (verifier.isTheLeader(msg.getSender(), collected)) {

                // Is the proposed value good according to the PaW algorithm?
                if (msg.value != null 
                		&& (verifier.good(msg.value, collected, msg.round, msg.leader))) {
//                	leaderModule.setLeaderInfo(eid, msg.round, msg.getSender());
                    executePropose(round, msg);
                } else {
					if (msg.value == null ) {
						log.info(msg + " | Proposed value null");
					} else {
						log.info(msg + " | Proposed value NOT GOOD");
					}
				}

//				else if (checkAndDiscardConsensus(eid, collected, msg.round)) {
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
//						executePropose(nextExecution.getRound(nextRoundNumber), msg.value);
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
//    public boolean executeAcceptedPendent(Long eid) {
//        if (nextProp != null && nextProp.eid.equals(eid)) {
//            if (log.isLoggable(Level.FINER)) {
//                log.finer("Executing accepted propose for " + eid);
//            }
//            Execution execution = manager.getExecution(eid);
//            try {
//                execution.lock.lock();
//
//                Round round = execution.getRound(nextProp.r);
//                executePropose(round, nextProp.propose);
//                nextProp = null;
//                return true;
//            } finally {
//                execution.lock.unlock();
//            }
//        } else {
//            nextProp = null;
//            return false;
//        }
//    }

    /**
     * Executes actions related to a proposed value.
     *
     * @param round the current round of the execution
	 * @param leader the proposer of this value
     * @param value Value that is proposed
     */
    @SuppressWarnings("unchecked")
    private void executePropose(Round round, Propose p) {
        Long eid = round.getExecution().getId();
        if (log.isLoggable(Level.FINER)) {
            log.finer( eid + " | " + round.getNumber() + " | executing PROPOSE with value "+Arrays.toString(p.value));
        }
		
		if(!round.hasPropose()){			
			round.scheduleTimeout(p.leader);
			byte[] hash = null;
			if (round.getPropValue() == null) {
				hash = tomlayer.computeHash(p.value);
				round.setPropose(p, hash);
			} else {
				throw new RuntimeException("This propose should not be set twice");
			}
			
			if (log.isLoggable(Level.FINEST)) {
				log.finest( eid + " | " + round.getNumber() + " | Hash is "+Arrays.toString(hash));
			}

			//TODO Check if this was needed.
//			if(round.getExecution().getDecisionRound().equals(round)){
//				round.getExecution().decided(round);
//			}

			/*
			 * start this execution if it is not already running, but only
			 * when we did not execute it yet.
			 */
			manager.state.startExecution(eid);
			
			Object deserialised = tomlayer.checkProposedValue(p.value);
			if (deserialised != null) {
				round.getExecution().getConsensus().setDeserialisedDecision(deserialised);

				checkSendWeak(eid,round,p);
			}
		}
    }
	
	private void checkSendWeak(Long eid, Round round, Propose p) {
		//Only send msg when not frozen and previous exec is inactive
		if(!round.isFrozen()
				&& !round.isWeakSetted(me)){
			if (log.isLoggable(Level.FINER)) {
				log.finer(eid + " | " + round.getNumber() + " | sending WEAK");
			}
			VoteMessage weak = factory.createWeak(eid, round.getNumber(), p.value);
			int weakcount = round.setWeak(weak);		//set myself as weak acceptor
			communication.send(manager.getOtherAcceptors(),weak);
			computeWeak(eid, round, weak, weakcount);		//compute weak if i just sent a weak

			if (Acceptor.msclog.isLoggable(Level.INFO)) {
				Integer[] acc = manager.getOtherAcceptors();
				for (int i = 0; i < acc.length; i++) {
					msclog.log(Level.INFO, "{0} >-- {1} W{2}-{3}", 
							new Object[]{conf.getProcessId(), acc[i], 
								eid, round.getNumber()});
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
		} else {
			log.fine("Not sending weak, already sent earlier or round frozen");
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
    private void weakAcceptReceived(Round round, VoteMessage weak) {
        Long eid = round.getExecution().getId();

        if (msclog.isLoggable(Level.INFO) && weak.getSender() != conf.getProcessId()) {
            msclog.log(Level.INFO, "{0} --> {1} W{2}-{3}", new Object[]{weak.getSender(),
                        conf.getProcessId(), eid, round.getNumber()});
        }
        if (msctlog.isLoggable(Level.INFO) && weak.getSender() != conf.getProcessId()) {
            String id = String.format("W%1$d-%2$d-%3$d-%4$d", weak.getSender(),
                    conf.getProcessId(), eid, round.getNumber());
            msctlog.log(Level.INFO, "mr| -t #time| -i {0,number,integer}| 0x{1}| 1| {2}|",
                    new Object[]{Math.abs(id.hashCode()), conf.getProcessId(), id});
        }
        int count = round.setWeak(weak);
        computeWeak(eid, round, weak, count);
    }

    /**
     * Computes weakly accepted values according to the standard PaW
     * specification (sends STRONG/DECIDE messages, according to the number of
     * weakly accepted values received).
     * 
	 * @param eid The id of the execution of this message
     * @param round Round of the receives message
     * @param weak Value sent in the message
	 * @param weakAccepted  The number of accepted values
     */
    private void computeWeak(final Long eid, final Round round, final VoteMessage weak, final int weakAccepted) {

        if (log.isLoggable(Level.FINER)) {
            log.finer( eid + " | " + round.getNumber() + " | " + weakAccepted
                    + " WEAKS");
        }

        /*
         * Schedule timeout if not yet scheduled when one correct replica 
         * indicates the existance of this round
         */
        if (weakAccepted > manager.quorumF) {
			 //start this execution if it is not already running
            if (eid == manager.state.getNextExecID()) {
                manager.state.startExecution(eid);
            }
			/*
			 * We have no proposed value even though we get equal weaks for some
			 * check if we got one from a now invalid leader due to
			 * a freeze that matches TODO check this, with quorum f
			 * we get different leaders.
			 */
			if(round.getPropValue() == null && weakAccepted > manager.quorum2F){
				for(Propose p:round.storedProposes){
					if(log.isLoggable(Level.FINER)){
						log.log(Level.FINER, "{0} | {1} checking propose from {2} "
								+ "for equality with the weaks", 
								new Object[]{eid,round.getNumber(),p.getSender()});
						if(log.isLoggable(Level.FINEST)){
							log.log(Level.FINEST,"Weak: {0}",Arrays.toString(weak.value));
							log.log(Level.FINEST,"Prophash: {0}",Arrays.toString(tomlayer.computeHash(p.value)));
						}
					}
					if(Arrays.equals(weak.value,tomlayer.computeHash(p.value))){
						log.log(Level.FINER, "{0} | {1} Popose from {2} "
							+ "matches waks, processing...", 
							new Object[]{eid,round.getNumber(),p.getSender()});
//						round.setPropose( p, weak.value);
						handlePropose(round, p);
					}
				}
			}
//			Execution last = manager.getExecution(eid-1);
//			Round lastround = last.getCurrentRound();
//			if(lastround.countFreeze() <= manager.quorumF){
//				
//			}
			round.scheduleTimeout(round.getProposer());
        }

        // Can I go straight to decided state?
        if (round.hasPropose() && weakAccepted > manager.quorumFastDecide && !round.isDecided()) {
            if (log.isLoggable(Level.FINE)) {
                log.fine( eid + " | " + round.getNumber() + " | DECIDE (WEAK)");
            }
            decide(eid, round, weak);
        }

        int strongcount = checkSendStrong(eid, round, weak);
        
        computeStrong(eid, round, weak, strongcount);
    }

    /**
     * Sends a strong message. Depending on the setup of the replica, the
     * sending is delayed to suppress unnecessary strong messages.
     *
     * @param eid The current execution id
     * @param round The current round
	 * @param msg The message with the value to send
	 * @return The current number of strong messages for the weaks value hash.
     */
    private int checkSendStrong(final Long eid, final Round round, 
			final VoteMessage msg) {
		int weakAccepted = round.countWeak(msg.value);
		int count = 0;
        // shall I send a STRONG message?
		if(round.hasPropose()){
	        if (weakAccepted > manager.quorumStrong && 
	            !round.isStrongSetted(me.intValue()) &&
					! round.isFrozen()) {
				VoteMessage strong = factory.createStrong(eid, round.getNumber(), round.getPropValueHash());
				round.setStrong(strong);
				communication.send(manager.getOtherAcceptors(), strong);
				
				if (log.isLoggable(Level.FINER)) {
					log.finer( eid + " | " + round.getNumber() + " | Sending STRONG");
				}
				if (msclog.isLoggable(Level.INFO)) {
					Integer[] acc = manager.getOtherAcceptors();
					for (int i = 0; i < acc.length; i++) {
						msclog.log(Level.INFO, "{0} >-- {1} S{2}-{3}", 
								new Object[]{conf.getProcessId(), acc[i], eid, 
									round.getNumber()});
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
	        } else {
	        	round.countStrong(round.getPropValueHash());
	        }
		} 
		return count;
    }

    /**
     * Called when a STRONG message is received
     *
     * @param round Round of the receives message
     * @param sender Replica that sent the message
     * @param strong Value sent in the message
     */
    @SuppressWarnings("boxing")
    private void strongAcceptReceived(Round round, VoteMessage strong) {
        Long eid = round.getExecution().getId();

        if (msclog.isLoggable(Level.INFO) && strong.getSender() != conf.getProcessId()) {
            msclog.log(Level.INFO, "{0} --> {1} S{2}-{3}", new Object[]{strong.getSender(), conf.getProcessId(), eid, round.getNumber()});
        }
        if (msctlog.isLoggable(Level.INFO) && strong.getSender() != conf.getProcessId()) {
            String id = String.format("S%1$d-%2$d-%3$d-%4$d", strong.getSender(),
                    conf.getProcessId(), eid, round.getNumber());
            msctlog.log(Level.INFO, "mr| -t #time| -i {0,number,integer}| 0x{1}|"
                    + " 2| {2}|", new Object[]{Math.abs(id.hashCode()),
                        conf.getProcessId(), id});
        }
        int count = round.setStrong(strong);
        computeStrong(eid, round, strong, count);
    }

    /**
     * Computes strongly accepted values according to the standard PaW
     * specification (sends DECIDE messages, according to the number of strongly
     * accepted values received)
     * 
	 * @param eid The id of the execution of this message
     * @param round Round of the receives message
     * @param msg Value sent in the message
	 * @param strongAccepted The number of accepted values
     */
    private void computeStrong(Long eid, Round round, VoteMessage msg,
    		final int strongAccepted) {
//		int strongAccepted = round.countStrong(msg.value);

        if (log.isLoggable(Level.FINER)) {
            log.finer( eid + " | " + round.getNumber() + " | " + strongAccepted
                    + " STRONGS");
        }

        if (round.hasPropose() && strongAccepted > manager.quorum2F && !round.isDecided()) {

            if (log.isLoggable(Level.FINE)) {
                log.fine( eid + " | " + round.getNumber() + " | DECIDE(STRONG)");
            }
            decide(eid, round, msg);
        }
    }

    /**
     * Called when a DECIDE message is received. Computes decided values
     * according to the standard PaW specification
     *
     * @param round Round of the receives message
     * @param decide Value sent in the message
     */
    @SuppressWarnings("boxing")
    private void decideReceived(Round round, VoteMessage decide) {
        Long eid = round.getExecution().getId();
        round.setDecide(decide);

        if (round.countDecide(decide.value) > manager.quorumF && !round.isDecided()) {
            if (log.isLoggable(Level.FINER)) {
                log.fine( eid + " | " + round.getNumber() + " | DECIDE MSG DECIDE");
            }
            decide(eid, round, decide);
        } else if (round.isDecided()) {
            if (log.isLoggable(Level.FINER)) {
                log.fine( eid + " | " + round.getNumber() + " | already decided.");
            }
        }
    }

    /**
     * This method is called by timertasks associated with rounds. It will
     * locally freeze a round, given that is not already frozen, its not
     * decided, and is not removed from its execution
     *
     * @param round
     */
    public void timeout(Round round) {
        Execution execution = round.getExecution();
        execution.lock.lock();

        if (log.isLoggable(Level.FINE)) {
            log.fine( round.getExecution() + " | " + round.getNumber() + " | TIMEOUT");
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
		// TODO check
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
                    factory.createFreeze(round.getExecution().getId(), 
					round.getNumber()));
        }
    }

    private void doFreeze(Round round) {
        if (log.isLoggable(Level.FINER)) {
            log.finer( round.getExecution() + " | " + round.getNumber() + " | FREEZING round");
        }

        msclog.log(Level.INFO, "{0} note: freezing Round: {1}-{2}",
				new Object[]{me, round.getExecution().getId(), round.getNumber()});
        msctlog.log(Level.INFO, "ps| -t #time| 0x{0}| freezing Round: {1}-{2}|",
				new Object[]{me, round.getExecution().getId(), round.getNumber()});

        round.getExecution().freeze(round);

    }

    /**
     * Invoked when a timeout for a round is triggered, or when a FREEZE message
     * is received. Computes wether or not to locally freeze this round
     * according to the standard PaW specification. If the round is
	 * frozen by f+1 acceptors including ourselves we send a collectproof 
	 * to the designated new leader. This leader might change after we froze
	 * the round due to freezes in rounds lower than this one.
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

//            exec.nextRound();	//Set active round to next round

            Integer newNextLeader = leaderModule.collectRound(exec.getId(), round.getNumber());
            
			/* 
			 * schedule timeout of the next round if not scheduled yet and
			 * if i am not the leader. This is because I am honest to myself.
			 * This optimisation would not apply if the proposer is some
			 * separate replica that might fail. But still this would not
			 * directly violate safety as long as I am the only one that "fails"
			 * in that manner. 
			 */
			nextRound.scheduleTimeout(newNextLeader);

            /* 
			 * Create signed W_s and S_s for all rounds up to this one in order 
			 * to send them to the new proposer. This proof is constant, as
			 * we do not send messages after we freeze a round.
			 */
            LinkedList<FreezeProof> proofs = new LinkedList<FreezeProof>();
            for (Round r : exec.getRounds()) {
                if (r.getNumber() < nextRound.getNumber()) {	// add only smaller rounds
                    proofs.add(createProof(exec.getId(), r));
                }
            }
			
			Integer proposer = round.getInitialProposer() == null ? newNextLeader : round.getInitialProposer();
            CollectProof clProof = new CollectProof(proofs, newNextLeader,proposer);
            verifier.sign(clProof);
			
            msclog.log(Level.INFO, "{0} >-- {1} C{2}-{3}", new Object[]{conf.getProcessId(), 
                newNextLeader, exec.getId(), round.getNumber()});
			if (msctlog.isLoggable(Level.INFO)) {
				String id = String.format("C%1$d-%2$d-%3$d-%4$d", conf.getProcessId(),
						newNextLeader, exec.getId(), round.getNumber());
				msctlog.log(Level.INFO, "ms| -t #time| -i {1,number,integer}| 0x{0}|"
						+ " 4| {2}|", new Object[]{conf.getProcessId(), Math.abs(id.hashCode()), id});
			}
			
			round.setCollectProof(me, clProof);
			
            sendCollect(round, newNextLeader);
			
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
                r.isStrongSetted(me), r.isDecideSetted(me.intValue()));
    }

    /**
     * This is the method invoked when a value is decided by this process
     *
	 * @param eid The execution id of this decision
     * @param round Round at which the decision is made
     * @param msg The decided value (got from WEAK or STRONG messages)
     */
    private void decide(Long eid, Round round, VoteMessage msg) {
        if (msclog.isLoggable(Level.INFO)) {
            msclog.log(Level.INFO, "{0} note: {1}-{2} decided", new Object[]{me, eid, round.getNumber()});
        }

        msctlog.log(Level.INFO, "ps| -t #time| 0x{0}| Deciding Round {1}-{2}|", new Object[]{me, round.getExecution().getId(), round.getNumber()});
		
		if (log.isLoggable(Level.FINER)){
			log.log(Level.FINER,"{0} | {1} DECIDED",new Object[]{eid,round.getNumber()});
		}

        if (conf.isDecideMessagesEnabled()) {
			VoteMessage decide = factory.createDecide(eid, round.getNumber(), msg.value);
            round.setDecide(decide);
            communication.send(manager.getOtherAcceptors(), decide);
        }
//        //Set next leader to be the same as this round if not collected
//        if (!round.isCollected()) {
		leaderModule.decided(round.getExecution().getId(),
				round.getNumber(),round.getInitialProposer());
//        }
        
	    round.decided();
      
    }

	/**
	 * Sends a collect message to this designated new leader. The collectproof
	 * is stored in the collectproof array of the round and created when
	 * the round is collected.
	 * 
	 * @param leaderId The id of the receiver of the collect message
	 * @param r The round to send the collect for.
	 */
	public void sendCollect(Round r, Integer leaderId) {
		if(!r.isCollected()){
			throw new IllegalStateException("Illegal sending of collect message,"
					+ "the round was not yet collected");
		}
		communication.send(new Integer[]{leaderId},
                    factory.createCollect(r.getExecution().getId(), 
				r.getNumber(), r.proofs[me]));
	}

    /**
     * This class is a data structure for a propose that was accepted
     */
//    private class AcceptedPropose {
//
//        public Long eid;
//        public Integer r;
//        public Propose propose;
//        public Proof p;
//
//        public AcceptedPropose(Long eid, Integer r, Propose value, Proof p) {
//            this.eid = eid;
//            this.r = r;
//            this.propose = value;
//            this.p = p;
//        }
//    }
}
