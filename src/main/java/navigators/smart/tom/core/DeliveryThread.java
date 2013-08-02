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
 */package navigators.smart.tom.core;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.consensus.Consensus;
import navigators.smart.consensus.ConsensusService;
import navigators.smart.statemanagment.TransferableState;
import navigators.smart.tom.TOMReceiver;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.BatchReader;
import navigators.smart.tom.util.TOMConfiguration;


/**
 * This class implements a thread which will deliver totally ordered requests to the application
 * @author Christian Spann
 */
public class DeliveryThread extends Thread {
	
    private static final Logger log = Logger.getLogger(DeliveryThread.class.getCanonicalName());

    private LinkedBlockingQueue<Consensus<TOMMessage[]>> decided = new LinkedBlockingQueue<Consensus<TOMMessage[]>>(); // decided consensus
    private TOMLayer tomLayer; // TOM layer
    private TOMConfiguration conf;

    private final TOMReceiver receiver;
    
    private ConsensusService consensusservice;        private volatile boolean running = true;

    /**
     * Creates a new instance of DeliveryThread
     * @param tomLayer TOM layer
     * @param recv The receiver of the decided requests
     * @param conf TOM configuration
     */
    public DeliveryThread(TOMLayer tomLayer, TOMReceiver recv, TOMConfiguration conf) {
        super("Delivery Thread "+ conf.getProcessId());
        this.tomLayer = tomLayer;
        this.conf = conf;
        this.receiver = recv;
    }

    /**
     * Invoked by the TOM layer, to deliver a decide consensus
     * @param cons MeasuringConsensus established as being decided
     */
    public void delivery(Consensus<TOMMessage[]> cons) {
        try {
            decided.put(cons);
            if(log.isLoggable(Level.FINER))
                log.finer(" Consensus " + cons.getId() + " finished. decided size=" + decided.size());
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    /** ISTO E CODIGO DO JOAO, PARA TRATAR DA TRANSFERENCIA DE ESTADO */
    
    private ReentrantLock deliverLock = new ReentrantLock();

    /**
     * Updates the State of this replica.
     *
     * TODO This part should be moved to the StateManager class
     *
     * @param transferredState The state we got from the other replicas
     */
    public void updateState(TransferableState transferredState) {    	    	    	//wake up if we are waiting    	this.interrupt();

        deliverLock.lock();

        consensusservice.startDeliverState();

        Long lastCheckpointEid = transferredState.lastCheckpointEid;
        Long lastEid = transferredState.lastEid;

        if(log.isLoggable(Level.FINE))
            log.fine("I'm going to update myself from EID " + lastCheckpointEid + " to EID " + lastEid);

        receiver.setState(transferredState.state);

        for (long eid = lastCheckpointEid.longValue() + 1; eid <= lastEid.longValue(); eid++) {

            try {
                byte[] batch = transferredState.getMessageBatch(eid).batch; // take a batch

                // obtain an array of requests from the taken consensus
                BatchReader batchReader = new BatchReader(batch, conf.getUseSignatures()==1,conf.getSignatureSize());

                if(log.isLoggable(Level.FINEST))
                    log.finest("interpreting and verifying batched requests.");

                TOMMessage[] requests = batchReader.deserialiseRequests();

                //deliver the request to the application (receiver)
                deliver(requests);

            } catch (Exception e) {
                e.printStackTrace(System.out);
            }

        }

        decided.clear();

        consensusservice.deliverState(transferredState);

        if(log.isLoggable(Level.FINE))
            log.fine("All finished from " + lastCheckpointEid + " to " + lastEid);

        deliverLock.unlock();
    }
    

    /**
     * This is the code for the thread. It delivers decided consensus to the 
     * TOMRequestReceiver object (which is the application)
     *
     */
    @SuppressWarnings("boxing")
    @Override
    public void run() {

        long startTime;
        while (running) {
            tomLayer.checkAndWaitForState();
            try {
                deliverLock.lock();
                Consensus<TOMMessage[]> cons = decided.poll(1500, TimeUnit.MILLISECONDS); // take a decided consensus
                if (cons == null) {
                    continue;
                }

                if(log.isLoggable(Level.FINER))
                    log.finer("" + cons + " was delivered.");
                startTime = System.currentTimeMillis();

                //TODO: avoid the case in which the received valid proposal is
                //different from the decided value

                TOMMessage[] requests = cons.getDeserializedDecision();

                if (requests == null) {
                    if(log.isLoggable(Level.FINER))
                        log.finer("interpreting and verifying batched requests.");

                    // obtain an array of requests from the taken consensus
                    BatchReader batchReader = new BatchReader(cons.getDecision(), conf.getUseSignatures()==1,conf.getSignatureSize());
                    requests = batchReader.deserialiseRequests();

                } else {
                    if(log.isLoggable(Level.FINER))
                        log.finer("using cached requests from the propose.");
                }

                deliver(requests);

                if(log.isLoggable(Level.FINER))
                    log.finer("I just delivered the batch of EID " + cons.getId());

                
                
                consensusservice.deliveryFinished(cons);
                if(log.isLoggable(Level.FINER))
                        log.finer("(DeliveryThread.run) All finished for " + cons.getId() + ", took " + (System.currentTimeMillis() - startTime));
            } catch (Exception e) {
                e.printStackTrace(System.out);
            } finally {
                deliverLock.unlock();
            }
        }
    }

    /**
     * Delivers the given desierialised Messagebatch to the clientsmanager,
     * the consensusservice and finally the receiver
     * @param requests The Batch with the deserialised messages
     */
    private void deliver(TOMMessage[] requests) {
        for (int i = 0; i < requests.length; i++) {
            tomLayer.clientsManager.requestOrdered(requests[i]);
            consensusservice.notifyRequestDecided(requests[i]);
            receiver.receiveOrderedMessage(requests[i]);
        }
    }

    public void setConsensusservice(ConsensusService consensusservice) {
        this.consensusservice = consensusservice;
    }	public void shutdown() {		running = false;		this.interrupt();	}
}
