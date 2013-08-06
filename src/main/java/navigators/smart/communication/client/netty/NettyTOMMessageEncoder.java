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
package navigators.smart.communication.client.netty;

import static org.jboss.netty.buffer.ChannelBuffers.buffer;import java.util.Hashtable;import java.util.concurrent.locks.ReentrantReadWriteLock;import java.util.logging.Level;import java.util.logging.Logger;import javax.crypto.Mac;import navigators.smart.tom.core.messages.TOMMessage;import navigators.smart.tom.util.Statistics;import org.jboss.netty.buffer.ChannelBuffer;import org.jboss.netty.channel.ChannelHandler;import org.jboss.netty.channel.ChannelHandlerContext;import org.jboss.netty.channel.Channels;import org.jboss.netty.channel.MessageEvent;import org.jboss.netty.channel.SimpleChannelHandler;@ChannelHandler.Sharable
public class NettyTOMMessageEncoder extends SimpleChannelHandler {
    
    private Hashtable<Integer,NettyClientServerSession> sessionTable;
    private int signatureLength;
    private ReentrantReadWriteLock rl;
    private boolean useMAC;
	
	private static final Logger log = Logger.getLogger(			NettyTOMMessageEncoder.class.getCanonicalName());

    public NettyTOMMessageEncoder(Hashtable<Integer,    		NettyClientServerSession> sessionTable, ReentrantReadWriteLock rl,     		int signatureLength, boolean useMAC){
        this.sessionTable = sessionTable;
        this.rl = rl;
        this.signatureLength = signatureLength;
        this.useMAC = useMAC;
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
    	ChannelBuffer buf;
    	Object msg = e.getMessage();
    	if(msg instanceof TOMMessage){
	        TOMMessage sm = (TOMMessage) msg;
	        byte[] msgData;
	        byte[] macData = null;
	        byte[] signatureData = null;
	
	        msgData = sm.getBytes();
	        if (sm.signed){
	            //signature was already produced before            
	            signatureData = sm.serializedMessageSignature;
	            if (signatureData.length != signatureLength)
	                System.out.println("WARNING: message signature has size "+signatureData.length+" and should have "+signatureLength);
	        }
	        
	        if (useMAC)
	            macData = produceMAC(sm.destination,msgData);
	        
	        int msglength = 1+msgData.length+(macData==null?0:macData.length)+(signatureData==null?0:signatureData.length);
	        
	        buf = buffer(4+msglength);
	        /* msg size */
	        buf.writeInt(msglength);
			if(log.isLoggable(Level.FINEST)){
				log.log(Level.FINEST,"Sending TOMMsg with Size {0} readonly: {1}",new Object[]{4+msglength,sm.isReadOnlyRequest()});
			}
			/* control byte indicating if the message is signed or not */
	        buf.writeByte(sm.signed==true?(byte)1:(byte)0);       
	        /* data to be sent */
	        buf.writeBytes(msgData);
	         /* MAC */
	        if (useMAC)
	            buf.writeBytes(macData);
	        /* signature */
	        if (signatureData != null)
	            buf.writeBytes(signatureData);
			//log to statistics module
			Statistics.stats.sentMsgToClient(sm.destination,sm);

    	} else {
    		buf = (ChannelBuffer)msg;
			if(log.isLoggable(Level.FINEST)){
				log.log(Level.FINEST,"Sending reply msg with Size {0}",new Object[]{buf.readableBytes()});
			}
    	}

        Channels.write(ctx, e.getFuture(), buf);
    }

    byte[] produceMAC(Integer id, byte[] data){
        rl.readLock().lock();
        Mac macsend = sessionTable.get(id).getMacSend();
        rl.readLock().unlock();
        synchronized(macsend){
            return macsend.doFinal(data);
        }
    }

}
