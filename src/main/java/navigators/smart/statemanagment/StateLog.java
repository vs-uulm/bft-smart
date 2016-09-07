/* * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, 
 * and the authors indicated in the @author tags 
 *  
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 *  
 * http://www.apache.org/licenses/LICENSE-2.0 
 *  
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License. 
 */package navigators.smart.statemanagment;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This classes serves as a log for the state associated with the last checkpoint, and the message
 * batches received since the same checkpoint until the present. The state associated with the last
 * checkpoint together with all the batches of messages received so far, comprises this replica
 * current state
 * 
 * @author Jo�o Sousa
 */
public class StateLog {
	
	private static final Logger log = Logger.getLogger(StateLog.class.getCanonicalName());

    private BatchInfo[] messageBatches; // batches received since the last checkpoint.
    private Long lastCheckpointEid; // Execution ID for the last checkpoint
    private Integer lastCheckpointRound; // Decision round for the last checkpoint
    private Integer lastCheckpointLeader; // Leader for the last checkpoint
    private byte[] state; // State associated with the last checkpoint
    private byte[] stateHash; // Hash of the state associated with the last checkpoint
    private int position; // next position in the array of batches to be written
    private Long lastEid; // Execution ID for the last messages batch delivered to the application
    private byte[] lmstate; //Ithe state of the leadermodule

    /**
     * Constructs a State log
     * @param k The chekpoint period
     */
    @SuppressWarnings("boxing")
    public StateLog(int k) {

        this.messageBatches = new BatchInfo[k - 1];
        this.lastCheckpointEid = -1l;
        this.lastCheckpointRound = -1;
        this.lastCheckpointLeader = -1;
        this.state = null;
        this.stateHash = null;
        this.position = 0;
        this.lastEid = -1l;
        this.lmstate = null;
    }
    
    /**
     * Sets the state associated with the last checkpoint, and updates the execution IDs associated with it
     * @param lastCPEid
     * @param lastCPRound
     * @param lastCPLeader
     * @param lastEid
     * @param state
     * @param stateHash
     * @param lmstate
     */
    @SuppressWarnings("hiding")
	public void newCheckpoint(Long lastCPEid, Integer lastCPRound, Integer lastCPLeader, Long lastEid, byte[] state, byte[] stateHash, byte[] lmstate) {

        for (int i = 0; i < this.messageBatches.length; i++)
            messageBatches[i] = null;

        position = 0;
        this.state = state;
        this.stateHash = stateHash;
        this.lastCheckpointEid = lastCPEid;
        this.lastCheckpointRound = lastCPRound;
        this.lastCheckpointLeader = lastCPLeader;
        this.lmstate = lmstate;
        this.lastEid = lastEid;
    }

    /**
     * Sets the execution ID for the last checkpoint
     * @param lastCheckpointEid Execution ID for the last checkpoint
     */
    public void setLastCheckpointEid(Long lastCheckpointEid) {

        this.lastCheckpointEid = lastCheckpointEid;
    }

    /**
     * Retrieves the execution ID for the last checkpoint
     * @return Execution ID for the last checkpoint, or -1 if none was obtained
     */
    public Long getLastCheckpointEid() {
        return lastCheckpointEid ;
    }

    /**
     * Sets the decision round for the last checkpoint
     * @param lastCheckpointEid Decision round for the last checkpoint
     */
    public void setLastCheckpointRound(Integer lastCheckpointRound) {
        this.lastCheckpointRound = lastCheckpointRound;
    }

    /**
     * Retrieves the decision round for the last checkpoint
     * @return Decision round for the last checkpoint, or -1 if none was obtained
     */
    public Integer getLastCheckpointRound() {
        return lastCheckpointRound ;
    }

    /**
     * Sets the leader for the last checkpoint
     * @param lastCheckpointEid Leader for the last checkpoint
     */
    public void setLastCheckpointLeader(Integer lastCheckpointLeader) {

        this.lastCheckpointLeader = lastCheckpointLeader;
    }

    /**
     * Retrieves the leader for the last checkpoint
     * @return Leader for the last checkpoint, or -1 if none was obtained
     */
    public Integer getLastCheckpointLeader() {

        return lastCheckpointLeader;
    }

