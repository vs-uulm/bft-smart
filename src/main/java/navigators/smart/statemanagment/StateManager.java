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
package navigators.smart.statemanagment;

import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles the transfer and creation of the application state in case of state
 * transfers beeing requested.
 * 
 * @author Joao Sousa, Christian Spann
 */
public class StateManager {

	private static final Logger log = Logger.getLogger(StateManager.class.getCanonicalName());
	public static final Long NOT_WAITING = Long.valueOf(-2l);
	private StateLog statelog;
	private SenderEids senderEids = null;
	private SenderStates senderStates = null;
	private int f;
	private int n;
	private int me;
	private Long lastEid;
	private Long waitingEid;
	private Integer replica;
	private TransferableState state;
	private ReentrantLock lockState = new ReentrantLock();
	private Condition statecondition = lockState.newCondition();
	private MessageDigest md;
	private AtomicInteger statetransfers = new AtomicInteger();

	@SuppressWarnings("boxing")
	public StateManager(int k, int f, int n, int me, MessageDigest md) {

		this.statelog = new StateLog(k);
		senderEids = new SenderEids();
		senderStates = new SenderStates();
		this.f = f;
		this.n = n;
		this.me = me;
		this.replica = 0;
		this.state = null;
		this.lastEid = -1l;
		this.waitingEid = NOT_WAITING;
		this.md = md;
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

	public byte[] getReplicaState() {
		return state.state;
	}

	/**
	 * Adds the given EID from the given sender to the statemanager and returns true if a statetransfer is needed.
	 *
	 * @param sender The sender of a message with the given eid
	 * @param eid The ahead of time eid we got
	 * @return true if statetransfer iss needed, false otherwhise
	 */
	public boolean addEIDAndCheckStateTransfer(Integer sender, Long eid) {
		int size = senderEids.add(sender, eid);
		if (lastEid < eid && size > f) {

			if (log.isLoggable(Level.FINE)) {
				log.fine(" I have now more than " + f + " messages for EID " + eid + " which are beyond EID " + lastEid + " - initialising statetransfer");
			}

			lastEid = eid;
			waitingEid = eid - 1;

			emptyEIDs(eid);

			if (log.isLoggable(Level.WARNING)) {
				log.warning("Requesting Statetransfer up to" + (eid - 1));
			}
			return true;
		} else {
			return false;
		}
	}

	@SuppressWarnings("boxing")
	public void emptyEIDs(Long eid) {
		senderEids.removeSmallerThan(eid);
	}

	/**
	 * Adds the given state from the given sender to the manager. If the state contains a full state from the correct replica it is also set
	 *
	 * @param sender
	 * @param newstate
	 */
	public int addState(Integer sender, TransferableState newstate) {
//		senderStates.add(new SenderState(sender, newstate));
		if (sender.equals(replica) && newstate.state != null) {
			if (log.isLoggable(Level.FINER)) {
				log.finer(" I received the state, from the replica that I was expecting");
			}
			state = newstate;
		}
		return senderStates.add(new SenderState(sender, newstate));
	}

	public Long getAwaitedState() {
		return waitingEid;
	}

	public TransferableState getValidState() {
		for (Set<SenderState> stateset : senderStates.values()) {
			if (stateset.size() > f) {
				TransferableState validstate = stateset.iterator().next().state;
				if (validstate.hasState) {
					return validstate;
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
	public void saveState(Long lastEid, Integer decisionRound, Integer leader, byte[] lmstate, byte[] recvstate) {

		lockState.lock();

		if (log.isLoggable(Level.FINER)) {
			log.finer(" Saving state of EID " + lastEid + ", round " + decisionRound + " and leader " + leader);
		}

		statelog.newCheckpoint(lastEid, decisionRound, leader, -1l, recvstate, md.digest(recvstate), lmstate);

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
		TransferableState tfstate = statelog.getTransferableState(eid, sendState);
		lockState.unlock();
		return tfstate;
	}

	/**
	 * Updates the StateLog with the given valid full state
	 *
	 * @param state A valid full state
	 */
	public void updateState(TransferableState state) {
		statetransfers.incrementAndGet();   //increment count log variable
		assert (state.state != null) : "State is null which is not correct";
		lockState.lock();
		statelog.update(state);
		lockState.unlock();
	}

	public void checkAndWaitForSTF() {
		lockState.lock();
		if (isWaitingForState()) {
			log.log(Level.FINER,"Starting to wait for stf to finish");
			statecondition.awaitUninterruptibly();
			log.log(Level.FINER,"Finished to wait for stf to finish");
		}
		lockState.unlock();
	}

	public TransferableState registerSMMessage(SMMessage msg) {
		TransferableState ret = null;
		if (msg.getEid().equals(waitingEid)) {

			if (log.isLoggable(Level.FINER)) {
				log.finer(" The reply is for the EID that I want!");
			}

			int replies = addState(msg.getSender(), msg.getState());

			if (replies > f) {

				if (log.isLoggable(Level.FINE)) {
					log.fine(" I have more than " + f + " equal replies!");
				}

				//check if the full state equals this f+1 replies
				int haveState = 0;
				if (state != null && state.hasState) {
					//this is also checked by addState
					assert (state.state != null) : "If we got a state from the proper replica it shouldn't be null";
					byte[] hash = null;

					//Synchronized by nothgin... is not accessed synchronously
					hash = md.digest(state.state);
					if (state != null) {
						if (Arrays.equals(hash, state.stateHash)) {
							haveState = 1;
						} else {
							haveState = -1;
						}
					}
				}

				if (state != null && haveState == 1) {
					if (log.isLoggable(Level.FINE)) {
						log.fine(" The state of those replies is good!");
					}
					updateState(state);
					// success -> return the state to be passed to the app
					ret = state;
				} else if (state == null && (n / 2) < getReplies()) {

					if (log.isLoggable(Level.FINE)) {
						log.fine(" I have more than " + (n / 2) + " messages that are no good!");
					}

					resetWaiting();

				} else if (haveState == -1) {

					if (log.isLoggable(Level.FINE)) {
						log.fine(" The replica from which I expected the state, sent one which doesn't match the hash of the others, or it never sent it at all");
					}

					changeReplica();
					//FIXME The statemanager will not wake up if no new client requests are sent here.
				}
			}
		}
		return ret;
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
	 * Retrieves and resets the number of state transfers since this StateManager was started or this method was last called.
	 *
	 * @return The number of state transfers
	 */
	public int getAndResetStateTransferCount() {
		return statetransfers.getAndSet(0);
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

	private class SenderStates {

		Map<String, Set<SenderState>> senderStates = new HashMap<String, Set<SenderState>>();
		private int size = 0;

		private int add(SenderState senderState) {
			Set<SenderState> states = senderStates.get(Arrays.toString(senderState.state.stateHash));
			if (states == null) {
				states = new HashSet<SenderState>(f + 1);
				senderStates.put(Arrays.toString(senderState.state.stateHash), states);
			}
			if (states.add(senderState)) {
				size++;
			}
			return states.size();
		}

		private int size() {
			return size;
		}

		private void clear() {
			size = 0;
			senderStates.clear();
		}

		private Collection<Set<SenderState>> values() {
			return senderStates.values();
		}
	}

	private class SenderEids {

		private TreeMap<Long, HashSet<Integer>> senderEids = new TreeMap<Long, HashSet<Integer>>();

		/**
		 * Adds the sender of the msg with the given eid to the accounted list and returns the size of the list
		 *
		 * @param sender
		 * @param eid
		 * @return
		 */
		private int add(Integer sender, Long eid) {
			HashSet<Integer> senders = senderEids.get(eid);
			if (senders == null) {
				senders = new HashSet<Integer>(f + 1);
				senderEids.put(eid, senders);
			}
			senders.add(sender);
			return senders.size();
		}

		private void removeSmallerThan(Long eid) {
			for (Iterator<Long> it = senderEids.keySet().iterator(); it.hasNext();) {
				if (it.next().longValue() <= eid.longValue()) {
					it.remove();
				} else {
					break; //stop search
				}
			}
		}
	}
}
