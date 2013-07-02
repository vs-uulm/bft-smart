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
package navigators.smart.paxosatwar.executionmanager;

import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import navigators.smart.paxosatwar.messages.CollectProof;
import navigators.smart.paxosatwar.messages.PaxosMessage;
import navigators.smart.paxosatwar.messages.Propose;
import navigators.smart.paxosatwar.messages.VoteMessage;
import navigators.smart.paxosatwar.roles.Acceptor;

/**
 * This class stands for a round of an execution of a consensus
 */
public class Round implements Comparable<Round> {

	public static final Integer ROUND_ZERO = Integer.valueOf(0);
	private static final Logger log = Logger.getLogger(Round.class.getCanonicalName());
	private transient final Execution execution;				// Execution where the round belongs to
	public transient List<PaxosMessage> pending = new LinkedList<PaxosMessage>();
	final int replicas;
	private transient volatile ScheduledFuture<?> timeoutTask;	// Timeout ssociated with this round
	private final Integer number; // Round's number
	private boolean[] weakSetted;
	private boolean[] strongSetted;
	private boolean[] decideSetted;
	private List<ValueCount> weakcount;	// weakling accepted values from other processes
	private List<ValueCount> strongcount;	// weakling accepted values from other processes
	private List<ValueCount> decidedcount;	// weakling accepted values from other processes
//	private byte[][] weak;						// weakling accepted values from other processes
//	private byte[][] strong;					// strongly accepted values from other processes
//	private byte[][] decide;					// values decided by other processes
	private boolean decided = false;			// Is this round decided
//	private boolean proposed = false;			// Did we propose in this round
	private Collection<Integer> freeze = null;	// processes where this round was freezed
	private boolean frozen = false;				// is this round frozen?
	private boolean collected = false;			// indicates if a collect message for this round was already sent
	private long timeout;						// duration of the timeout
	private boolean isTimeout = false;			// Was a timeout sent for this round?
	private boolean firstfrozen = false;			// indicates if this round was frozen before decision from its execution
	
	private Propose p = null;
	private byte[] propValueHash = null;		// proposed value hash
	public CollectProof[] proofs;				 // proof from other processes
	// Stores proposes where the leader did not mach whilst reception
	public Set<Propose> storedProposes = new HashSet<Propose>();

	class ValueCount { 
		private final byte[] value; 
		private int count = 1; 
		
		public ValueCount(byte[] val) { 
			this.value = val;
		} 
		
		/**
		 * Check if the given value equals the stored one and increment the count
		 * if true;
		 * @param other The value to check for equality
		 * @return true if equal, false if not
		 */
		public boolean checkAndInc(byte[] other){
			if(Arrays.equals(value, other)){
				count++;
				return true;
			}
			return false;
		}
		
		public int getCount(){
			return count;
		}
		
		public boolean equals(byte[] other){
			return Arrays.equals(value, other);
		}
	} 
	
	/**
	 * Creates a new instance of Round for acceptors
	 *
	 * @param parent Execution to which this round belongs
	 * @param number Number of the round
	 * @param timeout Timeout duration for this round
	 */
	@SuppressWarnings("boxing")
	protected Round(Execution parent, Integer number, long timeout) {
		this.execution = parent;
		this.number = number;
		ExecutionManager manager = execution.getManager();

		replicas = manager.getAcceptors().length;
		
		weakSetted = new boolean[replicas];
		strongSetted = new boolean[replicas];
		decideSetted = new boolean[replicas];

		weakcount = new ArrayList<ValueCount>(replicas);
		strongcount = new ArrayList<ValueCount>(replicas);
		decidedcount = new ArrayList<ValueCount>(replicas);
		
		//define the timeout for this round
		this.timeout = (int) Math.pow(2, number) * timeout;
	}
		
//	/**
//	 * Sets this rounds status to started. This is done when the previous round
//	 * is freezed or for the initial round.
//	 */
//	public void start() {
//		isStarted = true;
//	}
//	
//	/**
//	 * Returns if this round is started already
//	 */
//	public boolean isStarted(){
//		return isStarted;
//	}
	
	

