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
package navigators.smart.paxosatwar;

import navigators.smart.communication.ServerCommunicationSystem;
import navigators.smart.consensus.ConsensusService;
import navigators.smart.consensus.ConsensusServiceFactory;
import navigators.smart.paxosatwar.executionmanager.ExecutionManager;
import navigators.smart.paxosatwar.executionmanager.LeaderModule;
import navigators.smart.paxosatwar.executionmanager.ProofVerifier;
import navigators.smart.paxosatwar.messages.MessageFactory;
import navigators.smart.paxosatwar.messages.PaWMessageHandler;
import navigators.smart.paxosatwar.requesthandler.RequestHandler;
import navigators.smart.paxosatwar.roles.Acceptor;
import navigators.smart.paxosatwar.roles.Proposer;
import navigators.smart.tom.core.TOMLayer;
import navigators.smart.tom.core.messages.SystemMessage;
import navigators.smart.tom.util.TOMConfiguration;

/**
 * This class creates an instance of the Paxos at War algorithm specified
 * in the respective paper with some modifications.
 * 
 * @author Christian Spann 
 */
public class PaxosAtWarServiceFactory implements ConsensusServiceFactory{
    
    /** Holds the configuration */
    private final TOMConfiguration conf;

    /** Holds a link to the server communication system*/
    private final ServerCommunicationSystem cs;

    /**
     * Creates a new Instance with the given Configuration and communication system
     * @param cs The communicationsystem
     * @param conf The configuration
     */
    public PaxosAtWarServiceFactory(ServerCommunicationSystem cs, TOMConfiguration conf){
        this.cs = cs;
        this.conf = conf;
    }

    @SuppressWarnings("boxing")
    public ConsensusService newInstance(TOMLayer tom) {

         // Get group of replicas
        Integer[] group = new Integer[conf.getN()];
        for (int i = 0; i < group.length; i++) {
            group[i] = i;
        }

        int me = conf.getProcessId(); // this process ID

        if (me >= group.length) {
            throw new RuntimeException("I'm not an acceptor!");
        }
         // Init the PaW Service and al its components
        MessageFactory messageFactory = new MessageFactory(me);
        ProofVerifier proofVerifier = new ProofVerifier(conf);
        //init leaderhandling
        LeaderModule lm = new LeaderModule(conf.getN(),me);

        Acceptor acceptor = new Acceptor(cs, messageFactory, proofVerifier, lm, conf,tom);
        Proposer proposer = new Proposer(cs, messageFactory, proofVerifier, conf);

        ExecutionManager manager = new ExecutionManager(acceptor, proposer,
                group, conf.getF(), me, conf.getFreezeInitialTimeout(),tom);
		lm.setExecManager(manager);
        RequestHandler req = new RequestHandler(cs, manager, lm, proofVerifier, conf,tom);
        req.start();
        //init message handling threads
        //set backlinks
        acceptor.setManager(manager);
        proposer.setManager(manager);
        manager.setRequestHandler(req);

        PaWMessageHandler<?> msghandler = new PaWMessageHandler<byte[]>(acceptor, proposer, req);
        //create service object that implements ConsensusService interface
        ConsensusService service = new PaxosAtWarService(lm, manager,msghandler,conf,tom);
        cs.addMessageHandler(SystemMessage.Type.PAXOS_MSG, msghandler);
		cs.addMessageHandler(SystemMessage.Type.RT_MSG, msghandler);
        return service;
    }

}
