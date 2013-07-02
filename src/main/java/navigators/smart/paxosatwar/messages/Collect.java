/**
 * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the
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
package navigators.smart.paxosatwar.messages;

import java.io.IOException;
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
    public Collect (Long id,Integer round,Integer sender, Integer proposer, CollectProof proof){
    	super(MessageFactory.COLLECT,id,round,sender, proposer);
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
