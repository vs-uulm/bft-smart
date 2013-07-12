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
 */package navigators.smart.paxosatwar.messages;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Formatter;
import java.util.LinkedList;
import navigators.smart.tom.util.SerialisationHelper;

/**
 * This class contains the proofs for all freezed rounds until the current one
 * in order to enable the choice of a good value for the next round.
 */
public final class CollectProof {

    /**
     *  Proofs of this replica of all previous rounds including the currently
     *  frozen one
     */
    private final LinkedList<FreezeProof> proofIn;

    /** The new leader id*/
    private final Integer newLeader;
	
	/** The first proposer id, that did not cheat */
	public final Integer proposer;

    private byte[] signature;

    private byte[] serialisedForm;

    /**
     * Creates a new instance of CollectProof
     * @param proofIn Proofs to freezed consensus
     * @param proposer The proposer of this value.
     * @param newLeader The new leader id
     */
    public CollectProof(LinkedList<FreezeProof> proofIn, Integer newLeader, 
			Integer proposer) {
		if(proofIn == null){
			throw new NullPointerException("Null freezeprooflist not allowed");
		}
        this.proofIn = proofIn;
        this.newLeader = newLeader;
		this.proposer = proposer;
    }
    
    /**
     * Retrieves the proof
     * @return The list of proofs
     */
    public LinkedList<FreezeProof> getProofs(){
		return this.proofIn;
    }
    
    /**
    * Retrieves the leader ID
    * @return The leader ID
    */
    public Integer getLeader(){

        return this.newLeader;

    }

    @SuppressWarnings("boxing")
   public CollectProof(ByteBuffer in) {
		
		int size = in.getInt();
		proofIn = new LinkedList<FreezeProof>();
		for(int i = 0;i<size;i++){
			proofIn.add(new FreezeProof(in));
		}
		newLeader = in.getInt();
		proposer = in.getInt();
		signature = SerialisationHelper.readByteArray(in);
		
	}

    public void serialise(ByteBuffer out) {
        if(serialisedForm == null){
			serialisedForm = getBytes();
        } 
       out.put(serialisedForm);
       SerialisationHelper.writeByteArray(signature, out);
    }
    
	
	
    public int getMsgSize(){
		// the size of all proofs + 4 for the size of th signature + the signature length
    	return getProofsSize() + 4 + signature.length;
    }
	
	
	private int getProofsSize(){
		int size = 0;
		for(FreezeProof p:proofIn){
			size += p.getMsgSize();
		}
		return size	
				+ 4 // newleader
				+ 4 // proposer
				+ 4; //Number of proofs
	}

    public byte[] getBytes() {
        if(serialisedForm == null){
        	ByteBuffer buf = ByteBuffer.allocate(getProofsSize());
           //serialise without signature
          
			buf.putInt(proofIn.size());
			for(FreezeProof p:proofIn){
				p.serialise(buf);
			}
				
            buf.putInt(newLeader.intValue());
			buf.putInt(proposer);
            serialisedForm = buf.array();
        }
        return serialisedForm;
    }
	
    public void setSignature(byte[] sign) {
        signature = sign;
    }

    public byte[] getSignature() {
        return signature;
    }

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + newLeader.hashCode();
		result = prime * result + ((proofIn == null) ? 0 : proofIn.hashCode());
		result = prime * result + Arrays.hashCode(serialisedForm);
		result = prime * result + Arrays.hashCode(signature);
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
		if (!(obj instanceof CollectProof))
			return false;
		CollectProof other = (CollectProof) obj;
		if (!newLeader.equals(other.newLeader))
			return false;
		if (proofIn == null) {
			if (other.proofIn != null)
				return false;
		} else if (!proofIn.equals(other.proofIn))
			return false;
		if (!Arrays.equals(serialisedForm, other.serialisedForm))
			return false;
		if (!Arrays.equals(signature, other.signature))
			return false;
		return true;
	}
	
	@Override
	public String toString(){
		return String.format("CP | L %1s | Proofs %2s", newLeader, proofIn);
	}

}

