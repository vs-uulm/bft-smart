/*
 * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, 
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
 */
package navigators.smart.consensus;

import java.util.Arrays;

import navigators.smart.tom.util.Logger;

/**
 *
 * This class represents a Consensus Instance.
 *
 * @param <E> Type of the decided Object
 *
 * @author unkown
 * @author Christian Spann 
 */
public class Consensus<E> {

    private Long eid; // execution ID
    private Integer decisionRound;
    private byte[] decision = null; // decided value
    private Integer proposer = -1;
    private E deserializedDecision = null; // decided value (deserialized)
    private final Object sync = new Object();

    public Consensus(Long eid) {
        this.eid = eid;
    }

    /**
     * Indicates that this consensus was decided by a proposal from the given
     * proposer.
     * 
     * @param value The value that was decided
     * @param round The round of the decision
     * @param proposer The proposer that made the proposal in that round
     */
    public void decided(byte[] value, Integer round, Integer proposer) {
        synchronized (sync) {
            this.decision = value;
            this.decisionRound = round;
            this.proposer = proposer;
            sync.notifyAll();
        }
    }
    
    public Integer getProposer(){
    	return proposer;
    }

    public Integer getDecisionRound() {
        return decisionRound;
    }

    /**
     * Gets the serialized decided value
     * @return Decided Value
     */
    public byte[] getDecision() {
        synchronized (sync) {  
        	if (decision == null) {
                waitForPropose();
            }
            return decision;
        }
    }

    public void setDeserialisedDecision(E deserialised) {
    	synchronized(sync){
    		this.deserializedDecision = deserialised;
    	}
    }

    /**
     * Blocks until a decision has been reached. Returns null if the decision was not yet
     * deserialized, otherwise it returns the decision  
     * @return The deserialized decided value, null if the decision wasn't deserialized yet.
     */
    public E getDeserializedDecision() {
        synchronized (sync) {
            if (deserializedDecision == null && decision == null) {
                waitForPropose();
            }
        }
        return deserializedDecision;
    }

    /**
     * The Execution ID for this consensus
     * @return Execution ID for this consensus
     */
    public Long getId() {
        return eid;
    }

    private void waitForPropose() {
        synchronized (sync) {
            try {
                Logger.println("waiting for propose for " + eid);
                sync.wait();
            } catch (InterruptedException ex) {
                Logger.println(ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    @Override
    public String toString(){
        return "Consensus - EID: "+ eid +" Round: "+decisionRound;
    }

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@SuppressWarnings("boxing")
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(decision);
		result = prime * result + decisionRound;
		result = prime
				* result
				+ ((deserializedDecision == null) ? 0 : deserializedDecision
						.hashCode());
		result = prime * result + (int) (eid ^ (eid >>> 32));
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof Consensus<?>))
			return false;
		Consensus<?> other = (Consensus<?>) obj;
		if (!Arrays.equals(decision, other.decision))
			return false;
		if (!decisionRound.equals(other.decisionRound))
			return false;
		if (deserializedDecision == null) {
			if (other.deserializedDecision != null)
				return false;
		} else if (!deserializedDecision.equals(other.deserializedDecision))
			return false;
		if (!eid.equals(other.eid))
			return false;
		return true;
	}
}
