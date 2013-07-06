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
package navigators.smart.paxosatwar.executionmanager;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static navigators.smart.paxosatwar.executionmanager.Round.ROUND_ZERO;
import navigators.smart.paxosatwar.roles.Acceptor;

/**
 * This class manages information about the leader of each round of each
 * consensus
 *
 * @author Eduardo Alchieri
 * @author Christian Spann
 */
public class LeaderModule {
    
    private static transient final Logger log = Logger.getLogger(LeaderModule.class.getCanonicalName());

    /*
	 * Each value of this map is a list of all the rounds of a consensus
     * Each element of that list is a tuple which stands for a round, and the id
     * of the process that was the leader for that round
	 */
    private SortedMap<Long, Deque<ConsInfo>> leaderInfos = 
			Collections.synchronizedSortedMap(new TreeMap<Long, Deque<ConsInfo>>());
	private transient ExecutionManager manager;
    private transient final Object sync = new Object();
	private final int n;
	private final int me;

    /**
     * Creates a new instance of LeaderModule
	 * @param n The number of replicas
     */
    @SuppressWarnings("boxing")
    public LeaderModule(int n, int me) {
		this.n = n;
		this.me = me;
        setLeaderInfo(Long.valueOf(-1l), ROUND_ZERO, 0);
        setLeaderInfo(Long.valueOf(0), ROUND_ZERO, 0);
    }
	
	/**
	 * Sets a reference to the executionmanager in order to find active executions
	 * that require a ping when the leader of round zero changed.
	 * 
	 * @param manager 
	 */
	public void setExecManager(ExecutionManager manager){
		this.manager = manager;
	}


    /**
     * Adds or updates information about a leader.
     *
     * @param exec Consensus where the replica is a leader
     * @param r Rounds of the consensus where the replica is a leader
     * @param leader ID of the leader
     */
    public final void setLeaderInfo(Long exec, Integer r, Integer leader) {
        Deque<ConsInfo> list = leaderInfos.get(exec);
        if (list == null) {
            list = new LinkedList<ConsInfo>();
            leaderInfos.put(exec, list);
        }
        ConsInfo ci = findInfo(list, r);

        if (ci == null) {
            log.log(Level.FINE,"{0} | {1} | adding NEW LEADER {2} ", new Object[]{exec,r,leader});
            list.add(new ConsInfo(r, leader));
        } else {
            log.log(Level.FINE,"{0} | {1} | round exists - NEW LEADER {2} ({3})", new Object[]{exec,r,leader,ci.leaderId});
            ci.leaderId = leader;
        }
    }

    /**
     * Sets and returns the leader for the round after the specified one regarding
     * to the desired rotation scheme for leaders. Currently the next one modulo
     * n is chosen.
     * @param exec The execution where the round is frozen
     * @param r The round that is frozen by more than f+1 replicas
     * @return The leader of the next round.
     * @throws A <code>RuntimeException</code> if we do not find a valid old round
     * number
     */
    public Integer collectRound(final Long exec, final Integer r) {
		Integer oldLeader = getLeader(exec, r);
		Integer newLeader = null;
		
		/*
		 * If we did not yet set the leader for this round, get it from the
		 * last active round of the previous execution. There must be
		 * at least one round started, otherwise the messages triggering
		 * this collect would not be delivered to this execution.
		 * We then set this leader for this round until we receive updated
		 * information. TODO check this
		 */
		if(oldLeader == null){
				throw new RuntimeException("We did not set a leader in the "
						+ "current round, exiting as this should not happen... "
						+ "at least I think that it shouldnt...");
			
//			oldLeader = leaderInfos.get(exec-1).getLast().leaderId;
//			if(oldLeader == null){
//				throw new RuntimeException("We did not find a leader in the "
//						+ "previous round, exiting as this should not happen... "
//						+ "at least I think that it shouldnt...");
//			}
//			checkAndSetLeader(exec, r, oldLeader);
		}
		

		//define the leader for the next round: (previous_leader + 1) % N
		newLeader = (oldLeader +1 ) % n;

		log.log(Level.FINEST,"{0} | {1} | new leader? {2} ({3})",
				new Object[]{exec, r,
					oldLeader,newLeader});

		Acceptor.msclog.log(Level.INFO, "{0} note: new leader: {1}, {2}-{3}",
				new Object[]{me, newLeader, exec, r});
		Acceptor.msclog.log(Level.INFO, "ps| -t #time| 0x{0}| New leader:{1}  {2}-{3}|",
				new Object[]{me, newLeader, exec, r});
		if (log.isLoggable(Level.FINER)) {
			log.finer( exec +  " | " + r + " | NEW LEADER for the next round is " + newLeader);
		}

		Deque<ConsInfo> list = leaderInfos.get(exec);
		ConsInfo ci = findInfo(list, r+1);

		if (ci != null) {
			oldLeader = ci.leaderId;
			ci.leaderId = newLeader;
			log.log(Level.SEVERE,"{0} | {1} | round exists - NEW LEADER {2} ({3})", new Object[]{exec,r,newLeader,oldLeader});
			
		} else {
			log.log(Level.FINE,"{0} | {1} | SET LEADER {2}", new Object[]{exec,r,newLeader});
			list.add(new ConsInfo(r+1, newLeader));
		}
		
//		long tmpexec = exec+1;
//		Integer tmpleader = newLeader;
//		
//		while((list = leaderInfos.get(tmpexec))!= null){
//			for(ConsInfo cinfo:list){
//				cinfo.leaderId = tmpleader%n;
////				if(cinfo.round == 0){
//					manager.getExecution(tmpexec).notifyNewLeader(cinfo.leaderId);
////				}
//				tmpleader ++;
//				log.warning("{0} | {1} Changing leaderinfo for a round > 1 - should not happen!");
//			}
//			tmpexec ++;
//			tmpleader --; // The leader stays the same as the last round for the next execution
//		}

		return newLeader;
    }

