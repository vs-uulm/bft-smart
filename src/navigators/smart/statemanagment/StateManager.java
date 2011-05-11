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
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TODO: Não sei se esta classe sera usada. Para já, deixo ficar
 * 
 * @author Joao Sousa
 */
public class StateManager {

    private static final Logger log = Logger.getLogger(StateManager.class.getCanonicalName());
	public static final Long NOT_WAITING  = Long.valueOf(-1l);
    private StateLog statelog;
    private HashSet<SenderEid> senderEids = null;
    private HashSet<SenderState> senderStates = null;
    private int f;
    private int n;
    private int me;
    private Long lastEid;
    private Long waitingEid;
    private Integer replica;
    private byte[] state;
    private ReentrantLock lockState = new ReentrantLock();
    private Condition statecondition = lockState.newCondition();

    @SuppressWarnings("boxing")
    public StateManager(int k, int f, int n, int me) {

        this.statelog = new StateLog(k);
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
        lockState.lock();
        do {
            replica = (replica + 1) % n;
        } while (replica == me);
        resetWaiting();
        lockState.unlock();
    }

    public void setReplicaState(byte[] state) {
        this.state = state;
    }

    public byte[] getReplicaState() {
        return state;
    }
    
    /**
     * Adds the given EID from the given sender to the statemanager and returns
     * true if a statetransfer is needed.
     *
     * @param sender The sender of a message with the given eid
     * @param eid The ahead of time eid we got
     * @return true if statetransfer iss needed, false otherwhise
     */
    public boolean addEIDAndCheckStateTransfer(Integer sender, Long eid) {
        senderEids.add(new SenderEid(sender, eid));
        if (lastEid < eid && moreThenF_EIDs(eid)) {

            if (log.isLoggable(Level.FINE)) {
                log.fine(" I have now more than " + f + " messages for EID " + eid + " which are beyond EID " + lastEid + " - initialising statetransfer");
            }

            lastEid = eid;
            waitingEid = eid - 1;

            if (log.isLoggable(Level.WARNING)) {
                log.warning("Requesting Statetransfer up to" + (eid - 1));
            }
            return true;
        } else {
            return false;
        }
    }

    public void emptyEIDs() {
        senderEids.clear();
    }

    @SuppressWarnings("boxing")
	public void emptyEIDs(Integer eid) {
        for (SenderEid m : senderEids) {
            if (m.eid <= eid) {
                senderEids.remove(m);
            }
        }
    }

    /**
     * Adds the given state from the given sender to the manager. If the state
     * contains a full state from the correct replica it is also set
     * @param sender
     * @param newstate
     */
    public void addState(Integer sender, TransferableState newstate) {
        senderStates.add(new SenderState(sender, newstate));
        if (sender.equals(replica) && newstate.state != null) {
            if (log.isLoggable(Level.FINER)) {
                log.finer(" I received the state, from the replica that I was expecting");
            }
            state = newstate.state;
        }
    }

    public Long getAwaitedState() {
        return waitingEid;
    }

    private boolean moreThenF_EIDs(Long eid) {

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

                if (st[i].state.equals(st[j].state) && st[j].state.hasState) {
                    count++;
                }
                if (count > f) {
                    return st[j].state;
                }
            }
        }

        return null;
    }

    public int getReplies() {
        return senderStates.size();
    }

//    public StateLog getLog() {
//        return statelog;
//    }
    public void saveState(Long lastEid, Integer decisionRound, Integer leader, byte[] lmstate, byte[] recvstate, byte[] recvstatehash) {

        lockState.lock();

        if (log.isLoggable(Level.FINER)) {
            log.finer(" Saving state of EID " + lastEid + ", round " + decisionRound + " and leader " + leader);
        }

        statelog.newCheckpoint(lastEid, decisionRound, leader, -1l, recvstate, recvstatehash, lmstate);

        lockState.unlock();

        if (log.isLoggable(Level.FINER)) {
            log.finer(" Finished saving state of EID " + lastEid + ", round " + decisionRound + " and leader " + leader);
        }
    }

    public void saveBatch(byte[] batch, Long lastEid, Integer decisionRound, int leader) {
        lockState.lock();

        if (log.isLoggable(Level.FINER)) {
            log.finer(" Saving batch of EID " + lastEid + ", round " + decisionRound + " and leader " + leader);
        }
        statelog.addMessageBatch(batch, decisionRound, leader);
        statelog.setLastEid(lastEid);

        lockState.unlock();
        if (log.isLoggable(Level.FINER)) {
            log.finer(" Finished saving batch of EID " + lastEid + ", round " + decisionRound + " and leader " + leader);
        }
    }

    public TransferableState getTransferableState(Long eid, boolean sendState) {
        lockState.lock();
        TransferableState state = statelog.getTransferableState(eid, sendState);
        lockState.unlock();
        return state;
    }

    /**
     * Updates the StateLog with the given valid full state
     * @param state A valid full state
     */
    public void updateState(TransferableState state) {
        assert (state.state != null) : "State is null which is not correct";
        lockState.lock();

        statelog.update(state);

        resetWaiting();
        lockState.unlock();
    }

    public void checkAndWaitForSTF() {
        lockState.lock();
        if (isWaitingForState()) {
            statecondition.awaitUninterruptibly();
        }
        lockState.unlock();
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

    public boolean isWaitingForState() {
        lockState.lock();
        boolean ret = !waitingEid.equals(NOT_WAITING);
        lockState.unlock();
        return ret;
    }

    public void resetWaiting() {
        lockState.lock();

        waitingEid = NOT_WAITING;
        senderStates.clear();
        state = null;

        statecondition.signalAll();
        lockState.unlock();
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
}
