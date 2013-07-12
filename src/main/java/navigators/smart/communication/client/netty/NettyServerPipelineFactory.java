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

import static org.jboss.netty.channel.Channels.pipeline;

import java.util.Hashtable;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.SecretKey;
import navigators.smart.communication.server.MessageVerifierFactory.VerifierType;

import navigators.smart.tom.util.TOMConfiguration;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;


/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev: 643 $, $Date: 2009/09/08 00:11:57 $
 */
public class NettyServerPipelineFactory implements ChannelPipelineFactory {

    private static Logger log = Logger.getLogger(NettyClientPipelineFactory.class.getCanonicalName());

    NettyClientServerCommunicationSystemServerSide ncs;
    boolean isClient;
    Hashtable<Integer,NettyClientServerSession> sessionTable;
    SecretKey authKey;
    int macLength;
    int signatureLength;
    TOMConfiguration conf;
    ReentrantReadWriteLock rl;
    ReentrantLock lock;

    public NettyServerPipelineFactory(NettyClientServerCommunicationSystemServerSide ncs, boolean isClient, Hashtable<Integer,NettyClientServerSession> sessionTable, SecretKey authKey, int macLength, TOMConfiguration conf, ReentrantReadWriteLock rl, int signatureLength, ReentrantLock lock) {
        this.ncs = ncs;
        this.isClient = isClient;
        this.sessionTable = sessionTable;
        this.authKey = authKey;
        this.macLength = macLength;
        this.signatureLength = signatureLength;
        this.conf = conf;
        this.rl = rl;
        this.lock = lock;
    }


    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline p = pipeline();
        boolean ptpverification = conf.getVerifierType().equals(VerifierType.PTPVerifier);
        p.addLast("decoder", new NettyTOMMessageDecoder(isClient, sessionTable, authKey, macLength,conf,rl,signatureLength,ptpverification));
        p.addLast("encoder", new NettyTOMMessageEncoder( sessionTable,rl,signatureLength, ptpverification));
        p.addLast("handler", ncs);
        
        return p;
    }
}
