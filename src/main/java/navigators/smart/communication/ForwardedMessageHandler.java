/**
 * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the
 *
 * @author tags
 *
 * This file is part of SMaRt.
 *
 * SMaRt is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * SMaRt is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with SMaRt. If not, see <http://www.gnu.org/licenses/>.
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
