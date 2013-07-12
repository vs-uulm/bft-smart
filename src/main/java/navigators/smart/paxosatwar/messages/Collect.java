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

public class Collect extends PaxosMessage {
	
	/**
	 * Proof for this collect.
	 */
	private CollectProof proof; 
	
	/**
     * Creates a COLLECT message
     * @param id Consensus's execution ID
     * @param round Round number
     * @param sender This should be this process ID
     * @param proof The proof to be sent by the leader for all replicas
     */
    public Collect (Long id,Integer round,Integer sender, CollectProof proof){
    	super(MessageFactory.COLLECT,id,round,sender);
        this.proof = proof;
    }

	
	public Collect(ByteBuffer in) {
		super(in);
		proof = new CollectProof(in);
	}

	
	 /**
     * Returns the proof associated with this COLLECT message
     * @return The proof
     */
    public CollectProof getProof() {
        return proof;
    }
    
    // Implemented method of the Externalizable interface
    @Override
    public void serialise(ByteBuffer out) {
        super.serialise(out);
        proof.serialise(out);
    }
    
	@Override
	public int getMsgSize() {
		int ret = super.getMsgSize();

		ret += proof.getMsgSize();

		return ret;
	}

}
