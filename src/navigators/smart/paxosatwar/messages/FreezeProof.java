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

import java.nio.ByteBuffer;
import java.util.Arrays;

import navigators.smart.tom.util.SerialisationHelper;

/**
 *
 * @author edualchieri
 *
 * Proofs for one (freezed) consensus.
 */
public final class FreezeProof {

    private final Integer pid; // Replica ID
    private final Long eid; // Consensus's execution ID
    private final Integer round; // Round number
	private final byte[] value; //The value that was proposed to this replica
    private final boolean weak; // weakly accepted value
    private final boolean strong; // strongly accepted value
    private final boolean decide; // decided value

    /**
     * Creates a new instance of FreezeProof
     * @param pid Replica ID
     * @param eid Consensus's execution ID
     * @param round Round number
     * @param weak Weakly accepted value
     * @param strong Strongly accepted Value
     * @param decide Decided value
     */
    public FreezeProof(Integer pid, Long eid, Integer round,
            byte[] value, boolean weak, boolean strong, boolean decide) {

        this.pid = pid;
        this.eid = eid;
        this.round = round;
		this.value = value;
        this.weak = weak;
        this.strong = strong;
        this.decide = decide;
    }

    /**
     * Retrieves the replica ID
     * @return Replica ID
     */
    public Integer getPid() {

        return pid;

    }

    /**
     * Retrieves the consensus's execution ID
     * @return Consensus's execution ID
     */
    public Long getEid() {

        return eid;

    }

    /**
     * Retrieves the round number
     * @return Round number
     */
    public Integer getRound() {

        return round;

    }
	
	/**
	 * Retrieves the proposed value that was received by this replica in this round
	 */
	public byte[] getValue() {
		return value;
	}

    /**
     * Was this value accepted weakly
     * @return True if weakly accepted
     */
    public boolean isWeak() {

        return weak;

    }

    /**
     * Was this value accepted strongly
     * @return True if strongly accepted
     */
    public boolean isStrong() {

        return strong;

    }
    
    /**
     * Was this value decided
     * @return True if decided
     */
    public boolean isDecide() {

        return decide;

    }

    // Overwriten methods below
    
    @Override
    public String toString() {

        return "W="+weak+" S="+strong+" D="+decide;

    }

    @SuppressWarnings("boxing")
    public FreezeProof(ByteBuffer in){
        pid = in.getInt();
        eid = in.getLong();
        round = in.getInt();
		value = SerialisationHelper.readByteArray(in);
        weak = in.get() == 1;
        strong = in.get() == 1;
        decide = in.get() == 1;
    }

    @SuppressWarnings("boxing")
    public void serialise(ByteBuffer out){
        out.putInt(pid);
        out.putLong(eid);
        out.putInt(round);
		SerialisationHelper.writeByteArray(value, out);
        out.put(weak ? (byte) 1 : 0);
		out.put(strong ? (byte) 1 : 0);
		out.put(decide ? (byte) 1 : 0);
    }
    
    public int getMsgSize(){
		//5*integer (2 fields 3 arrays), 1* long, 3 arrays
    	return 21 + (value != null ? value.length : 0);
//				+ (weak != null ? weak.length : 0)
//				+ (strong != null ? strong.length : 0)
//				+ (decide != null ? decide.length : 0);
    }

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */

	@Override
	public int hashCode() {
		int hash = 7;
		hash = 59 * hash + (this.pid != null ? this.pid.hashCode() : 0);
		hash = 59 * hash + (this.eid != null ? this.eid.hashCode() : 0);
		hash = 59 * hash + (this.round != null ? this.round.hashCode() : 0);
		hash = 59 * hash + Arrays.hashCode(this.value);
		hash = 59 * hash + (this.weak ? 1 : 0);
		hash = 59 * hash + (this.strong ? 1 : 0);
		hash = 59 * hash + (this.decide ? 1 : 0);
		return hash;
	}
	

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final FreezeProof other = (FreezeProof) obj;
		if (this.pid != other.pid && (this.pid == null || !this.pid.equals(other.pid))) {
			return false;
		}
		if (this.eid != other.eid && (this.eid == null || !this.eid.equals(other.eid))) {
			return false;
		}
		if (this.round != other.round && (this.round == null || !this.round.equals(other.round))) {
			return false;
		}
		if (!Arrays.equals(this.value, other.value)) {
			return false;
		}
		if (this.weak != other.weak) {
			return false;
		}
		if (this.strong != other.strong) {
			return false;
		}
		if (this.decide != other.decide) {
			return false;
		}
		return true;
	}
	
}