	/**
	 * Sets the value to be decided upon in this round. Also counts the already processed weaks if matching this proposed value
	 * @param leader The leader who proposed this value
	 * @param propValue The proposed value
	 * @param propValueHash The hash of the proposed value
	 */
	public void setPropose( Propose p, byte[] propValueHash) {
		this.p = p;
		this.propValueHash = propValueHash;
	}

	/**
	 *
	 * @return The proposed value if set, null otherwise
	 */
	public byte[] getPropValue() {
		return p.value;
	}

	/**
	 *
	 * @return The hash of the proposed value, null otherwise
	 */
	public byte[] getPropValueHash() {
		return propValueHash;
	}
	
	/**
	 * Returns the proposer of the accepted proposal
	 */
	public Integer getProposer() {
		return p.proposer;
	}

	/**
	 * Adds a collect proof from another replica
	 *
	 * @param acceptor replica which sent the proof
	 * @param proof proof received
	 */
	public void setCollectProof(int acceptor, CollectProof proof) {
		if (proofs == null) {
			proofs = new CollectProof[replicas];
			Arrays.fill(proofs, null);
		}

		proofs[acceptor] = proof;
	}

	/**
	 * Retrieves the duration for the timeout
	 *
	 * @return Duration for the timeout
	 */
	public long getTimeout() {
		return this.timeout;
	}

	/**
	 * Retrieves this round's number
	 *
	 * @return This round's number
	 */
	public Integer getNumber() {
		return number;
	}

	/**
	 * Retrieves this round's execution
	 *
	 * @return This round's execution
	 */
	public Execution getExecution() {
		return execution;
	}

	/**
     * Schedules a timeout for a given round. It is called by an Execution when
     * a new round is confirmably created. This means when a propose arrives,
     * when f+1 weaks or strongs arrive or a round is frozen by 2f+1 freeze
     * messages.
     *
     */
    public void scheduleTimeout() {
        if (timeoutTask == null && !isFrozen() ) {
            if (log.isLoggable(Level.FINER)) {
                log.finer( execution + " | " 
                        + number + " | SCHEDULE TO " 
                        + timeout+ " ms");
            }
			Acceptor acc = execution.getManager().acceptor;
            TimeoutTask task = new TimeoutTask(acc, this);
            timeoutTask = execution.manager.timer.schedule(task, timeout, TimeUnit.MILLISECONDS);
        }
        //purge timer every 100 timeouts
        if (execution.getId().longValue() % 100 == 0) {
            execution.manager.timer.purge();
        }
    }
	
	/**
	 * Cancels the currently running timeout if one exists
	 */
	public void cancelTimeout() {
		if (timeoutTask != null) {
			timeoutTask.cancel(false);
		}
	}		

	/**
	 * Informs if there is a weakly accepted value from a replica
	 *
	 * @param acceptor The replica ID
	 * @return True if there is a weakly accepted value from a replica, false otherwise
	 */
	public boolean isWeakSetted(int acceptor) {
		return weakSetted[acceptor];
	}

	/**
	 * Informs if there is a strongly accepted value from a replica
	 *
	 * @param acceptor The replica ID
	 * @return True if there is a strongly accepted value from a replica, false otherwise
	 */
	public boolean isStrongSetted(int acceptor) {
		return strongSetted[acceptor];
	}

	/**
	 * Informs if there is a decided value from a replica
	 *
	 * @param acceptor The replica ID
	 * @return True if there is a decided value from a replica, false otherwise
	 */
	public boolean isDecideSetted( int acceptor) {
		return decideSetted[acceptor];
	}

	/**
	 * Retrives the weakly accepted value from the specified replica
	 *
	 * @param acceptor The replica ID
	 * @return The value weakly accepted from the specified replica
	 */
//	public byte[] getWeak(int acceptor) {
//		return this.weak[acceptor];
//	}

	/**
	 * Retrives all weakly accepted value from all replicas
	 *
	 * @return The values weakly accepted from all replicas
	 */
//	public byte[][] getWeak() {
//		return this.weak;
//	}

