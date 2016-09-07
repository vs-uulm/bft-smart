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

package navigators.smart.tom.core.timer.messages;

import java.io.IOException;
import java.nio.ByteBuffer;

import navigators.smart.tom.core.messages.SystemMessage;
import navigators.smart.tom.util.SerialisationHelper;


/**
 * This class represents a message used in the TO-FREEZE phase
 * 
 */
public class RTMessage extends SystemMessage {
   
    private int rtType; // message type (RT_TIMEOUT, RT_COLLECT, RT_LEADER)
    private Integer reqId; // Request ID associated with the timeout
    private Object content; // content of this message. Varies according to the message type
    private transient byte[] serialisedcontent;

    /**
     * Creates a new instance of RequestTimeoutMessage
     * @param in The stream containing the data to create this object
     * @throws IOException 
     * @throws ClassNotFoundException
     */
    @SuppressWarnings("boxing")
    public RTMessage(ByteBuffer in) throws IOException, ClassNotFoundException{
        super(Type.RT_MSG, in);
        rtType = in.getInt();
        reqId = in.getInt();
        content = SerialisationHelper.readObject(in);
    }

    /**
     * Creates a new instance of RequestTimeoutMessage
     * @param rtType Message type (RT_TIMEOUT, RT_COLLECT, RT_LEADER)
     * @param reqId Request ID associated with the timeout
     * @param from Replica ID of the sender
     * @param content Content of this message. Varies according to the message type
     */
    public RTMessage(int rtType, Integer reqId, Integer from, Object content) {
        super(Type.RT_MSG,from);
        this.rtType = rtType;
        this.reqId = reqId;
        this.content = content;
    }

    /**
     * Retrieves the message type
     * @return The message type
     */
    public int getRTType(){
        return this.rtType;
    }

    /**
     * Retrieves the request ID associated with the timeout
     * @return The request ID associated with the timeout
     */
    public Integer getReqId(){
        return this.reqId;
    }

    /**
     * Retrieves the content of this message
     * @return  The content of this message
     */
    public Object getContent(){
        return this.content;
    }

    // overwritten methods from the super-class

    @Override
    public void serialise(ByteBuffer out) {
        super.serialise(out);
        out.putInt(rtType);
        out.putInt(reqId.intValue());
        SerialisationHelper.writeByteArray(serialisedcontent, out);
        
    }

	/* (non-Javadoc)
	 * @see navigators.smart.tom.core.messages.SystemMessage#getMsgSize()
	 */
	@Override
	public int getMsgSize() {
		if(serialisedcontent == null){
			serialisedcontent = SerialisationHelper.writeObject(content);
		}
		return super.getMsgSize()+12+serialisedcontent.length;
	}
}
