/**
 * Copyright (c) 2007-2009 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and
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

    /**
     * Creates a new instance of LeaderModule
     */
    @SuppressWarnings("boxing")
    public LeaderModule() {
        addLeaderInfo(Long.valueOf(-1l), ROUND_ZERO, 0);
        addLeaderInfo(Long.valueOf(0), ROUND_ZERO, 0);
    }

    /**
     * Adds or updates information about a leader.
     *
     * @param exec Consensus where the replica is a leader
     * @param r Rounds of the consensus where the replica is a leader
     * @param l ID of the leader
     */
    public final void addLeaderInfo(Long exec, Integer r, Integer l) {
        List<ConsInfo> list = leaderInfos.get(exec);
        if (list == null) {
            list = new LinkedList<ConsInfo>();
            leaderInfos.put(exec, list);
        }
        ConsInfo ci = findInfo(list, r);

        if (ci != null) {
            ci.leaderId = l;
        } else {
            list.add(new ConsInfo(r, l));
        }
    }

    public void freezeRound(final Long exec, final Integer r, final Integer newLeader) {
        List<ConsInfo> list = leaderInfos.get(exec);
        ConsInfo ci = findInfo(list, r);
        Integer oldleader = null;

        if (ci != null) {
            oldleader = ci.leaderId;
            ci.leaderId = newLeader;
            log.log(Level.FINE,"{0} | {1} | round exists - NEW LEADER {2} ({3})", new Object[]{exec,r,newLeader,oldleader});
        } else {
            log.log(Level.FINE,"{0} | {1} | SET LEADER {2}", new Object[]{exec,r,newLeader});
            list.add(new ConsInfo(r, newLeader));
        }

        // Check later executions and set new leader
        long current_exec = exec + 1;

        while ((list = leaderInfos.get(current_exec++)) != null) {
			Integer nextLeader = newLeader;
            for (ConsInfo cit : list) {
                // Replace leaders of later executions if they have this current leader
//                if (oldleader == null || cit.leaderId.equals(oldleader)) {
//					oldleader = cit.leaderId;
                    cit.leaderId = nextLeader;
					nextLeader ++;
//                }
            }
        }
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
     * @param c ID of the consensus established as being decided
     * @param l ID of the replica established as being the leader for the round
     * 0 of the next consensus
     */
    public void decided(Long c, Integer l) {
        if (leaderInfos.get(c) == null) {
            addLeaderInfo(Long.valueOf(c.longValue() + 1), ROUND_ZERO, l);
        }
    }

    /**
     * Retrieves the replica ID of the leader for the specified consensus's
     * execution ID and round number
     *
     * @param exec consensus's execution ID
     * @param r Round number for the specified consensus
     * @return The replica ID of the leader
     */
    public Integer getLeader(Long exec, Integer r) {
        List<ConsInfo> list = leaderInfos.get(exec);
        if (list == null) {
            log.log(Level.FINE,"{0} | {1} | Creating CI List", new Object[]{exec,r});
            //there are no information for the execution c
            //let's see who were the leader of the last execution
            list = new LinkedList<ConsInfo>();
            leaderInfos.put(exec, list);

            List<ConsInfo> before = leaderInfos.get(Long.valueOf(exec.longValue() - 1));

            if (before != null && before.size() > 0) {
                //the leader for this round will be the leader of the last round of the last successful round
                ConsInfo ci = before.get(before.size() - 1);
                list.add(new ConsInfo(r, ci.leaderId));
                return ci.leaderId;
            }
        } else {
            ConsInfo info = findInfo(list, r);
            log.log(Level.FINEST,"{0} | {1} | INFOLIST existant {2}", 
                    new Object[]{exec, r, leaderInfos.entrySet()});
            if (info != null) {
                log.log(Level.FINE,"{0} | {1} | CI found - LEADER {2}", 
                        new Object[]{exec, r, info.leaderId});
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

    /**
     * Retrieves the replica ID of the leader for the specified consensus's
     * execution ID and last(current) round number
     *
     * @param c consensus's execution ID
     * @return The replica ID of the leader
     */
    public Integer getLeader(Execution exec) {
        return getLeader(exec.getId(), exec.getCurrentRoundNumber());
    }

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