    /**
     * Retrieves the tuple for the specified round, given a list of tuples
     *
     * @param l List of tuples formed by a round number and the ID of the leader
     * @param r Number of the round tobe searched
     * @return The tuple for the specified round, or null if there is none
     */
    private ConsInfo findInfo(Deque<ConsInfo> l, Integer r) {
        for (ConsInfo consInfo : l) {
            if (consInfo.round.equals(r)) {
                return consInfo;
            }
        }
        return null;
    }

    /**
     * Invoked by the acceptor object when a value is decided It adds a new
     * tuple to the list, which corresponds to the next consensus
     *
     * @param consID ID of the consensus established as being decided
     * @param leader ID of the replica established as being the leader for the round
     * 0 of the next consensus
     */
    public void decided(Long execId, Integer round, Integer leader) {
		Long nextId = execId + 1;
		//Only update leader information of the next exec round 0 if none exists yet
		// TODO check this if we need to update this more than once?
//		Integer goodleader = getLeader(execId, round);
//        if (leaderInfos.get(nextId) == null && leaderInfos.get(execId) != null) {
		log.log(Level.INFO,"{0} | {1} Decided, next leader: {3}",new Object[]{execId,round,leader});
		setLeaderInfo(nextId, ROUND_ZERO, leader);
//		if(getLeader(nextId,1) != null){
//			throw new RuntimeException("Secound round should not have started");
//		}
//        }
    }

    /**
     * Checks if the given leader is the correct leader for this round.
	 * If there is another designated leader
	 * false is returned.
     *
     * @param exec consensus's execution ID
     * @param r Round number for the specified consensus
	 * @param leader The leader that is checked
     * @return true if its the correct leader, false otherwise
     */
   public boolean checkLeader(Long exec, Integer r, Integer leader) {
		Deque<ConsInfo> list = leaderInfos.get(exec);
		if (list != null) {
			ConsInfo info = findInfo(list, r);
			log.log(Level.FINEST, "{0} | {1} | INFOLIST existant {2}",
					new Object[]{exec, r, leaderInfos.entrySet()});
			if (info != null) {
				log.log(Level.FINE, "{0} | {1} | CI found - LEADER {2}",
						new Object[]{exec, r, info.leaderId});
				return info.leaderId.equals(leader);
			}
		} else {
			if ((list = leaderInfos.get(exec - 1)) != null) {
				ConsInfo info = list.getLast();
				return info.leaderId.equals(leader);
			}
		}
		//We did not decide on the previous execution yet, we have to wait... 
		return false;
	}
	
	/**
     * Checks if the given leader is the correct leader for this round.
	 * If there is another designated leader
	 * false is returned. Otherwise the leader is set as leader for this round.
     *
     * @param exec consensus's execution ID
     * @param r Round number for the specified consensus
	 * @param leader The leader that is checked
     * @return true if its the correct leader, false otherwise
     */
//    public boolean checkAndSetLeader(Long exec, Integer r, Integer leader) {
//       if(checkLeader(exec, r, leader)){
//		   setLeaderInfo(exec, r, leader);
//		   return true;
//	   }
//	   return false;
//    }
	
	
	/**
     * Retrieves the replica ID of the leader for the specified consensus's
     * execution ID and round number 0
     *
     * @param exec consensus's execution ID
     * @return The replica ID of the leader
     */
    public Integer getLeader(Long exec, Integer round) {
        Deque<ConsInfo> list = leaderInfos.get(exec);
        if (list != null) {
           
            ConsInfo info = findInfo(list, round);
            log.log(Level.FINEST,"{0} | {1} | getLeader: INFOLIST existant {2}", 
                    new Object[]{exec, round, leaderInfos.entrySet()});
            if (info != null) {
                log.log(Level.FINE,"{0} | {1} | CI found - LEADER {2}", 
                        new Object[]{exec, round, info.leaderId});
                return info.leaderId;
            }
        } else {
        	log.log(Level.FINEST,"{0} | {1} | getLeader: INFOLIST for exec {0} not found", 
                    new Object[]{exec, round});
        }
		return null;
    }

