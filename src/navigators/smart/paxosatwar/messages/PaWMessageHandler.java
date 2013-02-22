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
 * @author Christian Spann <christian.spann at uni-ulm.de>
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
