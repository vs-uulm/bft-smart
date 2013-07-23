/* * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa,  * and the authors indicated in the @author tags  *   * Licensed under the Apache License, Version 2.0 (the "License");  * you may not use this file except in compliance with the License.  * You may obtain a copy of the License at  *   * http://www.apache.org/licenses/LICENSE-2.0  *   * Unless required by applicable law or agreed to in writing, software  * distributed under the License is distributed on an "AS IS" BASIS,  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  * See the License for the specific language governing permissions and  * limitations under the License.  */package navigators.smart.paxosatwar.messages;import java.nio.ByteBuffer;import java.util.Arrays;import navigators.smart.tom.util.SerialisationHelper;/** * * @author edualchieri * * Proofs for one (freezed) consensus. */public final class FreezeProof {    private final Integer pid; // Replica ID    private final Long eid; // Consensus's execution ID    private final Integer round; // Round number	private final Integer proposer; // Proposer of this round	private final byte[] value; //The value that was proposed to this replica    private final boolean weak; // weakly accepted value    private final boolean strong; // strongly accepted value    private final boolean decide; // decided value    /**     * Creates a new instance of FreezeProof     * @param pid Replica ID     * @param eid Consensus's execution ID     * @param round Round number     * @param weak Weakly accepted value     * @param strong Strongly accepted Value     * @param decide Decided value     */    public FreezeProof(Integer pid, Long eid, Integer round,            Integer proposer, byte[] value, boolean weak, boolean strong, boolean decide) {        this.pid = pid;        this.eid = eid;        this.round = round;		this.proposer = proposer;		this.value = value;        this.weak = weak;        this.strong = strong;        this.decide = decide;    }    /**     * Retrieves the replica ID     * @return Replica ID     */    public Integer getPid() {        return pid;    }    /**     * Retrieves the consensus's execution ID     * @return Consensus's execution ID     */    public Long getEid() {        return eid;    }    /**     * Retrieves the round number     * @return Round number     */    public Integer getRound() {        return round;    }		/**	 * Retrieves the proposed value that was received by this replica in this round	 */	public byte[] getValue() {		return value;	}    /**     * Was this value accepted weakly     * @return True if weakly accepted     */    public boolean isWeak() {        return weak;    }    /**     * Was this value accepted strongly     * @return True if strongly accepted     */    public boolean isStrong() {        return strong;    }        /**     * Was this value decided     * @return True if decided     */    public boolean isDecide() {        return decide;    }		public Integer getProposer(){		return proposer;	}    // Overwriten methods below        @Override    public String toString() {        return eid+" | "+round+" ("+pid+") W="+weak+" S="+strong+" D="+decide+" w="+Arrays.toString(value);    }    @SuppressWarnings("boxing")    public FreezeProof(ByteBuffer in){        pid = in.getInt();        eid = in.getLong();        round = in.getInt();		proposer = in.getInt();		value = SerialisationHelper.readByteArray(in);        weak = in.get() == 1;        strong = in.get() == 1;        decide = in.get() == 1;    }    @SuppressWarnings("boxing")    public void serialise(ByteBuffer out){        out.putInt(pid);        out.putLong(eid);        out.putInt(round);		out.putInt(proposer);		SerialisationHelper.writeByteArray(value, out);        out.put(weak ? (byte) 1 : 0);		out.put(strong ? (byte) 1 : 0);		out.put(decide ? (byte) 1 : 0);    }        public int getMsgSize(){		//4*integer (2 fields 1 arrays), 1* long, 1 arrays, 3 bytes    	return 27 + (value != null ? value.length : 0);//				+ (weak != null ? weak.length : 0)//				+ (strong != null ? strong.length : 0)//				+ (decide != null ? decide.length : 0);    }	/* (non-Javadoc)	 * @see java.lang.Object#hashCode()	 */	@Override	public int hashCode() {		int hash = 7;		hash = 59 * hash + (this.pid != null ? this.pid.hashCode() : 0);		hash = 59 * hash + (this.eid != null ? this.eid.hashCode() : 0);		hash = 59 * hash + (this.round != null ? this.round.hashCode() : 0);		hash = 59 * hash + Arrays.hashCode(this.value);		hash = 59 * hash + (this.weak ? 1 : 0);		hash = 59 * hash + (this.strong ? 1 : 0);		hash = 59 * hash + (this.decide ? 1 : 0);		return hash;	}		/* (non-Javadoc)	 * @see java.lang.Object#equals(java.lang.Object)	 */	@Override	public boolean equals(Object obj) {		if (obj == null) {			return false;		}		if (getClass() != obj.getClass()) {			return false;		}		final FreezeProof other = (FreezeProof) obj;		if (this.pid != other.pid && (this.pid == null || !this.pid.equals(other.pid))) {			return false;		}		if (this.eid != other.eid && (this.eid == null || !this.eid.equals(other.eid))) {			return false;		}		if (this.round != other.round && (this.round == null || !this.round.equals(other.round))) {			return false;		}		if (this.proposer != other.proposer && (this.proposer == null || !this.proposer.equals(other.proposer))) {			return false;		}		if (!Arrays.equals(this.value, other.value)) {			return false;		}		if (this.weak != other.weak) {			return false;		}		if (this.strong != other.strong) {			return false;		}		if (this.decide != other.decide) {			return false;		}		return true;	}	}