    /**
     * Sets the execution ID for the last messages batch delivered to the application
     * @param lastEid the execution ID for the last messages batch delivered to the application
     */
    public void setLastEid(Long lastEid) {
        this.lastEid = lastEid;
    }

    /**
     * Retrieves the execution ID for the last messages batch delivered to the application
     * @return Execution ID for the last messages batch delivered to the application
     */
    public Long getLastEid() {
        return lastEid;
    }

    /**
     * Retrieves the state associated with the last checkpoint
     * @return State associated with the last checkpoint
     */
    public byte[] getState() {
        return state;
    }

    /**
     * Retrieves the hash of the state associated with the last checkpoint
     * @return Hash of the state associated with the last checkpoint
     */
    public byte[] getStateHash() {
        return stateHash;
    }

    /**
     * Adds a message batch to the log. This batches should be added to the log
     * in the same order in which they are delivered to the application. Only
     * the 'k' batches received after the last checkpoint are supposed to be kept
     * @param batch The batch of messages to be kept.
     * @return True if the batch was added to the log, false otherwise
     */
    public void addMessageBatch(byte[] batch, Integer round, int leader) {

        if (position < messageBatches.length) {

            messageBatches[position] = new BatchInfo(batch, round, leader);
            position++;
        }
    }

    /**
     * Returns a batch of messages, given its correspondent execution ID
     * @param eid Execution ID associated with the batch to be fetched
     * @return The batch of messages associated with the batch correspondent execution ID
     */
    @SuppressWarnings("boxing")
	public BatchInfo getMessageBatch(Integer eid) {
        if (eid > lastCheckpointEid && eid <= lastEid) {
            return messageBatches[(int)(eid - lastCheckpointEid - 1)];
        }
        else return null;
    }

    /**
     * Retrieves all the stored batches kept since the last checkpoint
     * @return All the stored batches kept since the last checkpoint
     */
    public BatchInfo[] getMessageBatches() {
        return messageBatches;
    }

    /**
     * Retrieves the total number of stored batches kept since the last checkpoint
     * @return The total number of stored batches kept since the last checkpoint
     */
    public int getNumBatches() {
        return position;
    }
    /**
     * Constructs a TransferableState using this log information
     * @param eid Execution ID correspondent to desired state
     * @return TransferableState Object containing this log information
     */
    @SuppressWarnings("boxing")
	public TransferableState getTransferableState(Long eid, boolean setState) {

        if (lastCheckpointEid > -1 ) {

            BatchInfo[] batches = null;

            if (eid <= lastEid && eid >= lastCheckpointEid) {
                int size = (int) (eid - lastCheckpointEid);

                if (size > 0) {
                    batches = new BatchInfo[size];

                    for (int i = 0; i < size; i++) {
                        batches[i] = messageBatches[i];
                    }
                }
            } else if (lastEid > -1) {

                batches = messageBatches;
            }
            return new TransferableState(lastCheckpointEid, lastCheckpointRound, lastCheckpointLeader, eid, (setState ? state : null), stateHash, lmstate, batches);

        } else {
            if (log.isLoggable(Level.FINE)) {
                log.fine("No State for " + eid + "found. Current State of Statelog: " + toString());
            }
            return null;
        }
    }

    /**
     * Updates this log, according to the information contained in the TransferableState object
     * @param transState TransferableState object containing the information which is used to updated this log
     */
    public void update(TransferableState transState) {

        position = 0;
        if (transState.messageBatches != null) {
            for (int i = 0; i < transState.messageBatches.length; i++, position = i) {
                this.messageBatches[i] = transState.messageBatches[i];
            }
        }

        this.lastCheckpointEid = transState.lastCheckpointEid;

        this.state = transState.state;

        this.stateHash = transState.stateHash;

        this.lastEid = transState.lastEid;
    }

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "StateLog [lastCheckpointEid=" + lastCheckpointEid + ", lastCheckpointLeader=" + lastCheckpointLeader
				+ ", lastCheckpointRound=" + lastCheckpointRound + ", lastEid=" + lastEid + ", messageBatchesLen="+
				messageBatches.length + "]";
	}

}
