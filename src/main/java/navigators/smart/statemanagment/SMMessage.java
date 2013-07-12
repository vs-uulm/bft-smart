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
 */
package navigators.smart.statemanagment;

import java.io.IOException;
import java.nio.ByteBuffer;

import navigators.smart.tom.core.messages.SystemMessage;
import navigators.smart.tom.util.SerialisationHelper;

/**
 * This classe represents a message used in the state transfer protocol
 *
 * @author Jo�o Sousa
 */
public class SMMessage extends SystemMessage {

	private TransferableState state; // State log
	private Long eid; // Execution ID up to which the sender needs to be updated
	private int sm_type; // Message type
	private int replica; // Replica that should send the state
	private transient byte[] serialisedstate;

	/**
	 * Constructs a SMMessage
	 *
	 * @param sender Process Id of the sender
	 * @param eid Execution ID up to which the sender needs to be updated
	 * @param type Message type
	 * @param replica Replica that should send the state
	 * @param state State log
	 */
	public SMMessage(Integer sender, Long eid, int type, int replica, TransferableState state) {

		super(Type.SM_MSG, sender);
		this.state = state;
		this.eid = eid;
		this.sm_type = type;
		this.replica = replica;
		serialisedstate = SerialisationHelper.writeObject(state);
	}

	@SuppressWarnings("boxing")
	public SMMessage(ByteBuffer in) throws IOException, ClassNotFoundException {
		super(Type.SM_MSG, in);
		eid = in.getLong();
		sm_type = in.getInt();
		replica = in.getInt();
		state = (TransferableState) SerialisationHelper.readObject(in);
	}

	/**
	 * Retrieves the state log
	 *
	 * @return The state Log
	 */
	public TransferableState getState() {
		return state;
	}

	/**
	 * Retrieves the type of the message
	 *
	 * @return The type of the message
	 */
	public int getType() {
		return sm_type;
	}

	/**
	 * Retrieves the execution ID up to which the sender needs to be updated
	 *
	 * @return The execution ID up to which the sender needs to be updated
	 */
	public Long getEid() {
		return eid;
	}

	/**
	 * Retrieves the replica that should send the state
	 *
	 * @return The replica that should send the state
	 */
	public int getReplica() {
		return replica;
	}

	@Override
	public void serialise(ByteBuffer out) {
		super.serialise(out);
		out.putLong(eid.longValue());
		out.putInt(sm_type);
		out.putInt(replica);
		SerialisationHelper.writeByteArray(serialisedstate, out);
	}

	/*
	 * (non-Javadoc) @see navigators.smart.tom.core.messages.SystemMessage#getMsgSize()
	 */
	@Override
	public int getMsgSize() {
		return super.getMsgSize() + 20 + serialisedstate.length;
	}
}
