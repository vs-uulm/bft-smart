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
import navigators.smart.paxosatwar.messages.Propose;
import navigators.smart.paxosatwar.roles.Acceptor;

/**
 * This class stands for a round of an execution of a consensus
 */
public class Round implements Comparable<Round> {

	public static final Integer ROUND_ZERO = Integer.valueOf(0);
	private static final Logger log = Logger.getLogger(Round.class.getCanonicalName());
	private transient final Execution execution;				// Execution where the round belongs to
	private transient volatile ScheduledFuture<?> timeoutTask;	// Timeout ssociated with this round
	private Integer number; // Round's number
	private boolean[] weakSetted;
	private boolean[] strongSetted;
	//Counters for weaks, strongs and decides
	private int weaks = 0;
	private int strongs = 0;
	private int decides = 0;
	private byte[][] weak;						// weakling accepted values from other processes
	private byte[][] strong;					// strongly accepted values from other processes
	private byte[][] decide;					// values decided by other processes
	private boolean decided = false;			// Is this round decided
	private boolean proposed = false;			// Did we propose in this round
	private Collection<Integer> freeze = null;	// processes where this round was freezed
	private boolean frozen = false;				// is this round frozen?
	private boolean collected = false;			// indicates if a collect message for this round was already sent
	private long timeout;						// duration of the timeout
	private boolean isTimeout = false;			// Was a timeout sent for this round?
//	private boolean alreadyRemoved = false;		// indicates if this round was removed from its execution
	private byte[] propValue = null;			// proposed value
	private byte[] propValueHash = null;		// proposed value hash
	public CollectProof[] proofs;				 // proof from other processes
	// Stores proposes where the leader did not mach whilst reception
	public Set<Propose> storedProposes = new HashSet<Propose>(); 

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

		Integer[] acceptors = manager.getAcceptors();
		int n = acceptors.length;

		weakSetted = new boolean[n];
		strongSetted = new boolean[n];

		Arrays.fill(weakSetted, false);
		Arrays.fill(strongSetted, false);

//        if (number.intValue() == 0) {
		this.weak = new byte[n][];
		this.strong = new byte[n][];
		this.decide = new byte[n][];

		Arrays.fill(weak, null);
		Arrays.fill(strong, null);
		Arrays.fill(decide, null);
//        } else {
//            Round previousRound = execution.getRound(number - 1);
//
//            this.weak = previousRound.getWeak();
//            this.strong = previousRound.getStrong();
//            this.decide = previousRound.getDecide();
//        }

		//define the timeout for this round
		this.timeout = (int) Math.pow(2, number) * timeout;
	}

	/**
	 * Sets the value to be decided upon in this round. Also counts the already processed weaks if matching this proposed value
	 *
	 * @param propValue The proposed value
	 * @param propValueHash The hash of the proposed value
	 */
	public void setpropValue(byte[] propValue, byte[] propValueHash) {
		this.propValue = propValue;
		this.propValueHash = propValueHash;
		weaks = count(weakSetted, weak, propValueHash);
		strongs = count(strongSetted, strong, propValueHash);
	}

	/**
	 *
	 * @return The proposed value if set, null otherwise
	 */
	public byte[] getPropValue() {
		return propValue;
	}

	/**
	 *
	 * @return The hash of the proposed value, null otherwise
	 */
	public byte[] getPropValueHash() {
		return propValueHash;
	}

//	/**
//	 * Set this round as removed from its execution
//	 */
//	public void setRemoved() {
//		this.alreadyRemoved = true;
//	}

