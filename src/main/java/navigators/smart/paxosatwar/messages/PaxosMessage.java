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

import java.io.IOException;
import java.nio.ByteBuffer;
import static navigators.smart.paxosatwar.messages.MessageFactory.*;

import navigators.smart.tom.core.messages.SystemMessage;


/**
 * This class represents a message used in the paxos protocol.
 */
public class PaxosMessage extends SystemMessage {

    public final Long eid; //execution ID for this message 
    public final Integer round; // Round number to which this message belongs to
    public final int paxosType; // Message type


    /**
     * Creates a paxos message from the given bytebuffer
     * @param in 
     * @throws IOException
     */
    public PaxosMessage( ByteBuffer in) {
        super(Type.PAXOS_MSG,in);
        
        paxosType = in.getInt();
		eid = Long.valueOf(in.getLong());
		round = Integer.valueOf(in.getInt());
    }

    /**
     * @param paxosType This should be MessageFactory.FREEYE
     * @param id Consensus's execution ID
     * @param round Round number
     * @param sender This should be this process ID
     */
    public PaxosMessage(int paxosType, Long id, Integer round, Integer sender) {
        super(SystemMessage.Type.PAXOS_MSG, sender);
        this.paxosType = paxosType;
        this.eid = id;
        this.round = round;
    }

    // Implemented method of the Externalizable interface
    @Override
    public void serialise(ByteBuffer out)  {

        super.serialise(out);

        out.putInt(paxosType);
		out.putLong(eid.longValue());
		out.putInt(round.intValue());
    }
    
    @Override
    public int getMsgSize(){
    	int ret = super.getMsgSize();
    	return ret += 16; //8(number)+4 (round) +4 (paxostype)
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
        return eid + " | " + round + " | " + getPaxosVerboseType() + " (" + getSender() + ")";
    }

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + (int) (eid.longValue() ^ (eid.longValue() >>> 32));
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
		if (!eid.equals(other.eid))
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

