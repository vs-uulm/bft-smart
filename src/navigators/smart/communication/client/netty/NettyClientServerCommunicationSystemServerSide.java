/**
 * Copyright (c) 2007-2009 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
 * 
 * This file is part of SMaRt.
 * 
 * SMaRt is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * SMaRt is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with SMaRt.  If not, see <http://www.gnu.org/licenses/>.
 */

package navigators.smart.communication.client.netty;

import java.net.InetSocketAddress;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import navigators.smart.communication.client.CommunicationSystemServerSide;
import navigators.smart.communication.client.RequestReceiver;
import navigators.smart.communication.server.MessageVerifierFactory.VerifierType;
import navigators.smart.communication.server.ServerConnection;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.TOMConfiguration;
import navigators.smart.tom.util.TOMUtil;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;


/**
 *
 * @author Paulo
 */
@ChannelPipelineCoverage("all")
public class NettyClientServerCommunicationSystemServerSide extends SimpleChannelHandler implements CommunicationSystemServerSide  {

    private final static Logger log = Logger.getLogger(NettyClientServerCommunicationSystemServerSide.class.getCanonicalName());
    
    private static final String PASSWORD = "newcs";    
    private RequestReceiver requestReceiver;
    private TOMConfiguration conf;
    private Hashtable<Integer,NettyClientServerSession> sessionTable;
    private ReentrantReadWriteLock rl;
    private SecretKey authKey;
    private Queue<TOMMessage> requestsReceived;
    private ReentrantLock lock = new ReentrantLock();
    private TOMUtil tomutil;

    public NettyClientServerCommunicationSystemServerSide(TOMConfiguration conf) {
        try {            
            SecretKeyFactory fac = SecretKeyFactory.getInstance("PBEWithMD5AndDES");
            PBEKeySpec spec = new PBEKeySpec(PASSWORD.toCharArray());
            authKey = fac.generateSecret(spec);
            
            requestsReceived = new ArrayDeque<TOMMessage>(conf.getCommBuffering());

            this.conf = conf;
            sessionTable = new Hashtable<Integer,NettyClientServerSession>();
            rl = new ReentrantReadWriteLock();

            tomutil = new TOMUtil();
            
            //Configure the server.
            /* Cached thread pool */
            ServerBootstrap bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));
            

            Mac macDummy = Mac.getInstance(conf.getHmacAlgorithm());
            
            bootstrap.setOption("tcpNoDelay", true);
            bootstrap.setOption("keepAlive", true);

            bootstrap.setOption("child.tcpNoDelay", true);
            bootstrap.setOption("child.keepAlive", true);

            //Set up the default event pipeline.
            bootstrap.setPipelineFactory(new NettyServerPipelineFactory(this,false,sessionTable,authKey,macDummy.getMacLength(),conf,rl,tomutil.getSignatureSize(), new ReentrantLock() ));

            //Bind and start to accept incoming connections.
            bootstrap.bind(new InetSocketAddress(conf.getHost(conf.getProcessId()),conf.getPort(conf.getProcessId())));

            System.out.println("#Bound to port " + conf.getPort(conf.getProcessId()));
            System.out.println("#myId " + conf.getProcessId());
            System.out.println("#n " + conf.getN());
            System.out.println("#f " + conf.getF());            
            System.out.println("#requestTimeout= " + conf.getRequestTimeout());
            System.out.println("#maxBatch= " + conf.getMaxBatchSize());
            System.out.println("#Using MACs = " + conf.getVerifierType().equals(VerifierType.PTPVerifier));
            System.out.println("#Using Signatures = " + conf.getUseSignatures());
        } catch (InvalidKeySpecException ex) {
            log.log(Level.SEVERE, null, ex);
        } catch (NoSuchAlgorithmException ex) {
            log.log(Level.SEVERE, null, ex);
        } catch (InvalidKeyException ex) {
            log.log(Level.SEVERE, null, ex);
        } catch (SignatureException ex) {
            log.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void exceptionCaught(
            ChannelHandlerContext ctx, ExceptionEvent e) {
        if (e.getCause().toString().equals("Connection reset by peer")) {
            log.info(e.toString());
        } else {
            log.warning(e.toString());
            e.getCause().printStackTrace();
        }
    }

    @Override
    public void messageReceived(
            ChannelHandlerContext ctx, MessageEvent e) {
        TOMMessage sm = (TOMMessage) e.getMessage();               
        if (conf.getCommBuffering()>0) {
            lock.lock();
            requestsReceived.add(sm);
            if (requestsReceived.size()>=conf.getCommBuffering()){
                while(!requestsReceived.isEmpty()){
                    requestReceiver.requestReceived(requestsReceived.remove());
                }
            }
            lock.unlock();
        }
        else {
            //delivers message to TOMLayer
            requestReceiver.requestReceived(sm);
        }
        }
        
     @Override
    public void channelConnected(
            ChannelHandlerContext ctx, ChannelStateEvent e) {
        if(log.isLoggable(Level.INFO))
            log.info("New Connection from "+ ctx.getChannel().getRemoteAddress()+", active clients="+sessionTable.size());
        
    }

    @Override
     public void channelClosed(
            ChannelHandlerContext ctx, ChannelStateEvent e) {
        rl.writeLock().lock();
        //removes session from sessionTable
        Set<Entry<Integer,NettyClientServerSession>> s = sessionTable.entrySet();
        Iterator<Entry<Integer,NettyClientServerSession>> i = s.iterator();
        while (i.hasNext()) {
        	Entry<Integer,NettyClientServerSession> m =  i.next();
            NettyClientServerSession value =  m.getValue();
            if (e.getChannel().equals(value.getChannel())) {
                Integer key = m.getKey();
                sessionTable.remove(key);
                System.out.println("#Removed client channel with ID= " + key);
                System.out.println("#active clients="+sessionTable.size());
                break;
            }
        }
        rl.writeLock().unlock();
        navigators.smart.tom.util.Logger.println("Session Closed, active clients="+sessionTable.size());
    }

    public void setRequestReceiver(RequestReceiver tl) {
        this.requestReceiver = tl;
    }

	public void send(Integer[] targets, TOMMessage sm) {

        //replies are not signed in the current JBP version
        sm.signed = false;
        //produce signature if necessary (never in the current version)
        if (sm.signed){
            tomutil.signMessage(TOMConfiguration.getRSAPrivateKey(), sm);
        }
        for (int i = 0; i < targets.length; i++) {
            rl.readLock().lock();
            NettyClientServerSession ncss = sessionTable.get(targets[i]);
           
            if (ncss!=null){
                Channel session = sessionTable.get(targets[i]).getChannel();
                rl.readLock().unlock();
                sm.destination = targets[i];                
                //send message
                session.write(sm);
            }
            else
                rl.readLock().unlock();
        }
    }
}
