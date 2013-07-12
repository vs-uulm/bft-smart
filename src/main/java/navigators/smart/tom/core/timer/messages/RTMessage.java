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
 */package navigators.smart.tom.core.timer.messages;

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
