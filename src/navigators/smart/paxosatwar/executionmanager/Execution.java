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
package navigators.smart.paxosatwar.executionmanager;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.ExemptionMechanism;
import navigators.smart.consensus.MeasuringConsensus;
import static navigators.smart.paxosatwar.executionmanager.Round.ROUND_ZERO;

/**
 * This class stands for an execution of a consensus
 */
public class Execution {

	public static final Logger log = Logger.getLogger(Execution.class.getCanonicalName());
	final ExecutionManager manager; // Execution manager for this execution
	private MeasuringConsensus consensus; // MeasuringConsensus instance to which this execution works for
	private List<Round> rounds = new LinkedList<Round>();
//    private HashMap<Integer,Round> rounds = new HashMap<Integer,Round>(2);
	private ReentrantLock roundsLock = new ReentrantLock(); // Lock for concurrency control
	private volatile boolean started = false; // Did we start this execution
    private volatile boolean executed = false; // Is the execution of this consensus decision finished.
	private long initialTimeout; // Initial timeout for rounds
	private Integer decisionRound = Integer.valueOf(-1); // round at which a desision was made
	private Integer currentRound = ROUND_ZERO; //Currently active round
	public ReentrantLock lock = new ReentrantLock(); //this execution lock (called by other classes)

	/**
	 * Creates a new instance of Execution for Acceptor Manager
	 *
	 * @param manager Execution manager for this execution
	 * @param consensus MeasuringConsensus instance to which this execution works for
	 * @param initialTimeout Initial timeout for rounds
	 */
	@SuppressWarnings("rawtypes")
	protected Execution(ExecutionManager manager, MeasuringConsensus consensus, long initialTimeout) {
		this.manager = manager;
		this.consensus = consensus;
		this.initialTimeout = initialTimeout;
		getRound(0);
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
	 * Gets a round associated with this execution and creates it if not yet existant
	 *
	 * @param number The number of the round
	 * @return The round
	 */
	public Round getRound(Integer number) {
		return getRound(number, true);
	}

	/**
	 * Gets a round associated with this execution
	 *
	 * @param number The number of the round
	 * @param create if the round is to be created if not existent
	 * @return The round
	 */
	public Round getRound(Integer number, boolean create) {
		
		try {
			roundsLock.lock();

			Round round = null;
			if (rounds.size() <= number) {
				if (create) {
					log.log(Level.FINER, "Creating round {0} for Execution {1}",
							new Object[]{number, consensus.getId()});
					round = new Round(this, number, initialTimeout);
					rounds.add(round);
				}
			} else {
				round = rounds.get(number);
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
	public List<Round> getRounds(){
		return rounds;
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

	/**
	 * The currently processed round
	 */
	public void nextRound() {
		currentRound = currentRound + 1;
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
	 *
	 * @return True if it is decided, false otherwise
	 */
	public boolean isActive() {
		return !executed || getLastRound().isActive();
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
				consensus.decided(round.getPropValue(), decisionRound);

				manager.getTOMLayer().decided(consensus);
			}
		} else {		
			// Multiple decisions where made
			//Check if we have stuff remaining or new messages to propose
			manager.executionFinished(consensus);
		}
	}
        
	@Override
	public String toString() {
		return consensus.getId().toString();
	}
}
