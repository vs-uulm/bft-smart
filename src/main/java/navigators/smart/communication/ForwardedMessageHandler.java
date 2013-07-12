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
import navigators.smart.tom.core.TOMLayer;
import navigators.smart.tom.core.messages.SystemMessage;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.core.timer.messages.ForwardedMessage;

/**
 * Handler for forwarded messages.
 * @author Christian Spann 
 */
public class ForwardedMessageHandler<A> implements MessageHandler<ForwardedMessage, A> {

	private final static Logger log = Logger.getLogger(ForwardedMessageHandler.class.getCanonicalName());
	private final TOMLayer tomLayer;

	public ForwardedMessageHandler(TOMLayer tomlayer) {
		this.tomLayer = tomlayer;
	}

	@Override
	public void processData(SystemMessage sm) {
		TOMMessage request = ((ForwardedMessage) sm).getRequest();
		if (log.isLoggable(Level.FINER)) {
			log.finer("(MessageHandler.processData) receiving: " + request);
		}
		tomLayer.requestReceived(request);

	}

	public ForwardedMessage deserialise(SystemMessage.Type type, ByteBuffer buf, A verificationresult) throws IOException, ClassNotFoundException {
		return new ForwardedMessage(buf);
	}
}
