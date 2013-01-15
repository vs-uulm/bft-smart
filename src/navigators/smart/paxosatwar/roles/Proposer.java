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

import java.util.logging.Level;
import java.util.logging.Logger;
import navigators.smart.communication.ServerCommunicationSystem;
import navigators.smart.paxosatwar.executionmanager.Execution;
import navigators.smart.paxosatwar.executionmanager.ExecutionManager;
import navigators.smart.paxosatwar.executionmanager.ProofVerifier;
import navigators.smart.paxosatwar.executionmanager.Round;
import navigators.smart.paxosatwar.messages.Collect;
import navigators.smart.paxosatwar.messages.CollectProof;
import navigators.smart.paxosatwar.messages.MessageFactory;
import navigators.smart.paxosatwar.messages.Proof;
import navigators.smart.tom.util.Statistics;
import navigators.smart.tom.util.TOMConfiguration;
import static navigators.smart.paxosatwar.roles.Acceptor.msclog;


/**
 * This class represents the proposer role in the paxos protocol.
 **/
public class Proposer {
	
	public static final Logger log = Logger.getLogger(Proposer.class.getCanonicalName());

    private ExecutionManager manager = null; // Execution manager of consensus's executions
    private MessageFactory factory; // Factory for PaW messages
    private ProofVerifier verifier; // Verifier for proofs
    private ServerCommunicationSystem communication; // Replicas comunication system
    private TOMConfiguration conf; // TOM configuration

    /**
     * Creates a new instance of Proposer
     * @param communication Replicas comunication system
     * @param factory Factory for PaW messages
     * @param verifier Proof verifier
     * @param conf TOM configuration
     */
    public Proposer(ServerCommunicationSystem communication, MessageFactory factory,
            ProofVerifier verifier, TOMConfiguration conf) {
        this.communication = communication;
        this.verifier = verifier;
        this.factory = factory;
        this.conf = conf;
    }

    /**
     * Sets the execution manager associated with this proposer
     * @param manager Execution manager
     */
    public void setManager(ExecutionManager manager) {
        this.manager = manager;
    }

    /**
     * This method is called by the TOMLayer (or any other)
     * to start the execution of one instance of the paxos protocol.
     * 
     * @param eid ID for the consensus's execution to be started
     * @param value Value to be proposed
     */
    public void startExecution(Long eid, byte[] value) {
		if (msclog.isLoggable(Level.INFO)){
			Integer[] acc = manager.getOtherAcceptors();
			msclog.log(Level.INFO,"#Starting {0}-{1}",new Object[]{eid,0});
			for (int i = 0; i < acc.length; i++) {
				msclog.log(Level.INFO,"{0} >-- {1} P{2}-{3}", new Object[] {conf.getProcessId(), acc[i],eid,0});
			}
		}
        communication.send(manager.getAcceptors(),
                factory.createPropose(eid, Round.ROUND_ZERO, value, null));
    }

    /**
     * This method only deals with COLLECT messages.
     *
     * @param msg the COLLECT message received
     */
    public void deliver(Collect msg) {
        if (manager.checkLimits(msg)) {
            collectReceived(msg);
        } else {
			log.log(Level.FINE,"Discarding collect: ", msg);
		}
    }

    /**
     * This method is executed when a COLLECT message is received.
     *
     * @param msg the collectReceived message
     */
    private void collectReceived(Collect msg) {
        if (log.isLoggable(Level.FINER)) {
			log.log(Level.FINER, "COLLECT for {0},{1} received.", new Object[]{msg.getNumber(), msg.getRound()});
		}
		
        Execution execution = manager.getExecution(msg.getNumber());
		
		msclog.log(Level.INFO,"{0} --> {1} C{2}-{3}", new Object[] {msg.getSender(),conf.getProcessId(),execution.getId(),msg.getRound()});
		
        execution.lock.lock();

        CollectProof cp =  msg.getProof();

        if (cp != null && verifier.validSignature(cp, msg.getSender().intValue())) {
//            CollectProof cp = null;
//            try {
//                cp = (CollectProof) proof.getObject();
//            } catch (Exception e) {
//                e.printStackTrace(System.out);
//            }

            if (log.isLoggable(Level.FINEST)) {
               log.log(Level.FINEST, " signed COLLECT for {0},{1} received.", 
					   new Object[]{msg.getNumber(), msg.getRound()});
			}
            
            if ((cp.getProofs(true) != null) &&
                    // the received proof (that the round was frozen) should be valid
                    verifier.validProof(execution.getId(), msg.getRound(), cp.getProofs(true)) &&
                    // this replica is the current leader
                    (cp.getLeader() == conf.getProcessId())) {

                Integer nextRoundNumber = Integer.valueOf(msg.getRound().intValue() + 1);

                if (log.isLoggable(Level.FINEST)) {
                    log.log(Level.FINEST, "Valid COLLECT for starting {0},{1} received.", 
							new Object[]{execution.getId(), nextRoundNumber});
				}

                Round round = execution.getRound(nextRoundNumber);
                
                round.setCollectProof(msg.getSender().intValue(),cp);

                if (verifier.countProofs(round.proofs) > manager.quorumStrong) {
					//Count view changes
					Statistics.stats.viewChange();
                    if (log.isLoggable(Level.FINEST)) {
                        log.log(Level.FINEST, "Proposing for {0},{1}", 
								new Object[]{execution.getId(), nextRoundNumber});
					}

                    byte[] inProp = verifier.getGoodValue(round.proofs, true);
                    byte[] nextProp = verifier.getGoodValue(round.proofs, false);

                    manager.getRequestHandler().imAmTheLeader();
					
					if (msclog.isLoggable(Level.INFO)){
						Integer[] acc = manager.getOtherAcceptors();
						for (int i = 0; i < acc.length; i++) {
							msclog.log(Level.INFO,"{0} >-- {1} P{2}-{3}", new Object[] {conf.getProcessId(), acc[i],execution.getId(),nextRoundNumber});
						}
					}

                    communication.send(manager.getAcceptors(),
                            factory.createPropose(execution.getId(), nextRoundNumber,
                            inProp, new Proof(round.proofs, nextProp)));
                }
            }
        }

        execution.lock.unlock();
    }

    /* Not used in JBP, but can be usefull for systems in which there are processes
    that are only proposers

    private void paxosMessageReceived(int eid, int rid, int msgType,
    int sender, Object value) {
    Round round = manager.getExecution(eid).getRound(rid);
    if(msgType == WEAK) {
    round.setWeak(sender, value);
    if(round.countWeak(value) > manager.quorumFastDecide) {
    manager.getExecution(eid).decide(value,round.getNumber());
    }
    } else if(msgType == STRONG) {
    round.setStrong(sender, value);
    if(round.countStrong(value) > manager.quorum2F) {
    manager.getExecution(eid).decide(value,round.getNumber());
    }
    } else if(msgType == DECIDE) {
    round.setDecide(sender, value);
    if(round.countDecide(value) > manager.quorumF) {
    manager.getExecution(eid).decide(value,round.getNumber());
    }
     }
    }
     */
}
