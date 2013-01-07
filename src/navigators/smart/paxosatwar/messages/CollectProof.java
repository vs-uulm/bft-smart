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

package navigators.smart.paxosatwar.messages;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import navigators.smart.tom.util.SerialisationHelper;

/**
 * Proofs to freezed consensus. This class can contain proofs for two consensus.
 * The freezed one, and the next one (if have).
 */
public final class CollectProof {

    // Proofs to freezed consensus
    private final FreezeProof proofIn;

    // Proofs to next consensus, if have next - after the freezed one
    private final FreezeProof proofNext;

    // The new leader id
    private final Integer newLeader;

    private byte[] signature;

    private byte[] serialisedForm;

    /**
     * Creates a new instance of CollectProof
     * @param proofIn Proofs to freezed consensus
     * @param proofNext Proofs to next consensus, if have next - after the freezed one
     * @param newLeader The new leader id
     */
    public CollectProof(FreezeProof proofIn, FreezeProof proofNext, Integer newLeader) {

        this.proofIn = proofIn;
        this.proofNext = proofNext;
        this.newLeader = newLeader;

    }
    
    /**
     * Retrieves the proof
     * @param in True for the proof of the freezed consensus, false for the proof of the next consensus
     * @return
     */
    public FreezeProof getProofs(boolean in){
        if(in){
            return this.proofIn;
        }else{
            return this.proofNext;
        }
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
		
		proofIn = (in.get() == 1) ? new FreezeProof(in) : null;
		proofNext = (in.get() == 1) ? new FreezeProof(in) : null;
		newLeader = in.getInt();
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
		//2 for the indicators if proofin and proofnext exist
    	return getProofsSize() + 4 + signature.length;
    }
	
	
	private int getProofsSize(){
		return 2 // presence indicator bytes
				+ (proofIn != null ? proofIn.getMsgSize() : 0)
				+ (proofNext != null ? proofNext.getMsgSize():0)
				+ 4; // newleader
	}

    public byte[] getBytes() {
        if(serialisedForm == null){
        	ByteBuffer buf = ByteBuffer.allocate(getProofsSize());
           //serialise without signature
           if(proofIn != null){
				buf.put((byte)1);
				proofIn.serialise(buf);
			} else {
				buf.put((byte)0);
			}
			if(proofNext != null) {
				buf.put((byte)1);
				proofNext.serialise(buf);
			} else {
				buf.put((byte)0);
			}
				
            buf.putInt(newLeader.intValue());
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
		result = prime * result + ((proofNext == null) ? 0 : proofNext.hashCode());
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
		if (proofNext == null) {
			if (other.proofNext != null)
				return false;
		} else if (!proofNext.equals(other.proofNext))
			return false;
		if (!Arrays.equals(serialisedForm, other.serialisedForm))
			return false;
		if (!Arrays.equals(signature, other.signature))
			return false;
		return true;
	}

}

