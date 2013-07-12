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
package navigators.smart.paxosatwar.executionmanager;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import navigators.smart.consensus.MeasuringConsensus;
import static navigators.smart.paxosatwar.executionmanager.Round.ROUND_ZERO;
import navigators.smart.paxosatwar.messages.PaxosMessage;

/**
 * This class stands for an execution of a consensus
 */
public class Execution {

	public static final Logger log = Logger.getLogger(Execution.class.getCanonicalName());
	final ExecutionManager manager; // Execution manager for this execution
	private MeasuringConsensus consensus; // MeasuringConsensus instance to which this execution works for
	private SortedMap<Integer,Round> rounds = new TreeMap<Integer,Round>();
//    private HashMap<Integer,Round> rounds = new HashMap<Integer,Round>(2);
	private ReentrantLock roundsLock = new ReentrantLock(); // Lock for concurrency control
	private volatile boolean started = false; // Did we start this execution
    private volatile boolean executed = false; // Is the execution of this consensus decision finished.
	private long initialTimeout; // Initial timeout for rounds
	private Integer decisionRound = Integer.valueOf(-1); // round at which a desision was made
	private Integer currentRound = ROUND_ZERO; //Currently active round
	public final ReentrantLock lock = new ReentrantLock(); //this execution lock (called by other classes)
	public final Long eid;
	private boolean removed = false;

	/**
	 * Creates a new instance of Execution for Acceptor Manager
	 *
	 * @param eid The executions id
	 * @param manager Execution manager for this execution
	 * @param consensus MeasuringConsensus instance to which this execution works for
	 * @param initialTimeout Initial timeout for rounds
	 */
	@SuppressWarnings("rawtypes")
	protected Execution(Long eid, ExecutionManager manager, MeasuringConsensus consensus, 
	long initialTimeout) {
		this.eid = eid;
		this.manager = manager;
		this.consensus = consensus;
		this.initialTimeout = initialTimeout;
	}

	/**
	 * This is the execution ID
	 *
	 * @return Execution ID
	 */
	public Long getId() {
		return consensus.getId();
	}

	/**
	 * This is the execution manager for this execution
	 *
	 * @return Execution manager for this execution
	 */
	public ExecutionManager getManager() {
		return manager;
	}

	/**
	 * This is the consensus instance to which this execution works for
	 *
	 * @return MeasuringConsensus instance to which this execution works for
	 */
	@SuppressWarnings("rawtypes")
	public MeasuringConsensus getConsensus() {
		return consensus;
	}

	/**
	 * Gets a round associated with this execution. The round
	 * is created if nonexistant.
	 *
	 * @param number The number of the round
	 * @return The round
	 */
	public Round getRound(Integer number ) {
		try {
			roundsLock.lock();

			Round round = null;
			
			round = rounds.get(number);
			if (round == null) {
			log.log(Level.FINER, "Creating round {0} for Execution {1}",
						new Object[]{number, consensus.getId()});
				round = new Round(this, number, initialTimeout);
				rounds.put(number, round);
			} 
			return round;
		} finally {
			roundsLock.unlock();
		}

	}
	
	/**
	 * Returns all Rounds of this execution
	 * @return The currently active rounds;
	 */
	public Collection<Round> getRounds(){
		return rounds.values();
	}

//	/**
//	 * Removes rounds greater than 'limit' from this execution
//	 *
//	 * @param limit Rounds that should be kept (from 0 to 'limit')
//	 */
//	public void removeRoundsandCancelTO(int limit) {
//		try {
//			roundsLock.lock();
//
//			for (int i = 0; i<limit;i++) {
//				Round r = rounds.get(i);
//				r.setRemoved();
//				r.getTimeoutTask().cancel(true);
//			}
//
//		} finally {
//			roundsLock.unlock();
//		}
//	}

	/**
	 * The round at which a decision was possible to make
	 *
	 * @return Round at which a decision was possible to make
	 */
	public Round getDecisionRound() {
		try{
			roundsLock.lock();
			Round r = rounds.get(decisionRound);
			return r;
		} finally {
			roundsLock.unlock();
		}
	}

	/**
	 * The last round of this execution
	 *
	 * @return Last round of this execution
	 */
	public Round getLastRound() {
		try {
			roundsLock.lock();
			Round r = rounds.get(rounds.size() - 1);
			return r;
		} finally {
			roundsLock.unlock();
		}
	}

	/**
	 * The number of the currently processed round
	 */
	public Integer getCurrentRoundNumber() {
		return currentRound;
	}
	
	public Round getCurrentRound(){
		return getRound(currentRound);
	}


	/**
	 * Informs wether or not the execution is decided
	 *
	 * @return True if it is decided, false otherwise
	 */
	public boolean isDecided() {
		return decisionRound != -1;
	}
	
	/**
	 * Informs wether or not the execution is removed and invalid
	 *
	 * @return True if it is decided, false otherwise
	 */
	public boolean isRemoved() {
		return removed;
	}
	
	/**
	 * Is this execution already executed by the service
	 *
	 * @return True if it is decided, false otherwise
	 */
	public boolean isExecuted() {
		return executed;
	}
	
	/**
	 * Sets this execution to be executed
	 */
	public void  setExecuted() {
		executed = true;
	}
	/**
	 * Is this execution already started by a proposal
	 *
	 * @return True if it is decided, false otherwise
	 */
	public boolean isStarted() {
		return started;
	}
	
	/**
	 * Sets this execution to be executed
	 */
	public void  setStarted() {
		started = true;
	}
	