	/**
	 * Sets the weakly accepted value from the specified replica
	 *
	 * @param weak The weak message that we got
	 * @return The number of weaks that we have for this value;
	 */
	public int setWeak(VoteMessage weak) {
		//it can only be setted once and only if it fits the proposed value if yet received
		if (!weakSetted[weak.getSender()]) {
//			weak[acceptor] = value;
			weakSetted[weak.getSender()] = true;
			return countValue( weakcount, weak.value);
//			// Increase count only when we have received a valid propose for this round
//			if (propValueHash != null) {
//				weaks++;
//			}
		} else {
			log.log(Level.WARNING, "{2} | {3} | {0}: Second weak for this round received: {1}, "
					+ "not counting it again.", new Object[]{weak.getSender(),
						Arrays.toString(weak.value), execution, number});
			return getCount(weakcount, weak.value);
		}
	}

//	/**
//	 * Retrives the strongly accepted value from the specified replica
//	 *
//	 * @param acceptor The replica ID
//	 * @return The value strongly accepted from the specified replica
//	 */
//	public byte[] getStrong(int acceptor) {
//		return strong[acceptor];
//	}

//	/**
//	 * Retrives all strongly accepted values from all replicas
//	 *
//	 * @return The values strongly accepted from all replicas
//	 */
//	public byte[][] getStrong() {
//		return strong;
//	}

	/**
	 * Sets the strongly accepted value from the specified replica
	 *
	 * @param strong The strong message to record
	 * @return The number of strongs that we have for this value;
	 */
	public int setStrong(VoteMessage strong) {
		//it can only be setted once and only if it fits the proposed value if yet received, otherwise count later
		if (!strongSetted[strong.getSender()]) {
//			strong[strong.getSender()] = value;
			strongSetted[strong.getSender()] = true;
			return countValue(strongcount, strong.value);
		} else {
			log.log(Level.WARNING, "{2} | {3} | {0}: Second strong for this round received: {1}, "
					+ "not counting it again.", new Object[]{strong.getSender(),
						Arrays.toString(strong.value), execution, number});
			return getCount(strongcount, strong.value);
		}
	}

//	/**
//	 * Retrieves the decided value by the specified replica
//	 *
//	 * @param acceptor The replica ID
//	 * @return The value decided by the specified replica
//	 */
//	public byte[] getDecide(int acceptor) {
//		return decide[acceptor];
//	}
//
//	/**
//	 * Retrives all the decided values by all replicas
//	 *
//	 * @return The values decided by all replicas
//	 */
//	public byte[][] getDecide() {
//		return decide;
//	}

	/**
	 * Sets the decided value by the specified replica
	 *
	 * @param msg The decide message that we got
	 */
	public void setDecide(VoteMessage msg) {
		if (Arrays.equals(msg.value, propValueHash) && !isFrozen()) {
//			decide[acceptor] = value;
			countValue(decidedcount, msg.value);
		}
	}

	/**
	 * Indicates if a collect message for this round was already sent
	 *
	 * @return True if done so, false otherwise
	 */
	public boolean isCollected() {
		return collected;
	}

	/**
	 * Establishes that a collect message for this round was already sent
	 */
	public void collect() {
		cancelTimeout();
		collected = true;
	}

	/**
	 * Indicates if this round is frozen
	 *
	 * @return True if so, false otherwise
	 */
	public boolean isFrozen() {
		return frozen;
	}

	/**
	 * Establishes that this round is frozen
	 */
	public void freeze() {
		if(!frozen){
			frozen = true;
			if(!decided){
				firstfrozen = true;
			}
		}
	}

	/**
	 * Establishes that a replica locally freezed this round
	 *
	 * @param acceptor replica that locally freezed this round
	 */
	public void addFreeze(Integer acceptor) {
		if (freeze == null) {
			freeze = new TreeSet<Integer>();
		}
		freeze.add(acceptor);
	}

	/**
	 * Retrieves the ammount of replicas that locally freezed this round
	 *
	 * @return Ammount of replicas that locally freezed this round
	 */
	public int countFreeze() {
            return freeze.size();
	}

	public int countWeak(Integer leader, byte[] value) {
		return getCount(weakcount, value);
	}

	public int countStrong(Integer leader, byte[] value) {
		return getCount(strongcount, value);
	}

	public int countDecide(Integer leader, byte[] value) {
		return getCount(decidedcount, value);
	}

