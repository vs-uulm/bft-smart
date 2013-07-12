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

import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.communication.ServerCommunicationSystem;
import navigators.smart.paxosatwar.executionmanager.Execution;
import navigators.smart.paxosatwar.executionmanager.ExecutionManager;
import navigators.smart.paxosatwar.executionmanager.ProofVerifier;
import navigators.smart.paxosatwar.executionmanager.ProofVerifier.GoodInfo;
import navigators.smart.paxosatwar.executionmanager.Round;
import navigators.smart.paxosatwar.messages.Collect;
import navigators.smart.paxosatwar.messages.CollectProof;
import navigators.smart.paxosatwar.messages.MessageFactory;
import navigators.smart.paxosatwar.messages.Proof;
import static navigators.smart.paxosatwar.roles.Acceptor.msclog;
import static navigators.smart.paxosatwar.roles.Acceptor.msctlog;
import navigators.smart.tom.util.Statistics;
import navigators.smart.tom.util.TOMConfiguration;

/**
 * This class represents the proposer role in the paxos protocol.
 *
 */
public class Proposer {

    public static final Logger log = Logger.getLogger(Proposer.class.getCanonicalName());
    private ExecutionManager manager = null; // Execution manager of consensus's executions
    private MessageFactory factory; // Factory for PaW messages
    private ProofVerifier verifier; // Verifier for proofs
    private ServerCommunicationSystem communication; // Replicas comunication system
    private TOMConfiguration conf; // TOM configuration

    /**
     * Creates a new instance of Proposer
     *
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
     *
     * @param manager Execution manager
     */
    public void setManager(ExecutionManager manager) {
        this.manager = manager;
    }