//	/**
//	 * Informs if this round was removed from its execution
//	 *
//	 * @return True if it is removed, false otherwise
//	 */
//	public boolean isRemoved() {
//		return this.alreadyRemoved;
//	}

	/**
	 * Adds a collect proof from another replica
	 *
	 * @param acceptor replica which sent the proof
	 * @param proof proof received
	 */
	public void setCollectProof(int acceptor, CollectProof proof) {
		if (proofs == null) {
			proofs = new CollectProof[weak.length];
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
        if (timeoutTask == null && !isFrozen()) {
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
		return weak[acceptor] != null;
	}

	/**
	 * Informs if there is a strongly accepted value from a replica
	 *
	 * @param acceptor The replica ID
	 * @return True if there is a strongly accepted value from a replica, false otherwise
	 */
	public boolean isStrongSetted(int acceptor) {
		return strong[acceptor] != null;
	}

	/**
	 * Informs if there is a decided value from a replica
	 *
	 * @param acceptor The replica ID
	 * @return True if there is a decided value from a replica, false otherwise
	 */
	public boolean isDecideSetted(int acceptor) {
		return decide[acceptor] != null;
	}

	/**
	 * Retrives the weakly accepted value from the specified replica
	 *
	 * @param acceptor The replica ID
	 * @return The value weakly accepted from the specified replica
	 */
	public byte[] getWeak(int acceptor) {
		return this.weak[acceptor];
	}

	/**
	 * Retrives all weakly accepted value from all replicas
	 *
	 * @return The values weakly accepted from all replicas
	 */
	public byte[][] getWeak() {
		return this.weak;
	}

	/**
	 * Sets the weakly accepted value from the specified replica
	 *
	 * @param acceptor The replica ID
	 * @param value The value weakly accepted from the specified replica
	 */
	public void setWeak(int acceptor, byte[] value) {
		//it can only be setted once and only if it fits the proposed value if yet received
		if (!weakSetted[acceptor] && (propValueHash == null || Arrays.equals(value, propValueHash))) {
			weak[acceptor] = value;
			weakSetted[acceptor] = true;
			// Increase count only when we have received a valid propose for this round
			if (propValueHash != null) {
				weaks++;
			}
		} else {
			if(!Arrays.equals(value, propValueHash)){
				log.log(Level.WARNING, "{2} | {3} | {0}: Weak with different hash received: {1}, "
						+ "not counting this for this round.", new Object[]{acceptor,
							Arrays.toString(value), execution, number});
			}
		}
	}

	/**
	 * Retrives the strongly accepted value from the specified replica
	 *
	 * @param acceptor The replica ID
	 * @return The value strongly accepted from the specified replica
	 */
	public byte[] getStrong(int acceptor) {
		return strong[acceptor];
	}

	/**
	 * Retrives all strongly accepted values from all replicas
	 *
	 * @return The values strongly accepted from all replicas
	 */
	public byte[][] getStrong() {
		return strong;
	}

	/**
	 * Sets the strongly accepted value from the specified replica
	 *
	 * @param acceptor The replica ID
	 * @param value The value strongly accepted from the specified replica
	 */
	public void setStrong(int acceptor, byte[] value) {
		//it can only be setted once and only if it fits the proposed value if yet received, otherwise count later
		if (!strongSetted[acceptor] && (propValueHash == null || Arrays.equals(value, propValueHash))) {
			strong[acceptor] = value;
			strongSetted[acceptor] = true;
			if (propValueHash != null) {
				strongs++;
			}
		}
	}

	/**
	 * Retrieves the decided value by the specified replica
	 *
	 * @param acceptor The replica ID
	 * @return The value decided by the specified replica
	 */
	public byte[] getDecide(int acceptor) {
		return decide[acceptor];
	}

	/**
	 * Retrives all the decided values by all replicas
	 *
	 * @return The values decided by all replicas
	 */
	public byte[][] getDecide() {
		return decide;
	}

	/**
	 * Sets the decided value by the specified replica
	 *
	 * @param acceptor The replica ID
	 * @param value The value decided by the specified replica
	 */
	public void setDecide(int acceptor, byte[] value) {
		if (Arrays.equals(value, propValueHash) && !isFrozen()) {
			decide[acceptor] = value;
			decides++;
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
		frozen = true;
//        addFreeze(me);
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

	public int countWeak() {
		return weaks;
	}

	public int countStrong() {
		return strongs;
	}

	public int countDecide() {
		return decides;
	}

	/**
	 * Counts how many times 'value' occurs in 'array'
	 *
	 * @param array Array where to count
	 * @param value Value to count
	 * @return Ammount of times that 'value' was find in 'array'
	 */
	private int count(boolean[] arraySetted, byte[][] array, byte[] value) {
		int counter = 0;
		for (int i = 0; i < array.length; i++) {
			if (arraySetted != null && arraySetted[i] && Arrays.equals(value, array[i])) {
				counter++;
			}
		}
		return counter;
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
		StringBuffer buffWeak = new StringBuffer(1024);
		StringBuffer buffStrong = new StringBuffer(1024);
		StringBuffer buffDecide = new StringBuffer(1024);

		buffWeak.append("W=(");
		buffStrong.append("S=(");
		buffDecide.append("D=(");

		//recall that weak.length = strong.length = decide.length

		for (int i = 0; i < weak.length - 1; i++) {
			buffWeak.append(str(weak[i]) + ",");
			buffStrong.append(str(strong[i]) + ",");
			buffDecide.append(str(decide[i]) + ",");
		}

		buffWeak.append(str(weak[weak.length - 1]) + ")");
		buffStrong.append(str(strong[strong.length - 1]) + ")");
		buffDecide.append(str(decide[decide.length - 1]) + ")");

		return "eid=" + execution.getId() + " r=" + getNumber() + " " + buffWeak + " " + buffStrong + " " + buffDecide;
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
	}

//	/**
//	 * Indicates if this round is still active and further processing will happen
//	 * befor the corresponding execution will be done.
//	 * 
//	 * @return true if frozen or not decided, false otherwise
//	 */
//	public boolean isActive() {
//		return (proposed && !decided) || collected;
//	}

	/**
	 * Indicates if a proposal for this round was already sent.
	 * 
	 * @return true if a proposal was sent false otherwise
	 */
	public boolean isProposed() {
		return proposed;
	}
	
	/**
	 * Sets the proposed flag for this round
	 * 
	 * @return true if a proposal was sent false otherwise
	 */
	public void setProposed() {
		proposed = true;
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
