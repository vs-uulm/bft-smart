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

package navigators.smart.tom;

import java.util.Random;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import navigators.smart.communication.client.CommunicationSystemClientSide;
import navigators.smart.communication.client.ReplyReceiver;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.TOMConfiguration;


/**
 * This class is used to
 * multicast data to a group of replicas
 */
public abstract class TOMSender implements ReplyReceiver {

    private Integer me; // process id
    private Integer[] group; // group of replicas
    private int sequence = 0; // sequence number
    private CommunicationSystemClientSide cs; // Client side comunication system
    private Lock lock = new ReentrantLock(); // lock to manage concurrent access to this object by other threads
    private boolean useSignatures = false;
    private Random rnd = new Random();

    /**
     * This method initializes the object
     * TODO: Perguntar se este metodo n pode antes ser protected (compila como protected, mas mesmo assim...)
     *
     * @param cs Client side comunication system
     * @param conf Client side comunication system configuration
     * @param sequence Initial sequence number for data multicast
     *
     * TODO: ver o q é isto de "client side comunication system"
     */
    @SuppressWarnings("hiding")
    public void init(CommunicationSystemClientSide cs, TOMConfiguration conf, int sequence) {
        this.init(cs, conf);
        this.sequence = sequence;
        this.useSignatures = conf.getUseSignatures()==1?true:false;
    }

    /**
     * This method initializes the object
     * TODO: Perguntar se este metodo n pode antes ser protected (compila como protected, mas mesmo assim...)
     *
     * @param cs Client side comunication system
     * @param conf Total order messaging configuration
     */
    @SuppressWarnings({ "boxing", "hiding" })
    public void init(CommunicationSystemClientSide cs, TOMConfiguration conf) {
        this.cs = cs;
        this.cs.setReplyReceiver(this); // This object itself shall be a reply receiver
        this.me = conf.getProcessId();

        this.group = new Integer[conf.getN()];
        for (int i = 0; i < group.length; i++) {
            group[i] = i;
        }
        this.useSignatures = conf.getUseSignatures()==1?true:false;
    }

    // Get next sequence number to a soon to be multicasted message
    private int getNextSequenceNumber() {
        lock.lock();
        int id = sequence++;
        lock.unlock();
        return id;

    }

    /**
     * Get last sequence number of an already multicasted message
     *
     * @return Last sequence number of an already multicasted message
     *
     * TODO: Isto nao devia ter tambem um semaforo a controlar a leitura deste atributo?
     */
    public int getLastSequenceNumber() {
        return sequence - 1;
    }

    /**
     * Multicast data to the group of replicas
     *
     * @param m Data to be multicast
     */
    public void doTOMulticast(byte[] m) {
        cs.send(useSignatures, group, new TOMMessage(me, getNextSequenceNumber(), m));
    }

    /**
     * Multicast data to the group of replicas
     *
     * @param m Data to be multicast
     * @param readOnly it is a readonly request
     */
    public void doTOMulticast(byte[] m, boolean readOnly) {
        cs.send(useSignatures, group, new TOMMessage(me, getNextSequenceNumber(), m, readOnly));
    }
    
    /**
     * Unicast data to a random member of the group
     *
     * @param m Data to be multicast
     * @param readOnly it is a readonly request
     */
    @SuppressWarnings("boxing")
    public void doRandomTOUnicast(byte[] m, boolean readOnly) {
    	cs.send(useSignatures, rnd.nextInt(group.length), new TOMMessage(me, getNextSequenceNumber(), m, readOnly));
    }
    
    /**
     * Unicast data to a random member of the group
     *
     * @param m Data to be multicast
     * @param readOnly it is a readonly request
     */
    @SuppressWarnings("boxing")
    public void doRandomTOUnicast(TOMMessage m) {
    	cs.send(useSignatures, rnd.nextInt(group.length), m);
    }
    /**
     * Unicast data to a member of the group
     *
     * @param m Data to be multicast
     * @param recv The receiver of the message
     * @param readOnly it is a readonly request
     */
    @SuppressWarnings("boxing")
    public void doTOUnicast(byte[] m, int receiver, boolean readOnly) {
    	cs.send(useSignatures, receiver, new TOMMessage(me, getNextSequenceNumber(), m, readOnly));
    }
    
    /**
     * Unicast data to a member of the group
     *
     * @param m Data to be multicast
     * @param recv The receiver of the message
     * @param readOnly it is a readonly request
     */
    @SuppressWarnings("boxing")
    public void doTOUnicast(int receiver, TOMMessage m) {
    	cs.send(useSignatures, receiver, m);
    }

    /**
     * Multicast a TOMMessage to the group of replicas
     *
     * @param m Data to be multicast
     */
    public void TOMulticast(TOMMessage sm) {
        cs.send(useSignatures, group, sm);
    }

    /**
     * Create TOMMessage and sign it
     *
     * @param m Data to be included in TOMMessage
     *
     * @return TOMMessage with serializedMsg and serializedMsgSignature fields filled
     */
    public TOMMessage createTOMMsg(byte[] m) {
        TOMMessage tm = new TOMMessage(me, getNextSequenceNumber(), m);
        if(useSignatures){
        	cs.sign(tm);
        }
        return tm;
    }
}
