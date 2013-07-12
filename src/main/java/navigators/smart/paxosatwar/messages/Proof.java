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

/**
 *
 * @author edualchieri
 *
 * This class represents the proof used in the rounds freeze processing.
 * The SignedObject contain the CollectProof for this server.
 */
public final class Proof {

    private CollectProof[] proofs; // Signed proofs
//    private byte[] nextPropose; // next value to be proposed
 
    /**
     * Creates a new instance of Proof
     * @param proofs Signed proofs
     * @param nextPropose Next value to be proposed
     */
    public Proof(CollectProof[] proofs/*, byte[] nextPropose*/) {
        this.proofs = proofs;
//        this.nextPropose = nextPropose;
    }

    public Proof(ByteBuffer in){
        proofs = new CollectProof[in.getInt()];
		for(;;) {
            int pos = in.getInt();
            if(pos < 0){
                break;  //reached end
            }
            proofs[pos] = new CollectProof(in);
		} 
//        nextPropose = SerialisationHelper.readByteArray(in);
    }

    /**
     * Serialises this Proof to the given ByteBuffer. The Buffer is required
	 * to have at least @see Proof#getMsgSize() Bytes remaining.
	 * @param out The ByteBuffer to write this object to
     */
    public void serialise(ByteBuffer out) {
        out.putInt(proofs.length);
        for (int i = 0; i < proofs.length; i++) {
            if(proofs[i]!=null){
               out.putInt(i);
               proofs[i].serialise(out);
            }
        }
        out.putInt(-1); //put end tag
//        SerialisationHelper.writeByteArray(nextPropose, out);

    }
    
	/**
	 * Returns the message size of this data structure.
	 * It contains the number of proofs, an integer for the size of each proof, and an end Tag because
	 * not all CollectProofs may be set and msg size can be shrunk then.
	 * tag.
	 * @return 
	 */
	public int getMsgSize() {
    	 int ret = 12;
		 
         for (int i = 0; i < proofs.length; i++) {
             if(proofs[i]!=null){
            	 ret += 4;
            	 ret += proofs[i].getMsgSize();
             }
         }
//        ret += (nextPropose != null) ? nextPropose.length : 0;
		return ret;
	}
    
    /**
     * Retrieves the signed proofs
     * @return Signed proofs
     */
    public CollectProof[] getProofs(){
        return this.proofs;
    }
}

