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
package navigators.smart.paxosatwar.messages;

import static navigators.smart.paxosatwar.messages.MessageFactory.COLLECT;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.communication.MessageHandler;
import navigators.smart.paxosatwar.requesthandler.RequestHandler;
import navigators.smart.paxosatwar.roles.Acceptor;
import navigators.smart.paxosatwar.roles.Proposer;
import navigators.smart.tom.core.messages.SystemMessage;
import navigators.smart.tom.core.messages.SystemMessage.Type;
import navigators.smart.tom.core.timer.messages.RTMessage;


/**
 *
 * @author Christian Spann 
 */
public class PaWMessageHandler<T> implements MessageHandler<SystemMessage,T>{
    
	public static final Logger log = Logger.getLogger(PaWMessageHandler.class.getCanonicalName());
    
    private Proposer proposer;
    private Acceptor acceptor;
    private RequestHandler reqhandler;

    public PaWMessageHandler(Acceptor acc, Proposer prop, RequestHandler reqhandlr){
        this.proposer = prop;
        this.acceptor = acc;
        this.reqhandler = reqhandlr;
    }
    
     public void setProposer(Proposer proposer) {
        this.proposer = proposer;
    }

    public void setAcceptor(Acceptor acceptor) {
        this.acceptor = acceptor;
    }

	@Override
    public void processData(SystemMessage sm) {
        if (sm instanceof PaxosMessage) {
            PaxosMessage paxosMsg = (PaxosMessage) sm;
            //Logger.println("(TOMMessageHandler.processData) PAXOS_MSG received: " + paxosMsg);
            if (paxosMsg.getPaxosType() == COLLECT) {
                //the proposer layer only handle COLLECT messages
                proposer.deliver((Collect) paxosMsg);
            } else {
                acceptor.deliver(paxosMsg);
            }
        } else if (sm instanceof RTMessage) {
            RTMessage rtMsg = (RTMessage) sm;
            //Logger.println("(TOMMessageHandler.processData) RT_MSG received: "+rtMsg);
            reqhandler.deliverTimeoutRequest(rtMsg);
        } else {
			log.log(Level.SEVERE, "Unknown message received {0}", sm);
		}
    }

	public SystemMessage deserialise(Type type, ByteBuffer buf, Object result) throws ClassNotFoundException, IOException {
         switch(type){
            case PAXOS_MSG:
                return PaxosMessage.readFromBuffer(buf);
			case RT_MSG:
				return new RTMessage(buf);
            default:
                log.severe("Received msg for unknown msg type");
                return null;
        }
    }

}
