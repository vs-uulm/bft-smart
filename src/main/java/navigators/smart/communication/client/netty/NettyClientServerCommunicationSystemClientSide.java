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

import java.net.ConnectException;import java.nio.channels.ClosedChannelException;import java.security.InvalidKeyException;import java.security.NoSuchAlgorithmException;import java.security.PrivateKey;import java.security.Signature;import java.security.SignatureException;import java.security.spec.InvalidKeySpecException;import java.util.Hashtable;import java.util.List;import java.util.concurrent.ExecutorService;import java.util.concurrent.Executors;import java.util.concurrent.ScheduledExecutorService;import java.util.concurrent.TimeUnit;import java.util.concurrent.locks.ReentrantLock;import java.util.concurrent.locks.ReentrantReadWriteLock;import java.util.logging.Level;import java.util.logging.Logger;import javax.crypto.Mac;import javax.crypto.SecretKey;import javax.crypto.SecretKeyFactory;import javax.crypto.spec.PBEKeySpec;import navigators.smart.communication.client.CommunicationSystemClientSide;import navigators.smart.communication.client.ReplyReceiver;import navigators.smart.tom.core.messages.TOMMessage;import navigators.smart.tom.util.TOMConfiguration;import navigators.smart.tom.util.TOMUtil;import org.jboss.netty.bootstrap.ClientBootstrap;import org.jboss.netty.buffer.ChannelBuffer;import org.jboss.netty.buffer.ChannelBuffers;import org.jboss.netty.channel.Channel;import org.jboss.netty.channel.ChannelFuture;import org.jboss.netty.channel.ChannelHandler;import org.jboss.netty.channel.ChannelHandlerContext;import org.jboss.netty.channel.ChannelStateEvent;import org.jboss.netty.channel.ExceptionEvent;import org.jboss.netty.channel.MessageEvent;import org.jboss.netty.channel.SimpleChannelUpstreamHandler;import org.jboss.netty.channel.group.ChannelGroup;import org.jboss.netty.channel.group.ChannelGroupFuture;import org.jboss.netty.channel.group.DefaultChannelGroup;import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;/**
 *
 * @author Paulo
 */
@ChannelHandler.Sharable
public class NettyClientServerCommunicationSystemClientSide extends SimpleChannelUpstreamHandler implements CommunicationSystemClientSide {

    private static final Logger log = Logger.getLogger(NettyClientServerCommunicationSystemClientSide.class.getCanonicalName());

//    private static final int MAGIC = 59;
//    private static final int CONNECT_TIMEOUT = 3000;
    private static final String PASSWORD = "newcs";
    //private static final int BENCHMARK_PERIOD = 10000;
    protected ReplyReceiver trr;
    private TOMConfiguration conf;
    private Hashtable<Integer, NettyClientServerSession> sessionTable;
    private ReentrantReadWriteLock rl;
    private SecretKey authKey;
    //the signature engine used in the system
    private Signature signatureEngine;
    //private Storage st;
//    private int count = 0;
    private int signatureLength;           private ChannelGroup allservers = new DefaultChannelGroup();    ClientBootstrap bootstrap;        private ScheduledExecutorService reconnectionhandler = Executors.newScheduledThreadPool(2);
    
    private TOMUtil tomutil;        private volatile boolean running = true;

