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

import java.nio.ByteBuffer;
import java.util.Arrays;

import navigators.smart.tom.util.SerialisationHelper;

public class VoteMessage extends PaxosMessage {

	/**
	 * The value of this Propose
	 */
	public final byte[] value;
	
	/**
     * Creates a VoteMessage message
     * @param paxosType This should be MessageFactory.WEAK, .STRONG or .DECIDE
     * @param id Consensus's execution ID
     * @param round Round number
     * @param from This should be this process ID
     * @param value The proposed value 
	 * @param proposer The proposer that proposed this value
     */
    public VoteMessage( int paxosType, Long id,Integer round,Integer from, 
			byte[] value, Integer proposer){
    	super(paxosType,id,round,from, proposer);
        this.value = value;
    }
	
	public VoteMessage(ByteBuffer in) {
		super(in);
		value = SerialisationHelper.readByteArray(in);
	}
	
	@Override
	public void serialise(ByteBuffer out){
		super.serialise(out);
		SerialisationHelper.writeByteArray(value, out);
		out.putInt(proposer);
	}
	
	@Override
	public int getMsgSize(){
		int ret = super.getMsgSize();
		return ret += 4 + (value!=null ? value.length : 0 ); // +4 (length field) + value.length
	}

//	public VoteMessage(int paxosType, Long id, Integer round, Integer from) {
//		super(paxosType, id, round, from);
//	}

//	/**
//	 * Retrieves the weakly accepted, strongly accepted, decided, or proposed value.
//	 * @return The value
//	 */
//	public byte[] getValue() {
//	    return value;
//	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Arrays.hashCode(value);
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
		if (!(obj instanceof VoteMessage))
			return false;
		VoteMessage other = (VoteMessage) obj;
		if (!Arrays.equals(value, other.value))
			return false;
		return true;
	}

}
