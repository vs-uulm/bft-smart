/**
 * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
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

package navigators.smart.paxosatwar.requesthandler.timer;

import java.security.SignedObject;
import java.util.Arrays;

import navigators.smart.tom.core.timer.messages.RTCollect;
import navigators.smart.tom.util.TOMConfiguration;


/**
 * This class stores information about a timeout related to a request
 * TODO: Julgo que esta classe tem/faz muita coisa que deveria estar no TOM layer
 * 
 */
public class RTInfo {

    private boolean[] timeout; // Replicas where this timeout also occured
    private TOMConfiguration conf; // TOM configuration
    private Integer reqId; // ID of the request associated with this timer
    private SignedObject[] collect; // Proofs
    private boolean collected = false; // Does the collect message was already sent?
    private boolean nlSent = false; // Does the new leader message was already sent?

    /**
     * Creates a new instance of RequestTimeoutInfo
     * @param conf TOM configuration
     * @param reqId ID of the request associated with this timer
     * @param timeoutNum Number of the timeout
     * @param tom TOM layer
     */
    public RTInfo(TOMConfiguration conf, Integer reqId) {
        this.conf = conf;
        this.reqId = reqId;
        timeout = new boolean[conf.getN()];
    }

    public Integer getRequestId() {
        return reqId;
    }

    public boolean isNewLeaderSent() {
        return nlSent;
    }

    public void setCollect(int a, SignedObject c) {
        if (collect == null) {
            collect = new SignedObject[conf.getN()];
        }
        collect[a] = c;
    }

    public SignedObject[] getCollect() {
        return collect;
    }

    public void setCollected() {
        collected = true;
    }

    public boolean isCollected() {
        return collected;
    }

    /**
     * Checks if this timeout occured in the specified replica
     * @param a Replica id
     * @return True if this timer occurred in the specified replica, false otherwise
     */
    public boolean isTimeout(int a) {
        return this.timeout[a];
    }

    public void setNewLeaderSent() {
        nlSent = true;
    }

    public void setTimeout(int a) {
        this.timeout[a] = true;
    }

    /**
     * Gets the new leader and consensus ID to be started after a TO-FREEZE
     * fase is finished
     *
     * @param othercollects Proofs from other replicas
     * @param f Maximum number of faulty replicas that can exist
     * @return The replica ID of the new leader and the id of the next consensus
     * to be executed
     */
    @SuppressWarnings("boxing")
	public NextLeaderAndConsensusInfo getStartLeader(RTCollect[] othercollects, int f) {

        NextLeaderAndConsensusInfo[] last = new NextLeaderAndConsensusInfo[2 * f + 1];

        int p = 0;
        for (int i = 0; i < othercollects.length && p < last.length; i++) {
            if (othercollects[i] != null) {
                last[p++] = new NextLeaderAndConsensusInfo(othercollects[i].getLastConsensus(),
                        othercollects[i].getNewLeader());
            }
        }
        Arrays.sort(last);

        for (int i = last.length - 1; i > -1; i--) {
            //count how many collects say that their replica last executed round
            //is at most the same as replica i
            int c = 0;
            for (int j = 0; j < last.length; j++) {
                if (last[i].cons <= last[j].cons) {
                    c++;
                }
            }

            //if there is at least one correct replica that says that it is still
            //waiting for the next consensus to finish
            if (c > f) {
                last[i].cons += 1;
                return last[i];
            }
        }

        last[0].cons += 1;
        return last[0];//return the oldest reported execution +1
    }

    /**
     * Retrieves the ammount of replicas where this timeout occured
     * @return The ammount of replicas where this timeout occured
     */
    public int countTimeouts() {
        int c = 0;
        for (int i = 0; i < timeout.length; i++) {
            if (timeout[i]) {
                c++;
            }
        }
        return c;
    }

    /**
     * Counts the ammount of collect proofs that are not null
     * 
     * @return The ammount of collect proofs that are not null
     */
    public int countCollect() {
        int c = 0;
        for (int i = 0; i < collect.length; i++) {
            if (collect[i] != null) {
                c++;
            }
        }
        return c;
    }

    public class NextLeaderAndConsensusInfo implements Comparable<NextLeaderAndConsensusInfo> {
        public Long cons;
        public Integer leader;

        public NextLeaderAndConsensusInfo(Long cons, Integer leader) {
            this.cons = cons;
            this.leader = leader;
        }

        public int compareTo(NextLeaderAndConsensusInfo o) {
            return (int) (cons.longValue() - o.cons.longValue());
        }

    }

}
