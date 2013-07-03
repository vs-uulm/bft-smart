/**
 * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the
 *
 * @author tags
 *
 * This file is part of SMaRt.
 *
 * SMaRt is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * SMaRt is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with SMaRt. If not, see <http://www.gnu.org/licenses/>.
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
        acceptor.setRequesthandler(req);
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