    @SuppressWarnings("boxing")
    public NettyClientServerCommunicationSystemClientSide(TOMConfiguration conf) {
        try {
            SecretKeyFactory fac = SecretKeyFactory.getInstance("PBEWithMD5AndDES");
            PBEKeySpec spec = new PBEKeySpec(PASSWORD.toCharArray());
            authKey = fac.generateSecret(spec);
            tomutil = new TOMUtil();
            this.conf = conf;
            this.sessionTable = new Hashtable<Integer, NettyClientServerSession>();
            //this.st = new Storage(BENCHMARK_PERIOD);
            this.rl = new ReentrantReadWriteLock();
            Mac macDummy = Mac.getInstance(conf.getHmacAlgorithm());
            signatureLength = tomutil.getSignatureSize();
            for (int i = 0; i < conf.getN(); i++) {
                try {                	// Configure the client.
                                	ExecutorService	pool = Executors.newCachedThreadPool();                    bootstrap = new ClientBootstrap(
                            new NioClientSocketChannelFactory(
                            pool,
                            pool));

                    bootstrap.setOption("tcpNoDelay", true);
                    bootstrap.setOption("keepAlive", true);

                    // Set up the default event pipeline.
                    bootstrap.setPipelineFactory(                    		new NettyClientPipelineFactory(this, true,                     				sessionTable, authKey,                     				macDummy.getMacLength(), conf, rl,                     				signatureLength, new ReentrantLock()));                    
                    // Start the connection attempt.
                    ChannelFuture future = null;
                    log.fine("Connecting to replica " + i + " at "                     + conf.getRemoteAddress(i));
                    do {
                        if(future != null){
                            log.warning("Failed to connect to replica " + i                             		+ " at " + conf.getRemoteAddress(i)+" - trying again.");
                        }
                        future = bootstrap.connect(conf.getRemoteAddress(i));
                        future.awaitUninterruptibly();

                    } while (!future.isSuccess());
                    log.info("Connected to replica " + i + " at " + conf.getRemoteAddress(i));

                    //creates MAC stuff
                    Mac macSend = Mac.getInstance(conf.getHmacAlgorithm());
                    macSend.init(authKey);
                    Mac macReceive = Mac.getInstance(conf.getHmacAlgorithm());
                    macReceive.init(authKey);
                    NettyClientServerSession cs = new NettyClientServerSession(future.getChannel(), macSend, macReceive, i, TOMConfiguration.getRSAPublicKey(i), new ReentrantLock());
                    sessionTable.put(i, cs);

                } catch (InvalidKeyException ex) {
                    log.log(Level.SEVERE, null, ex);
                }
            }
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

        if (!(e.getCause() instanceof ClosedChannelException) && !(e.getCause() instanceof ConnectException)) {
            e.getCause().printStackTrace();
        }
    }

    @Override
    public void messageReceived(
            ChannelHandlerContext ctx, MessageEvent e) {
        //System.out.println("MsgReceived");
        TOMMessage sm = (TOMMessage) e.getMessage();

        //delivers message to replyReceived callback
        trr.replyReceived(sm);
    }

    @Override
    public void channelConnected(
            ChannelHandlerContext ctx, ChannelStateEvent e) {
    	Channel ch = e.getChannel();    	allservers.add(ch);
    	ChannelBuffer id = ChannelBuffers.buffer(4);
    	id.writeInt(conf.getProcessId().intValue());
    	ChannelFuture f = ch.write(id);
    	f.awaitUninterruptibly();
    }

    @Override
    public void channelClosed(
            final ChannelHandlerContext ctx, ChannelStateEvent e) {    	    	if(running){			reconnectionhandler.schedule(new Runnable() {				public void run() {					// Reconnect to this host					ChannelFuture f = bootstrap.connect(ctx.getChannel()							.getRemoteAddress());						f.awaitUninterruptibly();					if (!f.isSuccess()&&running) {						//reschedule if failure						reconnectionhandler.schedule(this, 5, TimeUnit.SECONDS);					}				}			}, 5, TimeUnit.SECONDS);    	}
        //System.out.println("Channel closed");
//        rl.writeLock().lock();
//        //tries to reconnect the channel
//        Enumeration<NettyClientServerSession> sessionElements = sessionTable.elements();
//        while (sessionElements.hasMoreElements()) {
//            NettyClientServerSession ncss = sessionElements.nextElement();
//            if (ncss.getChannel() == ctx.getChannel()) {
//                try {
//                    Mac macDummy = Mac.getInstance(conf.getHmacAlgorithm());
//                   ctx.getChannel().co
//                    // Start the connection attempt.
//                    ChannelFuture future = bootstrap.connect(conf.getRemoteAddress(ncss.getReplicaId().intValue()));
//                    //creates MAC stuff
//                    Mac macSend = ncss.getMacSend();
//                    Mac macReceive = ncss.getMacReceive();
//                    @SuppressWarnings("boxing")
//                    NettyClientServerSession cs = new NettyClientServerSession(future.getChannel(), macSend, macReceive, ncss.getReplicaId(), TOMConfiguration.getRSAPublicKey(ncss.getReplicaId()), new ReentrantLock());
//                    sessionTable.remove(ncss.getReplicaId());
//                    sessionTable.put(ncss.getReplicaId(), cs);
//                    //System.out.println("RE-Connecting to replica "+ncss.getReplicaId()+" at " + conf.getRemoteAddress(ncss.getReplicaId()));
//                } catch (NoSuchAlgorithmException ex) {
//                    log.log(Level.SEVERE, null, ex);
//                }
//            }
//
//        }

        //closes all other channels to avoid messages being sent to only a subset of the replicas
		/*
		 * Enumeration sessionElements = sessionTable.elements(); while
		 * (sessionElements.hasMoreElements()){ ((NettyClientServerSession)
		 * sessionElements.nextElement()).getChannel().close(); }
		 */
//        rl.writeLock().unlock();
    }

    public void setReplyReceiver(ReplyReceiver trr) {
        this.trr = trr;
    }

    public void send(boolean sign, List<Integer> targets, TOMMessage sm) {
    	if(sign){
    		//checks if msg is serialized and signs it then
    		sign(sm);
    	} 

        for (Integer t:targets) {
            writeToChannel(sm,t);
        }
    }
    public void send(boolean sign, Integer target, TOMMessage sm) {
    	if(sign){
    		//checks if msg is serialized and signs it then
    		sign(sm);
    	} 
    	writeToChannel(sm,target);
    }

    private void writeToChannel(TOMMessage sm, Integer destination) {
    	sm.destination = destination;
        rl.readLock().lock();
        Channel channel = sessionTable.get(destination).getChannel();
        rl.readLock().unlock();
        if (channel.isConnected()) {
            channel.write(sm);
        } else {
            //System.out.println("WARNING: channel is not connected");
        }
		
	}

    public void sign(TOMMessage sm) {
        //produce signature        
        byte[] data2 = signMessage(TOMConfiguration.getRSAPrivateKey(), sm.getBytes());
        sm.signed = true;
        sm.serializedMessageSignature = data2;
    }

    public byte[] signMessage(PrivateKey key, byte[] message) {
        try {
            if (signatureEngine == null) {
                signatureEngine = Signature.getInstance("SHA1withRSA");
            }
            byte[] result = null;
            
            signatureEngine.initSign(key);
            signatureEngine.update(message);
            result = signatureEngine.sign();
            
            //st.store(System.nanoTime() - startTime);
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }        public void shutdown(){    	running = false;    	reconnectionhandler.shutdownNow();    	ChannelGroupFuture f = allservers.close();    	f.awaitUninterruptibly();		bootstrap.releaseExternalResources();		log.info("Released ext ressources");    }
}
