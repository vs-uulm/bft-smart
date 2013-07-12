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
package navigators.smart.paxosatwar.messages;

import java.nio.ByteBuffer;

/**
 * @author Christian Spann
 *
 */
public class Propose extends VoteMessage {
	
	/**
	 * Proof for a already started round...l
	 */
	private Proof proof; 
	
	/** The leader that initially proposed this value in order to have
	 * stable leader information */
	public final Integer leader;
	
	public Propose( ByteBuffer in) {
		super(in);
		leader = in.getInt();
		boolean hasProof = in.get() == 1 ? true : false;
		if (hasProof) {
			proof = new Proof(in);
		} else { 
			proof = null;
		}
	}

	/**
     * Creates a PROPOSE message
     * @param paxosType This should be MessageFactory.COLLECT or MessageFactory.PROPOSE
     * @param id Consensus's execution ID
     * @param round Round number
     * @param sender This should be this process ID
     * @param value The proposed value 
	 * @param leader The initial leader that proposed this.
     * @param proof The proof to be sent by the leader for all replicas
     */
    public Propose(Long id, Integer round, Integer sender, Integer leader, byte[] value, Proof proof) {
		super(MessageFactory.PROPOSE, id, round, sender, value);
		this.leader = leader;
		this.proof = proof;
	}

	/**
     * Returns the proof associated with a PROPOSE or COLLECT message
     * @return The proof
     */
    public Proof getProof() {
        return proof;
    }
    
    
    
    @Override
	public String toString() {
		
		return super.toString()+", Propopser("+leader+")";
	}

	// Implemented method of the Externalizable interface
    @Override
	public void serialise(ByteBuffer out) {
		super.serialise(out);
		out.putInt(leader);
		if (proof != null) {
			out.put((byte) 1);
			proof.serialise(out);
		} else {
			out.put((byte) 0);
		}

	}
    
    @Override
	public int getMsgSize() {
		int ret = super.getMsgSize();

		ret += 5; // 1 for the indication of a proof and 4 for the leader int
		if (proof != null) {
			ret += proof.getMsgSize();
		}

		return ret;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((proof == null) ? 0 : proof.hashCode());
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (!(obj instanceof Propose))
			return false;
		Propose other = (Propose) obj;
		if (proof == null) {
			if (other.proof != null)
				return false;
		} else if (!proof.equals(other.proof))
			return false;
		return true;
	}

}
