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
package navigators.smart.communication;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import navigators.smart.statemanagment.SMMessage;
import navigators.smart.tom.core.TOMLayer;
import navigators.smart.tom.core.messages.SystemMessage;
import navigators.smart.tom.util.TOMUtil;

/**
 * Handler for forwarded messages.
 * @author Christian Spann 
 */
public class StateMessageHandler implements MessageHandler<SMMessage, Boolean> {

	private final static Logger log = Logger.getLogger(ForwardedMessageHandler.class.getCanonicalName());
	private final TOMLayer tomLayer;

	public StateMessageHandler(TOMLayer tomlayer) {
		this.tomLayer = tomlayer;
	}

	@Override
	public void processData(SystemMessage sm) {
		if (log.isLoggable(Level.FINER)) {
			log.finer("(MessageHandler.processData) receiving a state managment message from replica " + sm.getSender());
		}
		SMMessage smsg = (SMMessage) sm;
		if (smsg.getType() == TOMUtil.SM_REQUEST) {
			tomLayer.SMRequestDeliver(smsg);
		} else {
			tomLayer.SMReplyDeliver(smsg);
		}

	}

	public SMMessage deserialise(SystemMessage.Type type, ByteBuffer buf, 			Boolean verificationresult) throws IOException, ClassNotFoundException {		if(!verificationresult){			log.severe("Unverified SMMessage");			return null;		}
		return new SMMessage(buf);
	}
}