    /**
     * Retrieves the replica ID of the leader for the specified consensus's
     * execution ID and round number 0
     *
     * @param exec consensus's execution ID
     * @return The replica ID of the leader
     */
    public Integer getLeader(Long exec) {
        return getLeader(exec, ROUND_ZERO);
    }

//    /**
//     * Retrieves the replica ID of the leader for the specified consensus's
//     * execution ID and last(current) round number
//     *
//     * @param c consensus's execution ID
//     * @return The replica ID of the leader
//     */
//    public Integer getLeader(Execution exec) {
//        return getLeader(exec, exec.getCurrentRoundNumber());
//    }

    /**
     * Removes a consensus that is established as being stable
     *
     * @param c Execution ID of the consensus
     */
    public void removeStableConsenusInfo(Long c) {

        synchronized (sync) {

            Long next = Long.valueOf(c.longValue() + 1);

            Deque<ConsInfo> list = leaderInfos.get(next);

            try {
                if (list == null) {//nunca vai acontecer isso!!!
                    System.err.println("- Executing a code that wasn't supposed to be executed :-)");
                    System.err.println("- And we have some reports there is a bug here!");
                    log.log(Level.WARNING,"Removing {0} even though {1} does not yet exist",new Object[]{c, next});
                    list = new LinkedList<ConsInfo>();
                    leaderInfos.put(next, list);
                    Deque<ConsInfo> rm = leaderInfos.remove(c);
                    if (rm != null && rm.size() > 0) {
                        ConsInfo ci = rm.getLast();
                        list.add(new ConsInfo(ci.leaderId));
                    }
                } else {
                    leaderInfos.remove(c);
                }
            } catch (NullPointerException npe) {
                System.out.println("Nullpointer when removing " + c);
                System.out.println(npe);
            }

        }
    }

    /**
     * Removes all stable consensusinfos older than and including c 
	 * @param c The newest consensus to remove
	 */
    public void removeAllStableConsenusInfo(Long c) {
        Long next = Long.valueOf(c.longValue() + 1);

        synchronized (sync) {
            Deque<ConsInfo> list = leaderInfos.get(next);

            if (list == null) {//nunca vai acontecer isso!!!
                System.err.println("- Executing a code that wasn't supposed to be executed :-)");
                System.err.println("- And we have some reports there is a bug here!");
                list = new LinkedList<ConsInfo>();
                leaderInfos.put(next, list);
                Deque<ConsInfo> rm = leaderInfos.remove(c);
                if (rm != null && rm.size() > 0) {
                    ConsInfo ci = rm.getLast();
                    list.add(new ConsInfo(ci.leaderId));
                }
            } else {
                leaderInfos.headMap(next).clear(); //remove all older infos
            }

        }
    }

    public void removeMultipleStableConsenusInfos(Long cStart, Long cEnd) {
        Long next = Long.valueOf(cEnd.longValue() + 1);
        synchronized (sync) {

            Deque<ConsInfo> list = leaderInfos.get(next);

            if (list == null) {//nunca vai acontecer isso!!!
                //System.err.println("- Executing a code that wasn't supposed to be executed :-)");
                //System.err.println("- And we have some reports there is a bug here!");
                list = new LinkedList<ConsInfo>();
                leaderInfos.put(next, list);
                Deque<ConsInfo> rm = leaderInfos.get(cEnd);
                if (rm != null) {
                    ConsInfo ci = rm.getLast();
                    list.add(new ConsInfo(ci.leaderId));
                }
            }

            for (long c = cStart.longValue(); c <= cEnd.longValue(); c++) {
                leaderInfos.remove(Long.valueOf(c));
            }

        }
    }

    /**
     * *****************************************************
     */
    public byte[] getState() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);
        ObjectOutputStream oos;
        try {
            oos = new ObjectOutputStream(bos);
            oos.writeObject(leaderInfos);
            return bos.toByteArray();
        } catch (IOException e) {
            // cannot happen with bytearray outputstream
			System.out.println("Error! ");
			e.printStackTrace();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public void setState(byte[] state) throws ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(state);
        ObjectInputStream ois;
        try {
            ois = new ObjectInputStream(bais);
            leaderInfos = (SortedMap<Long, Deque<ConsInfo>>) ois.readObject();
        } catch (IOException e) {
            //cannot happen with bais
        }
    }
}