/**
 * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
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


/**
 * This class work as a factory of messages used in the paxos protocol.
 */
public class MessageFactory{

    // constants for messages types
    public static final int PROPOSE = 44781;
    public static final int WEAK    = 44782;
    public static final int STRONG  = 44783;
    public static final int DECIDE  = 44784;
    public static final int FREEZE  = 44785;
    public static final int COLLECT = 44786;

    private Integer sender; // Replica ID of the process which sent this message

    /**
     * Creates a message factory
     * @param myID Replica ID of the process which sent this message
     */
    public MessageFactory(Integer myID) {

        this.sender = myID;

    }

    /**
     * Creates a PROPOSE message to be sent by this process
     * @param id Consensus's execution ID
     * @param round Round number
     * @param value Proposed value
     * @param proof Proofs from other replicas
     * @return A paxos message of the PROPOSE type, with the specified id, round, value, and proof
     */
    public Propose createPropose(Long id, Integer round, byte[] value,
            Proof proof) {

        return new Propose(id,round, sender, value, proof);

    }

    /**
     * Creates a WEAK message to be sent by this process
     * @param id Consensus's execution ID
     * @param round Round number
     * @param value Weakly accepted value
     * @return A paxos message of the WEAK type, with the specified id, round, and value
     */
	public VoteMessage createWeak(Long id, Integer round, byte[] value) {

        return new VoteMessage(WEAK,id,round, sender, value);

    }

    /**
     * Creates a STRONG message to be sent by this process
     * @param id Consensus's execution ID
     * @param round Round number
     * @param value Strongly accepted value
     * @return A paxos message of the STRONG type, with the specified id, round, and value
     */
	public VoteMessage createStrong(Long id, Integer round, byte[] value) {

        return new VoteMessage(STRONG,id,round, sender, value);

    }

    /**
     * Creates a DECIDE message to be sent by this process
     * @param id Consensus's execution ID
     * @param round Round number
     * @param value Decided value
     * @return A paxos message of the DECIDE type, with the specified id, round, and value
     */
	public VoteMessage createDecide(Long id, Integer round, byte[] value) {
         return new VoteMessage(DECIDE,id,round, sender, value);
    }

    /**
     * Creates a FREEZE message to be sent by this process
     * @param id Consensus's execution ID
     * @param round Round number
     * @return A paxos message of the FREEZE type, with the specified id, and round
     */
	public PaxosMessage createFreeze(Long id, Integer round) {
        return new PaxosMessage(FREEZE,id,round, sender);
    }

    /**
     * Creates a COLLECT message to be sent by this process
     * @param id Consensus's execution ID
     * @param round Round number
     * @param proof The proof to be sent by the leader for all replicas
     * @return A paxos message of the COLLECT type, with the specified id, round, and proof
     */
    public Collect createCollect(Long id, Integer round, CollectProof proof) {

        return new Collect (id,round, sender, proof);

    }

}

