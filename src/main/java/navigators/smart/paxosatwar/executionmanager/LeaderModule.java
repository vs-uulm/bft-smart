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

import java.io.ByteArrayInputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static navigators.smart.paxosatwar.executionmanager.Round.ROUND_ZERO;
import navigators.smart.paxosatwar.roles.Acceptor;

/**
 * This class manages information about the leader of each round of each
 * consensus
 *
 * @author edualchieri
 */
public class LeaderModule {
    
    private static final Logger log = Logger.getLogger(LeaderModule.class.getCanonicalName());

    // Each value of this map is a list of all the rounds of a consensus
    // Each element of that list is a tuple which stands for a round, and the id
    // of the process that was the leader for that round
    private SortedMap<Long, List<ConsInfo>> leaderInfos = Collections.synchronizedSortedMap(new TreeMap<Long, List<ConsInfo>>());
    private final Object sync = new Object();
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
     * Adds or updates information about a leader.
     *
     * @param exec Consensus where the replica is a leader
     * @param r Rounds of the consensus where the replica is a leader
     * @param leader ID of the leader
     */
    public final void setLeaderInfo(Long exec, Integer r, Integer leader) {
        List<ConsInfo> list = leaderInfos.get(exec);
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

    public Integer collectRound(final Long exec, final Integer r) {
		Integer oldLeader = getLeader(exec, r);
		Integer newLeader = null;
		

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

		List<ConsInfo> list = leaderInfos.get(exec);
		ConsInfo ci = findInfo(list, r+1);

		if (ci != null) {
			oldLeader = ci.leaderId;
			ci.leaderId = newLeader;
			log.log(Level.SEVERE,"{0} | {1} | round exists - NEW LEADER {2} ({3})", new Object[]{exec,r,newLeader,oldLeader});
		} else {
			log.log(Level.FINE,"{0} | {1} | SET LEADER {2}", new Object[]{exec,r,newLeader});
			list.add(new ConsInfo(r+1, newLeader));
		}

		return newLeader;
    }

    /**
     * Retrieves the tuple for the specified round, given a list of tuples
     *
     * @param l List of tuples formed by a round number and the ID of the leader
     * @param r Number of the round tobe searched
     * @return The tuple for the specified round, or null if there is none
     */
    private ConsInfo findInfo(List<ConsInfo> l, Integer r) {
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
    public void decided(Long execId, Integer round) {
		Long nextId = execId + 1;
		//Only update leader information of the next exec round 0 if none exists yet
		// TODO check this if we need to update this more than once?
		Integer goodleader = getLeader(execId, round);
//        if (leaderInfos.get(nextId) == null && leaderInfos.get(execId) != null) {
            setLeaderInfo(nextId, ROUND_ZERO, goodleader);
			if(getLeader(nextId,1) != null){
				throw new RuntimeException("Secound round should not have started");
			}
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
        List<ConsInfo> list = leaderInfos.get(exec);
        if (list != null) {
            ConsInfo info = findInfo(list, r);
            log.log(Level.FINEST,"{0} | {1} | INFOLIST existant {2}", 
                    new Object[]{exec, r, leaderInfos.entrySet()});
            if (info != null) {
                log.log(Level.FINE,"{0} | {1} | CI found - LEADER {2}", 
                        new Object[]{exec, r, info.leaderId});
                return info.leaderId.equals(leader);
            }
        }
		//We did not decide on the previous execution yet, we have to wait... 
		return false;
    }
	
	
	/**
     * Retrieves the replica ID of the leader for the specified consensus's
     * execution ID and round number 0
     *
     * @param exec consensus's execution ID
     * @return The replica ID of the leader
     */
    public Integer getLeader(Long exec, Integer round) {
        List<ConsInfo> list = leaderInfos.get(exec);
        if (list != null) {
           
            ConsInfo info = findInfo(list, round);
            log.log(Level.FINEST,"{0} | {1} | getLeader: INFOLIST existant {2}", 
                    new Object[]{exec, round, leaderInfos.entrySet()});
            if (info != null) {
                log.log(Level.FINE,"{0} | {1} | CI found - LEADER {2}", 
                        new Object[]{exec, round, info.leaderId});
                return info.leaderId;
            }
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

            List<ConsInfo> list = leaderInfos.get(next);

            try {
                if (list == null) {//nunca vai acontecer isso!!!
                    System.err.println("- Executing a code that wasn't supposed to be executed :-)");
                    System.err.println("- And we have some reports there is a bug here!");
                    list = new LinkedList<ConsInfo>();
                    leaderInfos.put(next, list);
                    List<ConsInfo> rm = leaderInfos.remove(c);
                    if (rm != null && rm.size() > 0) {
                        ConsInfo ci = rm.get(rm.size() - 1);
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
     * Removes all stable consensusinfos older than c *
     */
    public void removeAllStableConsenusInfo(Long c) {
        Long next = Long.valueOf(c.longValue() + 1);

        synchronized (sync) {
            List<ConsInfo> list = leaderInfos.get(next);

            if (list == null) {//nunca vai acontecer isso!!!
                System.err.println("- Executing a code that wasn't supposed to be executed :-)");
                System.err.println("- And we have some reports there is a bug here!");
                list = new LinkedList<ConsInfo>();
                leaderInfos.put(next, list);
                List<ConsInfo> rm = leaderInfos.remove(c);
                if (rm != null && rm.size() > 0) {
                    ConsInfo ci = rm.get(rm.size() - 1);
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

            List<ConsInfo> list = leaderInfos.get(next);

            if (list == null) {//nunca vai acontecer isso!!!
                //System.err.println("- Executing a code that wasn't supposed to be executed :-)");
                //System.err.println("- And we have some reports there is a bug here!");
                list = new LinkedList<ConsInfo>();
                leaderInfos.put(next, list);
                List<ConsInfo> rm = leaderInfos.get(cEnd);
                if (rm != null) {
                    ConsInfo ci = rm.get(rm.size() - 1);
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
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public void setState(byte[] state) throws ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(state);
        ObjectInputStream ois;
        try {
            ois = new ObjectInputStream(bais);
            leaderInfos = (SortedMap<Long, List<ConsInfo>>) ois.readObject();
        } catch (IOException e) {
            //cannot happen with bais
        }
    }

    /**
     * This class represents a tuple formed by a round number and the replica ID
     * of that round's leader
     */
    private class ConsInfo {

        public Integer round;
        public Integer leaderId;

        public ConsInfo(Integer l) {
            this.round = ROUND_ZERO;
            this.leaderId = l;
        }

        public ConsInfo(Integer round, Integer l) {
            this.round = round;
            this.leaderId = l;
        }
        
        public String toString() {
            return "R "+round+" | L "+leaderId;
        }
    }
}