    /**
     * This method is called by the TOMLayer (or any other) to start the
     * execution of one instance of the paxos protocol.
     *
     * @param eid ID for the consensus's execution to be started
     * @param value Value to be proposed
     */
    public void startExecution(Long eid, byte[] value) {
        
        if(log.isLoggable(Level.FINER)){
            log.finer(eid + " | 0 | STARTING");
        }
        if (msclog.isLoggable(Level.INFO)) {
            Integer[] acc = manager.getOtherAcceptors();
            msclog.log(Level.INFO, "#Starting {0}-{1}", new Object[]{eid, 0});
            for (int i = 0; i < acc.length; i++) {
                msclog.log(Level.INFO, "{0} >-- {1} P{2}-{3}",
                        new Object[]{conf.getProcessId(), acc[i], eid, 0});
            }
        }

        if (msctlog.isLoggable(Level.INFO)) {
            Integer[] acc = manager.getOtherAcceptors();

            msctlog.log(Level.INFO, "taskChangedState| -t #time| 0x{0}| Proposing P{1}-{2}|",
                    new Object[]{conf.getProcessId(), eid, 0});
            for (int i = 0; i < acc.length; i++) {
                String id = String.format("P%1$d-%2$d-%3$d-%4$d", conf.getProcessId(),
                        acc[i], eid, 0);
                msctlog.log(Level.INFO, "ms| -t #time| -i {1,number,integer}| 0x{0}| 0| {2}|",
                        new Object[]{conf.getProcessId(), Math.abs(id.hashCode()), id});
            }
        }
		Round r = manager.getExecution(eid).getRound(Round.ROUND_ZERO);
		r.setProposed();
        communication.send(manager.getAcceptors(),
                factory.createPropose(eid, Round.ROUND_ZERO, conf.getProcessId(), value, null));
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
            log.log(Level.FINE, "Discarding collect: ", msg);
        }
    }

    /**
     * This method is executed when a COLLECT message is received.
     *
     * @param msg the collectReceived message
     */
    private void collectReceived(Collect msg) {
        Execution execution = manager.getExecution(msg.eid);
        if(execution == null){
        	//Execution is removed already, return
        	return;
        }
        try {
            execution.lock.lock();
            
            if(execution.isRemoved()){
            	//Execution has just been removed, return.
            	return;
            }
            CollectProof cp = msg.getProof();
            
            // Logging outputs for logfile and message sequence chart logs
            if (log.isLoggable(Level.FINER)) {
            	log.finer(msg.toString()+" | received");
            }
            msclog.log(Level.INFO, "{0} --> {1} C{2}-{3}",
            		new Object[]{msg.getSender(), conf.getProcessId(),
            		execution.getId(), msg.round});
            String id = String.format("C%1$d-%2$d-%3$d-%4$d", msg.getSender(),
            		conf.getProcessId(), execution.getId(), msg.round);
            msctlog.log(Level.INFO, "mr| -t #time| -i {0,number,integer}| 0x{1}| 4| {2}|",
            		new Object[]{Math.abs(id.hashCode()), conf.getProcessId(), id});
            if (cp != null && verifier.validSignature(cp, msg.getSender().intValue())) {
                if (log.isLoggable(Level.FINEST)) {
                    log.log(Level.FINEST, "{0} | {1} | COLLECT SIGNED",
                            new Object[]{msg.eid, msg.round});
                }
                if ((cp.getProofs() != null)
                        && verifier.validCollectProof(execution.getId(),
                        msg.round, cp.getProofs()) // proofs are valid
                        && (cp.getLeader() == conf.getProcessId())) {	// this is current leader
                    Integer nextRoundNumber = msg.round + 1;

                    if (log.isLoggable(Level.FINEST)) {
                        log.log(Level.FINEST, "{0} | {1} | COLLECT VALID",
                                new Object[]{execution.getId(), nextRoundNumber});
                    }
                    Round round = execution.getRound(nextRoundNumber);
                    round.setCollectProof(msg.getSender(), cp);

                    log.log(Level.FINEST, "{0} | {1} | {2} VALID PROOFS",
                            new Object[]{execution.getId(), nextRoundNumber,
                                verifier.countProofs(round.proofs)});
                    if (verifier.countProofs(round.proofs) > manager.quorumStrong && !round.isProposed()) {
                        createPropose(execution, round);
                    }
                } else {
                    if (cp.getProofs() == null) {
                        log.warning(msg.toString() + " | No proofs provided");
                    }
                    if (!verifier.validCollectProof(execution.getId(),
                            msg.round, cp.getProofs())) {
                        log.warning(msg.toString() + " | CollectProof invalid");
                    }
                    if (cp.getLeader() != conf.getProcessId()) {
                        log.warning(msg.toString() + " | I got a CollectProof for a Round where I"
                                + "am not the leader!");
                    }
                }
            } else {
                if (cp == null) {
                    log.log(Level.WARNING, msg.toString() + " | Collect with no collectproof received");
                } else {
                    log.log(Level.WARNING, msg.toString() + " | Collect with invalid signature received");
                }
            }
        } finally {
            execution.lock.unlock();
        }
    }

    /**
     * Creates a Propose with the given proofs and a good value.
     *
     * @param execution The current execution
     * @param round The new round where to propose
     */
	private void createPropose(Execution execution, Round round) {
        GoodInfo info = verifier.getGoodValue(round.proofs, round.getNumber());
//		manager.getRequestHandler().imAmTheLeader();

        //Count view changes in statistics
        Statistics.stats.viewChange();
        if (log.isLoggable(Level.FINEST)) {
            log.log(Level.FINEST, "{0} | {1} | STARTING",
                    new Object[]{execution.getId(), round.getNumber()});
        }

        //Log Propose to message sequence chart logfiles
        if (msclog.isLoggable(Level.INFO)) {
            Integer[] acc = manager.getOtherAcceptors();
            msctlog.log(Level.INFO, "taskChangedState| 0x{i}| Proposing P{1}-{2}|\n", new Object[]{conf.getProcessId(), execution.getId(), 0});
            for (int i = 0; i < acc.length; i++) {
                msclog.log(Level.INFO, "{0} >-- {1} P{2}-{3}",
                        new Object[]{conf.getProcessId(), acc[i],
                            execution.getId(), round.getNumber()});
            }
        }
        if (msctlog.isLoggable(Level.INFO)) {
            Integer[] acc = manager.getOtherAcceptors();
            for (int i = 0; i < acc.length; i++) {
                String id = String.format("P%1$d-%2$d-%3$d-%4$d",
                        conf.getProcessId(), acc[i], execution.getId(), round.getNumber());
                msctlog.log(Level.INFO, "ms| -t #time| -i {1,number,integer}| 0x{0}| 0| {2}|",
                        new Object[]{conf.getProcessId(), Math.abs(id.hashCode()), id});
            }
        }
		// TODO check if we need to calculate this
		Integer prevleader = (info.proposer == null) 
				? conf.getProcessId() : info.proposer;
		
        //Send propose
        communication.send(manager.getAcceptors(),
                factory.createPropose(execution.getId(), round.getNumber(), 
				prevleader, info.val, new Proof(round.proofs)));
    }
}
