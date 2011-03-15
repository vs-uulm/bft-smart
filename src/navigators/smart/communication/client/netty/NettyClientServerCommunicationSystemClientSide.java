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

import java.net.ConnectException;
import java.nio.channels.ClosedChannelException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import navigators.smart.communication.client.CommunicationSystemClientSide;
import navigators.smart.communication.client.ReplyReceiver;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.TOMConfiguration;
import navigators.smart.tom.util.TOMUtil;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;


/**
 *
 * @author Paulo
 */
@ChannelPipelineCoverage("all")
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
    private int signatureLength;    
    
    private TOMUtil tomutil;

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
                try {
                    // Configure the client.
                    ClientBootstrap bootstrap = new ClientBootstrap(
                            new NioClientSocketChannelFactory(
                            Executors.newCachedThreadPool(),
                            Executors.newCachedThreadPool()));

                    bootstrap.setOption("tcpNoDelay", true);
                    bootstrap.setOption("keepAlive", true);

                    // Set up the default event pipeline.
                    bootstrap.setPipelineFactory(new NettyClientPipelineFactory(this, true, sessionTable, authKey, macDummy.getMacLength(), conf, rl, signatureLength, new ReentrantLock()));

                    // Start the connection attempt.
                    ChannelFuture future = null;
                    do {
                        if(future != null){
                            log.warning("Failed to connect to replica " + i + " at " + conf.getRemoteAddress(i)+" - trying again.");
                        }
                        log.info("Connecting to replica " + i + " at " + conf.getRemoteAddress(i));
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

                    System.out.println("Connecting to replica " + i + " at " + conf.getRemoteAddress(i));
                    future.awaitUninterruptibly();


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
    	Channel ch = e.getChannel();
    	ChannelBuffer id = ChannelBuffers.buffer(4);
    	id.writeInt(conf.getProcessId().intValue());
    	ChannelFuture f = ch.write(id);
    	f.awaitUninterruptibly();
        System.out.println("Channel connected");
    }

    @Override
    public void channelClosed(
            ChannelHandlerContext ctx, ChannelStateEvent e) {
        try {
            //sleeps 10 seconds before trying to reconnect
            Thread.sleep(10000);
        } catch (InterruptedException ex) {
            log.log(Level.SEVERE, null, ex);
        }
        //System.out.println("Channel closed");
        rl.writeLock().lock();
        //tries to reconnect the channel
        Enumeration<NettyClientServerSession> sessionElements = sessionTable.elements();
        while (sessionElements.hasMoreElements()) {
            NettyClientServerSession ncss = sessionElements.nextElement();
            if (ncss.getChannel() == ctx.getChannel()) {
                try {
                    Mac macDummy = Mac.getInstance(conf.getHmacAlgorithm());
                    // Configure the client.                    
                    ClientBootstrap bootstrap = new ClientBootstrap(new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));
                    // Set up the default event pipeline.
                    bootstrap.setPipelineFactory(new NettyClientPipelineFactory(this, true, sessionTable, authKey, macDummy.getMacLength(), conf, rl, tomutil.getSignatureSize(), new ReentrantLock()));
                    // Start the connection attempt.
                    ChannelFuture future = bootstrap.connect(conf.getRemoteAddress(ncss.getReplicaId().intValue()));
                    //creates MAC stuff
                    Mac macSend = ncss.getMacSend();
                    Mac macReceive = ncss.getMacReceive();
                    @SuppressWarnings("boxing")
                    NettyClientServerSession cs = new NettyClientServerSession(future.getChannel(), macSend, macReceive, ncss.getReplicaId(), TOMConfiguration.getRSAPublicKey(ncss.getReplicaId()), new ReentrantLock());
                    sessionTable.remove(ncss.getReplicaId());
                    sessionTable.put(ncss.getReplicaId(), cs);
                    //System.out.println("RE-Connecting to replica "+ncss.getReplicaId()+" at " + conf.getRemoteAddress(ncss.getReplicaId()));
                } catch (NoSuchAlgorithmException ex) {
                    log.log(Level.SEVERE, null, ex);
                }
            }

        }

        //closes all other channels to avoid messages being sent to only a subset of the replicas
		/*
		 * Enumeration sessionElements = sessionTable.elements(); while
		 * (sessionElements.hasMoreElements()){ ((NettyClientServerSession)
		 * sessionElements.nextElement()).getChannel().close(); }
		 */
        rl.writeLock().unlock();
    }

    public void setReplyReceiver(ReplyReceiver trr) {
        this.trr = trr;
    }

    public void send(boolean sign, Integer[] targets, TOMMessage sm) {
    	if(sign){
    		//checks if msg is serialized and signs it then
    		sign(sm);
    	} 

        for (int i = targets.length - 1; i >= 0; i--) {
            writeToChannel(sm,targets[i]);
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
    }
}
