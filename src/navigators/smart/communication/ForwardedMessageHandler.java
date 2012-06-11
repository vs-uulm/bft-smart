/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
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
 * @author Christian Spann <christian.spann at uni-ulm.de>
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
