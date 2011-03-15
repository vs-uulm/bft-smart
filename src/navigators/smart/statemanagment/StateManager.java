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

package navigators.smart.statemanagment;

import java.util.HashSet;

/**
 * TODO: Não sei se esta classe sera usada. Para já, deixo ficar
 * 
 * @author Joao Sousa
 */
public class StateManager {

	public static final Long NOT_WAITING  = Long.valueOf(-1l);

    private StateLog log;
    private HashSet<SenderEid> senderEids = null;
    private HashSet<SenderState> senderStates = null;
    private int f;
    private int n;
    private int me;
    private Long lastEid;
    private Long waitingEid;
    private Integer replica;
    private byte[] state;

    @SuppressWarnings("boxing")
    public StateManager(int k, int f, int n, int me) {

        this.log = new StateLog(k);
        senderEids = new HashSet<SenderEid>();
        senderStates = new HashSet<SenderState>();
        this.f = f;
        this.n = n;
        this.me = me;
        this.replica = 0;
        this.state = null;
        this.lastEid = -1l;
        this.waitingEid = -1l;
    }

    public Integer getReplica() {
        return replica;
    }

    @SuppressWarnings("boxing")
    public void changeReplica() {
        do {
            replica = (replica + 1) % n;
        } while (replica == me);
    }

    public void setReplicaState(byte[] state) {
        this.state = state;
    }

    public byte[] getReplicaState() {
        return state;
    }
    
    public void addEID(Integer sender, Long eid) {
        senderEids.add(new SenderEid(sender, eid));
    }

    public void emptyEIDs() {
        senderEids.clear();
    }

    @SuppressWarnings("boxing")
	public void emptyEIDs(Integer eid) {
        for (SenderEid m : senderEids)
            if (m.eid <= eid) senderEids.remove(m);
    }

    public void addState(Integer sender, TransferableState newstate) {
        senderStates.add(new SenderState(sender, newstate));
    }

    public Long getAwaitedState() {
        return waitingEid;
    }


    public void setAwaitedState(Long wait) {
        this.waitingEid = wait;
    }
    public void setLastEID(Long eid) {
        lastEid = eid;
    }

    public Long getLastEID() {
        return lastEid;
    }

    public boolean moreThenF_EIDs(Long eid) {

        long count = 0;
        HashSet<Integer> replicasCounted = new HashSet<Integer>();

        for (SenderEid m : senderEids) {
            if (m.eid.equals(eid) && !replicasCounted.contains(m.sender)) {
                replicasCounted.add(m.sender);
                count++;
            }
        }
        
        return count > f;
    }
    public boolean moreThenF_Replies() {

        int count = 0;
        HashSet<Integer> replicasCounted = new HashSet<Integer>();

        for (SenderState m : senderStates) {
            if (!replicasCounted.contains(m.sender)) {
                replicasCounted.add(m.sender);
                count++;
            }
        }

        return count > f;
    }

    public TransferableState getValidState() {
        
        SenderState[] st = new SenderState[senderStates.size()];
        senderStates.toArray(st);
        int count = 0;

        for (int i = 0; i < st.length; i++) {

            for (int j = i; j < st.length; j++) {

                if (st[i].state.equals(st[j].state) && st[j].state.hasState) count++;
                if (count > f) return st[j].state;
            }
        }

        return null;
    }

    public int getReplies() {
        return senderStates.size();
    }

    public StateLog getLog() {
        return log;
    }

    private class SenderEid {

        private Integer sender;
        private Long eid;

        SenderEid(Integer sender, Long eid) {
            this.sender = sender;
            this.eid = eid;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof SenderEid) {
                SenderEid m = (SenderEid) obj;
                return (m.eid.equals(this.eid) && m.sender.equals(this.sender));
            }
            return false;
        }

        @Override
        public int hashCode() {
            int hash = 1;
            hash = hash * 31 + this.sender.intValue();
            hash = hash * 31 + this.eid.intValue();
            return hash;
        }
    }

    /**
     * This class iss a Holder for a transferred State object
     */
    private class SenderState {

        private Integer sender;
        private TransferableState state;

        SenderState(Integer sender, TransferableState state) {
            this.sender = sender;
            this.state = state;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof SenderState) {
                SenderState m = (SenderState) obj;
                return (this.state.equals(m.state) && m.sender == this.sender);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int hash = 1;
            hash = hash * 31 + this.sender.hashCode();
            hash = hash * 31 + this.state.hashCode();
            return hash;
        }
    }

	public boolean isWaitingForState() {
		return !waitingEid.equals(NOT_WAITING);
}

	public void resetWaiting() {
		waitingEid = NOT_WAITING;
		senderStates.clear();
        state = null;
		
	}
}