	/**
	 * Counts how many times 'value' occurs in 'array'
	 *
	 * @param array Array where to count
	 * @param value Value to count
	 * @param leader The proposer of the specified value
	 * @return Ammount of times that 'value' was find in 'array'
	 */
	private int getCount(List<ValueCount> l, byte[] value) {
		for(ValueCount c: l){
			if(c.equals(value)){
				return c.getCount();
			}
		}
		return 0;
	}
	
	private int countValue ( List<ValueCount> counts, byte[] value){
		for(ValueCount c:counts){
			if(c.checkAndInc(value)){
				// We found one and incremented it
				return c.getCount();
			}
		}
		// we found none, add new one
		counts.add(new ValueCount(value));
		return 1;
	}

	/**
	 * Indicates wheter a timeout was already sent for this round
	 *
	 * @return True if a timeout was sent False otherwise
	 */
	public boolean isTimeout() {
		return isTimeout;
	}

	/**
	 * Sets a flag that a timeout was sent for this round
	 */
	public void setTimeout() {
		isTimeout = true;
	}

	/**
	 * ************************* DEBUG METHODS ******************************
	 */
	/**
	 * Print round information.
	 */
	@Override
	public String toString() {
//		StringBuffer buffWeak = new StringBuffer(1024);
//		StringBuffer buffStrong = new StringBuffer(1024);
//		StringBuffer buffDecide = new StringBuffer(1024);
//
//		buffWeak.append("W=(");
//		buffStrong.append("S=(");
//		buffDecide.append("D=(");
//
//		//recall that weak.length = strong.length = decide.length
//
//		for (int i = 0; i < weak.length - 1; i++) {
//			buffWeak.append(str(weak[i]) + ",");
//			buffStrong.append(str(strong[i]) + ",");
//			buffDecide.append(str(decide[i]) + ",");
//		}
//
//		buffWeak.append(str(weak[weak.length - 1]) + ")");
//		buffStrong.append(str(strong[strong.length - 1]) + ")");
//		buffDecide.append(str(decide[decide.length - 1]) + ")");

		return "eid=" + execution.getId() + " r=" + getNumber() ;//+ " " + buffWeak + " " + buffStrong + " " + buffDecide;
	}

	private String str(byte[] obj) {
		if (obj == null) {
			return "*";
		} else {
			sun.misc.BASE64Encoder b64 = new sun.misc.BASE64Encoder();
			return b64.encode(obj);
		}
	}

	/*
	 * (non-Javadoc) @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((execution == null) ? 0 : execution.hashCode());
		result = prime * result + number.hashCode();
		return result;
	}

	/*
	 * (non-Javadoc) @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof Round)) {
			return false;
		}
		Round other = (Round) obj;
		if (execution == null) {
			if (other.execution != null) {
				return false;
			}
		} else if (!execution.equals(other.execution)) {
			return false;
		}
		if (!number.equals(other.number)) {
			return false;
		}
		return true;
	}

	public boolean isDecided() {
		return decided;
	}

	public void decided() {
		cancelTimeout();
		decided = true;
		execution.decided(this);
	}

//	/**
//	 * Indicates if this round is still active and further processing will happen
//	 * befor the corresponding execution will be done.
//	 * 
//	 * @return true if frozen and not removed or collected,
//	 * or proposed not decided, false otherwise
//	 */
//	public boolean isActive() {
//		return (proposed && !decided) || frozen && (!removed || collected);
//	}
	
//	/**
//	 * Sets this round to removed. This is called when the next
//	 * execution gets decided.
//	 */
//	public void setRemoved(){
//		firstfrozen = true;
//	}
	
	/** 
	 * Indicates if this round is inactive even though it is frozen
	 * and no later round started. This happens when a single replica
	 * gets frozen.
	 */
	public boolean firstFrozen(){
		return firstfrozen;
	}
	

	/**
	 * Indicates if a proposal for this round was already sent.
	 * 
	 * @return true if a proposal was sent false otherwise
	 */
	public boolean isProposed() {
		return p != null;
	}
	
	/**
	 * Creates an inverse ordering on the lists, sorting them from highest to 
	 * lowest
	 * @param o The round to be compared to
	 * @return The negative output of number.compareTo(o.number)
	 */
	public int compareTo(Round o) {
		return -number.compareTo(o.number);
	}
}
