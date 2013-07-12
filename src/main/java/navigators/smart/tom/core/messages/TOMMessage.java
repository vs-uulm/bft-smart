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

import java.nio.ByteBuffer;

import navigators.smart.tom.util.DebugInfo;
import navigators.smart.tom.util.SerialisationHelper;

/**
 * This class represents a total ordered message
 */
public class TOMMessage extends SystemMessage implements Comparable<TOMMessage> {

    private int sequence; // Sequence number defined by the client
    private byte[] content = null; // Content of the message
    private boolean readOnlyRequest = false; //this is a read only request

    //the fields bellow are not serialized!!!
    private transient Integer id; // ID for this message. It should be unique

    public transient long timestamp = 0; // timestamp to be used by the application
    public transient byte[] nonces = null; // nonces to be used by the applciation

    public transient Integer destination = null; // message destination
    public transient boolean signed = false; // is this message signed?
//    public transient boolean includesClassHeader = false; //are class header serialized

    public transient long receptionTime;//the reception time of this message
    public transient boolean timeout = false;//this message was timed out?

    //the bytes received from the client and its MAC and signature
//    public transient byte[] serializedMessage = null;
    public transient byte[] serializedMessageSignature = null;
    public transient byte[] serializedMessageMAC = null;

    //for benchmarking purposes
    public transient long consensusStartTime=0;
    public transient long consensusExecutionTime=0;
    public transient int consensusBatchSize=0;
    public transient long requestTotalLatency=0;

    public TOMMessage(ByteBuffer in) {
        super(Type.TOM_MSG,in);
        sequence = in.getInt();
		readOnlyRequest = in.get() == 1 ? true : false;
        content = SerialisationHelper.readByteArray(in);
        buildId();
    }

    /**
     * Creates a new instance of TOMMessage
     *
     * @param sender ID of the process which sent the message
     * @param sequence Sequence number defined by the client
     * @param content Content of the message
     */
    public TOMMessage(Integer sender, int sequence, byte[] content) {
        this(sender,sequence,content,false);
    }

    /**
     * Creates a new instance of TOMMessage
     *
     * @param sender ID of the process which sent the message
     * @param sequence Sequence number defined by the client
     * @param content Content of the message
     * @param readOnlyRequest it is a read only request
     */
    public TOMMessage(Integer sender, int sequence, byte[] content, boolean readOnlyRequest) {
        super(Type.TOM_MSG,sender);
        this.sequence = sequence;

        buildId();
        this.content = content;
        this.readOnlyRequest = readOnlyRequest;
    }

    /** ISTO E CODIGO DO JOAO, PARA TRATAR DE DEBUGGING */
    private transient DebugInfo info = null; // Debug information
    
    /**
     * Retrieves the debug info from the TOM layer
     * @return The debug info from the TOM layer
     */
    public DebugInfo getDebugInfo() {
        return info;
    }
    
    /**
     * Sets the debug info from the TOM layer
     * @return The debug info from the TOM layer
     */
    public void  setDebugInfo(DebugInfo info) {
        this.info = info;
    }

    /****************************************************/

    /**
     * Retrieves the sequence number defined by the client
     * @return The sequence number defined by the client
     */
    public int getSequence() {
        return sequence;
    }

    /**
     * Retrieves the ID for this message. It should be unique
     * @return The ID for this message.
     */
    public Integer getId() {
        return id;
    }

    /**
     * Retrieves the content of the message
     * @return The content of the message
     */
    public byte[] getContent() {
        return content;
    }

    /**
     * @return the readOnlyRequest
     */
    public boolean isReadOnlyRequest() {
        return readOnlyRequest;
    }
    
    /**
     * Verifies if two TOMMessage objects are equal.
     *
     * Two TOMMessage are equal if they have the same sender,
     * sequence number and content.
     */
    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        if (!(o instanceof TOMMessage)) {
            return false;
        }

        TOMMessage mc = (TOMMessage) o;

        return (mc.getSender().equals( sender)) && (mc.getSequence() == sequence);
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 59 * hash + this.sequence;
        hash = 59 * hash + this.getSender().intValue();
        return hash;
    }

    @Override
    public String toString() {
        return "TOMMsg (" + sender + "," + sequence + ")";
    }

    @Override
    public void serialise(ByteBuffer out) {
        super.serialise(out);
        out.putInt(sequence);
		out.put(readOnlyRequest?(byte)1:0);
        SerialisationHelper.writeByteArray(content, out);
    }
    
    @Override
    public int getMsgSize(){
    	return super.getMsgSize() + 9 + content.length; //4 + 1 + 4 + content.length
    }
    

//    @Override
//    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
//
//    }

//    public void writeExternal(DataOutput out) throws IOException {
//        out.writeInt(sender);
//        out.writeInt(sequence);
//
//        if (content == null) {
//            out.writeInt(-1);
//        } else {
//            out.writeInt(content.length);
//            out.write(content);
//        }
//
//        out.writeBoolean(isReadOnlyRequest());
//    }

//    public void readExternal(DataInput in) throws IOException, ClassNotFoundException {
//        sender = in.readInt();
//        sequence = in.readInt();
//
//        int toRead = in.readInt();
//        if (toRead != -1) {
//            content = new byte[toRead];
//
//            in.readFully(content);
//        }
//
//        readOnlyRequest = in.readBoolean();
//
//        buildId();
//    }


    /**
     * Used to build an unique id for the message
     */
    @SuppressWarnings("boxing")
    private void buildId() {
        id = (sender << 20) | sequence;
    }

    /**
     * Retrieves the process ID of the sender given a message ID
     * @param id Message ID
     * @return Process ID of the sender
     */
    public static Integer getSenderFromId(Integer id) {
        return Integer.valueOf(id.intValue() >>> 20);
    }

//    public static byte[] messageToBytes(TOMMessage m) {
//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
////        DataOutputStream dos = new DataOutputStream(baos);
//        try{
//            ObjectOutput oo = new ObjectOutputStream(baos);
//            m.serialise(oo);
//            oo.flush();
//        }catch(Exception e) {
//        }
//        return baos.toByteArray();
//    }
//
//    public static TOMMessage bytesToMessage(byte[] b) {
//        ByteBuffer buf = ByteBuffer.wrap(b);
//        TOMMessage m = new TOMMessage(buf);
//        return m;
//    }

    @SuppressWarnings("boxing")
    public int compareTo(TOMMessage tm) {
        final int BEFORE = -1;
        final int EQUAL = 0;
        final int AFTER = 1;

        if (this.equals(tm))
            return EQUAL;

        if (this.getSender() < tm.getSender())
            return BEFORE;
        if (this.getSender() > tm.getSender())
            return AFTER;

        if (this.getSequence() < tm.getSequence())
            return BEFORE;
        if (this.getSequence() > tm.getSequence())
            return AFTER;

        return EQUAL;
    }
}

