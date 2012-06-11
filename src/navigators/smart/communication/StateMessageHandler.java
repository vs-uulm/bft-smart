/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
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
 * @author Christian Spann <christian.spann at uni-ulm.de>
 */
public class StateMessageHandler<A> implements MessageHandler<SMMessage, A> {

	private final static Logger log = Logger.getLogger(ForwardedMessageHandler.class.getCanonicalName());
	private final TOMLayer tomLayer;

	public StateMessageHandler(TOMLayer tomlayer) {
		this.tomLayer = tomlayer;
	}

	@Override
	public void processData(SystemMessage sm) {
		 if(log.isLoggable(Level.FINER))
                log.finer("(MessageHandler.processData) receiving a state managment message from replica " + sm.getSender());
            SMMessage smsg = (SMMessage) sm;
            if (smsg.getType() == TOMUtil.SM_REQUEST) {
                tomLayer.SMRequestDeliver(smsg);
            }
            else {
                tomLayer.SMReplyDeliver(smsg);
            }

	}

	public SMMessage deserialise(SystemMessage.Type type, ByteBuffer buf, A verificationresult) throws IOException, ClassNotFoundException {
		return new SMMessage(buf);
	}
}