	/**
	 * Informs wether or not the execution is currently active. This can
	 * change back to true if f+1 freeze messages for the last round arrive.
	 * The current round is immediately increased by one if the round is frozen,
	 * but it does not become active right away. This happens if the round is
	 * globally frozen, i.e. when f+1 freezes are detected.
	 * <strong>Important</strong>The designated leader of the
	 * next round cannot freeze itself by timeout, because it would
	 * have to restart its propose if it would freeze its own leadership round.
	 * Even though, no deadlock can arise, because the client would then suspect
	 * the leader and the other replicas would find out this finally.
	 * @return True if this execution is decided and not frozen, false otherwise
	 */
	public boolean isActive() {
		try {
			roundsLock.lock();
			
			Round r = getRound(currentRound);
			if(r.getNumber() == Round.ROUND_ZERO){
				return  r.isProposed() && !r.isDecided(); //
			} else {
				return getRound(currentRound-1).countFreeze() > manager.quorumF 
						&& !r.isDecided();
			}
				
			// TODO check this for optimisations
//				
//			boolean ret;
//			if(rounds.size() == 1){
//				Round r = getRound(0);
//				ret = r.isProposed() && !isDecided() || r.isCollected();
//				if(log.isLoggable(Level.FINEST))
//					log.log(Level.FINEST, "{0} | {1} isactive: prop: {2}, decided: {3}, collected: {4}", 
//							new Object[]{getId(),r.getNumber(),r.isProposed(),r.isDecided(),r.isCollected()});
//				if(log.isLoggable(Level.FINE))
//					log.log(Level.FINE,"{0} | {1} isactive: {2}",
//							new Object[]{getId(),r.getNumber(),ret});
//				return ret;
//			} else {
//				Iterator<Round> it = rounds.values().iterator();
//				//This set is reversed, because round sort from high to low
//				SortedSet<Round> reverseset = new TreeSet<Round>(rounds.values());
//				boolean lastdecided=false;
//				int lastid = -1;
//				for(Round r : reverseset){
//					if(log.isLoggable(Level.FINEST))
//						log.log(Level.FINEST, "{0} | {1} isactive: prop: {2}, decided: {3}, collected: {4}", 
//								new Object[]{getId(),r.getNumber(),r.isProposed(),r.isDecided(),r.isCollected()});
//					if(r.isCollected()){
//						// If we find a collected round return status of the
//						//round after this one (which is located before this round in the reverse list)
//						ret = !(lastdecided && r.getNumber()+1 == lastid);
//						if(log.isLoggable(Level.FINE))
//							log.log(Level.FINE,"{0} | {1} isactive: {2}",
//									new Object[]{getId(),r.getNumber(),ret});
//						return ret; 						
//					} else {
//						//If we find a decided round first we are done
//						if (r.isDecided()){
//							ret = false;
//							if(log.isLoggable(Level.FINE))
//								log.log(Level.FINE,"{0} | {1} isactive: {2}",
//										new Object[]{getId(),r.getNumber(),ret});
//							return ret;
//						}
//					}
//					lastdecided = r.isDecided();
//					lastid = r.getNumber();
//					// Nothing must be done here, if we find a frozen round we are done
//				}
//			}
		} finally {
			roundsLock.unlock();
		}
		
	}

	/**
	 * Called by the Acceptor, to set the decided value. If the Propose for the 
	 * round was not yet received the execution is postponed until the decide
	 * arrives and this method is called again.
	 *
	 * @param value The decided value
	 * @param round The round at which a decision was made
	 */
	@SuppressWarnings({"unchecked"})
	public void decided(Round round/*
			 * , byte[] value
			 */) {
		// This is the first time we decide
		if (decisionRound == -1) {
			decisionRound = round.getNumber();
			if (round.getPropValue() != null) {
				consensus.decided(round.getPropValue(), decisionRound,round.getInitialProposer());
				manager.executionDecided(this);
			}
		} else {		
			// Multiple decisions where made
			//Check if we have stuff remaining or new messages to propose
//			manager.processOOCMessages(eid+1);
		}
		//check if we need to propose
		manager.getRequestHandler().notifyChangedConditions();
	}
        
	@Override
	public String toString() {
		return consensus.getId().toString();
	}

	public void freeze(Round round) {
		round.freeze();
		currentRound = currentRound + 1;
		Round next = getRound(currentRound);
		//Process pending msgs for next round
		for(PaxosMessage msg:next.pending){
			if(log.isLoggable(Level.FINE)){
				log.log(Level.FINE,"{0} | {1} Handling message: {2}",new Object[]{next.getExecution().eid,next.getNumber(),msg});
			}
			getManager().acceptor.processMessage(msg);
		}
	}

	/**
	 * Notifies this execution that the leader for round 0 has changed.
	 * This implies that this replica might have to send the collect message
	 * to the new leader again.
	 * 
	 * @param leaderId The newly selected leader.
	 */
	void notifyNewLeader(Integer leaderId) {
		Round r = getCurrentRound();
		// Safety check
		if(r.getNumber() == 0){
			// Send a collect message only when collected
			if(r.isCollected()){
				getManager().acceptor.sendCollect(r, leaderId);
			}
		} else {
			log.severe("Got notified about new leader, but the round "
					+ "is not zero, so this should not happen ");
		}
	}
	
	/**
	 * Cleans up this Execution when it is finished
	 */
	protected void cleanUp(){
		lock.lock();
		removed = true;
		roundsLock.lock();
		for(Round r:getRounds()){
			r.cleanUp();
		}
		rounds.clear();
		roundsLock.unlock();
		lock.unlock();
	}
}
