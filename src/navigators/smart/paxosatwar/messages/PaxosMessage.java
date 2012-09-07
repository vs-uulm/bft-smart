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
import static navigators.smart.paxosatwar.messages.MessageFactory.*;

import navigators.smart.tom.core.messages.SystemMessage;


/**
 * This class represents a message used in the paxos protocol.
 */
public class PaxosMessage extends SystemMessage {

    private Long number; //execution ID for this message TODO: Isto n devia chamar-se 'eid'?
    private Integer round; // Round number to which this message belongs to
    private int paxosType; // Message type

    /**
     * Creates a paxos message. Not used. TODO: Q tal meter isto como private?
     * @param in 
     * @throws IOException
     */
    public PaxosMessage( ByteBuffer in) {
        super(Type.PAXOS_MSG,in);
        
        paxosType = in.getInt();
		number = Long.valueOf(in.getLong());
		round = Integer.valueOf(in.getInt());
		
    }

    /**
     * @param paxosType This should be MessageFactory.FREEYE
     * @param id Consensus's execution ID
     * @param round Round number
     * @param from This should be this process ID
     */
    public PaxosMessage(int paxosType, Long id, Integer round, Integer from) {
        super(SystemMessage.Type.PAXOS_MSG, from);

        this.paxosType = paxosType;
        this.number = id;
        this.round = round;

    }

    // Implemented method of the Externalizable interface
    @Override
    public void serialise(ByteBuffer out)  {

        super.serialise(out);

        out.putInt(paxosType);
		out.putLong(number.longValue());
		out.putInt(round.intValue());

    }
    
    @Override
    public int getMsgSize(){
    	int ret = super.getMsgSize();
    	return ret += 16; //8(number)+4 (round) +4 (paxostype) 
    }
    
//

    /**
     * Retrieves the round number to which this message belongs
     * @return Round number to which this message belongs
     */
    public Integer getRound() {

        return round;

    }
    
    /**
     * Returns the consensus execution ID of this message
     * @return Consensus execution ID of this message
     */
    public Long getNumber() {

        return number;

    }

    /**
     * Returns this message type
     * @return This message type
     */
    public int getPaxosType() {

        return paxosType;

    }

    /**
     * Returns this message type as a verbose string
     * @return Message type
     */
    public String getPaxosVerboseType() {

        switch (paxosType) {
            case MessageFactory.COLLECT:
                return "COLLECT";
            case MessageFactory.DECIDE:
                return "DECIDE";
            case MessageFactory.FREEZE:
                return "FREEZE";
            case MessageFactory.PROPOSE:
                return "PROPOSE";
            case MessageFactory.STRONG:
                return "STRONG";
            case MessageFactory.WEAK:
                return "WEAK";
            default:
                return "";
        }
    }

    // Over-written method
    @Override
    public String toString() {

        return "type="+getPaxosVerboseType()+", number="+getNumber()+", round="+getRound()+", from="+getSender();

    }

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + (int) (number.longValue() ^ (number.longValue() >>> 32));
		result = prime * result + paxosType;
		result = prime * result + round.intValue();
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
		if (!(obj instanceof PaxosMessage))
			return false;
		PaxosMessage other = (PaxosMessage) obj;
		if (!number.equals(other.number))
			return false;
		if (paxosType != other.paxosType)
			return false;
		if (!round.equals(other.round))
			return false;
		return true;
	}

	public static PaxosMessage readFromBuffer(ByteBuffer buf) {
		//read type for deserialisation and reset it then for beautiful deserialisation
		buf.mark();                 //mark current position
		buf.get();                  //read type
		buf.getInt();               //read sender and drop it
		int ptype = buf.getInt();   //read type
		buf.reset();                //reset buffer
		switch (ptype) {
		case PROPOSE:
			return new Propose(buf);
		case FREEZE:
			return new PaxosMessage(buf);
		case WEAK:
		case STRONG:
		case DECIDE:
			return new VoteMessage(buf);
		case COLLECT:
			return new Collect(buf);
		default:
			System.out.println("Received unknown paxos type:" + ptype);
			return null;
		}
	}


}

