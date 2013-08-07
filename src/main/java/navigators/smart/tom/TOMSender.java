/*
 * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, 
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
package navigators.smart.tom;

import java.util.ArrayList;
import java.util.List;
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
    protected List<Integer> group; // group of replicas
    private int sequence = 0; // sequence number
    private CommunicationSystemClientSide cs; // Client side comunication system
    private Lock lock = new ReentrantLock(); // lock to manage concurrent access to this object by other threads
    private boolean useSignatures = false;

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
    public void init(CommunicationSystemClientSide cs, TOMConfiguration conf, int sequence) {
        this.init(cs, conf);
        this.sequence = sequence;
    }

    /**
     * This method initializes the object
     * TODO: Perguntar se este metodo n pode antes ser protected (compila como protected, mas mesmo assim...)
     *
     * @param cs Client side comunication system
     * @param conf Total order messaging configuration
     */
    @SuppressWarnings({ "boxing"})
    public void init(CommunicationSystemClientSide cs, TOMConfiguration conf) {
        this.cs = cs;
        this.cs.setReplyReceiver(this); // This object itself shall be a reply receiver
        this.me = conf.getProcessId();

        this.group = new ArrayList<Integer>(conf.getN());
        for (int i = 0; i < conf.getN(); i++) {
            group.add(i);
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
     * Multicast a TOMMessage to the group of replicas
     *
     * @param sm Data to be multicast
     */
    public void doTOMulticast(TOMMessage sm) {
        cs.send(useSignatures, group, sm);
    }
    /**
     * Multicast a TOMMessage to the group of replicas
     *
     * @param sm Data to be multicast
	 * @param targets Targets to send the message to
	 * 
     */
    public void doTOMulticast(TOMMessage sm, List<Integer> targets) {
        cs.send(useSignatures, targets, sm);
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
	 * Create TOMMessage and sign it
	 *
	 * @param m Data to be included in TOMMessage
	 *
	 * @return TOMMessage with serializedMsg and serializedMsgSignature fields
	 * filled
	 */
    public TOMMessage createTOMMsg(byte[] m) {
        TOMMessage tm = new TOMMessage(me, getNextSequenceNumber(), m);
        if(useSignatures){
        	cs.sign(tm);
        }
        return tm;
    }
    /**
     * Create TOMMessage and sign it
     *
     * @param m Data to be included in TOMMessage
	 * @param readonly  Is this request readonly
     *
     * @return TOMMessage with serializedMsg and serializedMsgSignature fields filled
     */
    public TOMMessage createTOMMsg(byte[] m, boolean readonly) {
        TOMMessage tm = new TOMMessage(me, getNextSequenceNumber(), m, readonly);
        if(useSignatures){
        	cs.sign(tm);
        }
        return tm;
    }

    public Integer getId() {
        return me;
    }
    
    public void shutdown() {
    	cs.shutdown();
    }
}
