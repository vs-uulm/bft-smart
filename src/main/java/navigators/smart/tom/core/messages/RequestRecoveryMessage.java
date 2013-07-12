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
 */package navigators.smart.tom.core.messages;

import java.io.IOException;
import java.nio.ByteBuffer;

import navigators.smart.tom.util.SerialisationHelper;
import navigators.smart.tom.util.TOMUtil;


/**
 * This class represents a message used when recovering requests
 *
 */
public class RequestRecoveryMessage extends SystemMessage {

    private int type; // Message type (RR_REQUEST, RR_REPLY, RR_DELIVERED)
    private Integer consId; // Consensus's ID to which the request recover refers to
    private byte[] hash; // Hash of the request being recovered
    private TOMMessage msg; // TOM message containing the request being recovered

    /**
     * Creates a new instance of RecoveryRequestMessage
     */
    @SuppressWarnings("boxing")
    public RequestRecoveryMessage(ByteBuffer in) throws IOException, ClassNotFoundException {
        super(Type.RR_MSG,in);
        consId = in.getInt();
        type = in.getInt();
        hash = SerialisationHelper.readByteArray(in);

        msg = new TOMMessage(in);
    }

    /**
     * Creates a new instance of RecoveryRequestMessage, of the RR_REQUEST type
     * @param hash Hash of the request being recovered
     * @param from ID of the process which sent the message
     */
    public RequestRecoveryMessage(byte[] hash, Integer from) {
        super(Type.RR_MSG,from);
        this.hash = hash;
        this.type = TOMUtil.RR_REQUEST;
    }

    /**
     * Creates a new instance of RecoveryRequestMessage, of the RR_REPLY type
     * @param msg TOM message containing the request being recovered
     * @param from ID of the process which sent the message
     */
    public RequestRecoveryMessage(TOMMessage msg, Integer from) {
        super(Type.RR_MSG,from);
        this.msg = msg;
        this.type = TOMUtil.RR_REPLY;
    }

    /**
     * Creates a new instance of RecoveryRequestMessage, of the RR_DELIVERED type
     * @param hash Hash of the request being recovered
     * @param from ID of the process which sent the message
     * @param consId  Consensus's ID to which the request recover refers to
     */
    public RequestRecoveryMessage(byte[] hash, Integer from, Integer consId) {
        super(Type.RR_MSG,from);
        this.hash = hash;
        this.type = TOMUtil.RR_DELIVERED;
        this.consId = consId;
    }

    /**
     * Retrieves the consensus's ID to which the request recover refers to
     * @return The consensus's ID to which the request recover refers to
     */
    public Integer getConsId() {
        return this.consId;
    }

    /**
     * Retrieves the message type (RR_REQUEST, RR_REPLY, RR_DELIVERED)
     * @return The message type (RR_REQUEST, RR_REPLY, RR_DELIVERED)
     */
    public int getType() {
        return this.type;
    }

    /**
     * Retrieves the TOM message containing the request being recovered
     * @return The TOM message containing the request being recovered
     */
    public TOMMessage getMsg() {
        return this.msg;
    }

    /**
     * Retrieves the hash of the request being recovered. If there is no hash the length is 0
     * @return The hash of the request being recovered
     */
    public byte[] getHash() {
        return this.hash;
    }

    // The following are overwritten methods

    @Override
    public void serialise(ByteBuffer out) {

        super.serialise(out);
        out.putInt(consId.intValue());
        out.putInt(type);

        SerialisationHelper.writeByteArray(hash, out);

        msg.serialise(out);
    }
    
    

    @Override
    public String toString() {
        return "consId=" + getConsId() + ", type=" + getType() + ", from=" + getSender();
    }

	/* (non-Javadoc)
	 * @see navigators.smart.tom.core.messages.SystemMessage#getMsgSize()
	 */
	@Override
	public int getMsgSize() {
		return super.getMsgSize() + 12 + hash.length+msg.getMsgSize();
	}
}