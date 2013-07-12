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

import java.io.DataInput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * This is the super-class for all other kinds of messages created by JBP
 */

public abstract class SystemMessage {

    public enum Type {

        TOM_MSG(Byte.valueOf((byte) 1)),
        FORWARDED(Byte.valueOf((byte) 2)),
        PAXOS_MSG(Byte.valueOf((byte) 3)),
        RR_MSG(Byte.valueOf((byte) 4)),
        RT_MSG(Byte.valueOf((byte) 5)),
        SM_MSG(Byte.valueOf((byte) 6));

        public final Byte type;

        private static Map<Byte,Type> mapping = new HashMap<Byte, Type>();
        

        static{
            for(Type type:values()){
                mapping.put(type.type, type);
            }
        }

        Type (Byte type) {
            this.type = type;
        }

        public static Type getByByte(byte type){
            return mapping.get(Byte.valueOf(type));
        }
        
    }
    
    public final Type type;
    protected final Integer sender; // ID of the process which sent the message
    protected volatile byte[] msgdata; //serialised version of this message

    /**
     * Creates a new instance of SystemMessage
     * @param type The type id of this message
     * @param in The inputstream containing the serialised object
     */
    public SystemMessage(Type type, ByteBuffer in){
        this.type = type;
        in.get();
        sender = Integer.valueOf(in.getInt());
    }
    
    /**
     * Creates a new instance of SystemMessage
     * @param type The type id of this message
     * @param in The inputstream containing the serialised object
     * @throws IOException 
     */
    public SystemMessage(Type type, DataInput in) throws IOException{
    	this.type = type;
    	in.readByte();
    	sender = Integer.valueOf(in.readInt());
    }
    
	/**
     * Creates a new instance of SystemMessage
     * @param type The type id of this message for preformant serialisation
     * @param sender ID of the process which sent the message
     */
    public SystemMessage(Type type, Integer sender){
        this.type = type;
        this.sender = sender;
    }
    
    /**
     * Returns the ID of the process which sent the message
     * @return
     */
    public Integer getSender() {
        return sender;
    }

    /**
     * this method serialises the contents of this class
     * @param out
     */
    public void serialise(ByteBuffer out){
        out.put(type.type.byteValue());
        out.putInt(sender.intValue());
    }
    
    /**
     * this method serialises the contents of this class
     * @param out
     * @throws IOException
     */
//    public void serialise(DataOutput out) throws IOException{
//    	out.writeByte(type.type);
//    	out.writeInt(sender);
//    }

    public byte[] getBytes(){
    	if(msgdata == null){
    		ByteBuffer buf = ByteBuffer.allocate(getMsgSize());
    		serialise(buf);
    		msgdata = buf.array();
    	}
        return msgdata;
    }
    
    /**
     * Sets the messagedata in serialised form
     * @param bytes The data of the message
     */
    public void setBytes(byte[] bytes){
    	msgdata = bytes;
    }

    /**
     * Returns the messageSize in Bytes that the serialised version of this msg will use.
     * @return The messagesize in bytes
     */
	public int getMsgSize() {
		return 5;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(msgdata);
		result = prime * result + sender.intValue();
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof SystemMessage))
			return false;
		SystemMessage other = (SystemMessage) obj;
		if (!Arrays.equals(msgdata, other.msgdata))
			return false;
		if (!sender.equals(other.sender))
			return false;
		if (type == null) {
			if (other.type != null)
				return false;
		} else if (!type.equals(other.type))
			return false;
		return true;
	}
}
