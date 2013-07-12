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
 */package navigators.smart.tom.util;

import navigators.smart.communication.ServerCommunicationSystem;
import navigators.smart.consensus.ConsensusService;
import navigators.smart.tom.core.TOMLayer;

/**
 * Print information about the replica when it is shutdown.
 *
 */
@SuppressWarnings("unused")
public class ShutdownThread extends Thread {

    private ServerCommunicationSystem scs;
//    private LeaderModule lm;
//    private Acceptor acceptor;
//    private ExecutionManager manager;
    ConsensusService conSrv;
	private TOMLayer tomLayer;

    public ShutdownThread(ServerCommunicationSystem scs, ConsensusService consensus, TOMLayer tomLayer) {
        this.scs = scs;
        this.conSrv = consensus;
        this.tomLayer = tomLayer;
    }

    @Override
    public void run() {
        synchronized(Statistics.stats){
            System.err.println("---------- DEBUG INFO ----------");
            System.err.println("Current time: " + System.currentTimeMillis());
            System.err.println(conSrv);
            Statistics.stats.close();
    //        Round r = manager.getExecution(tomLayer.getLastExec()).getLastRound();
    //        System.err.println("Last executed leader: " +
    //                lm.getLeader(r.getExecution().getId(),r.getNumber()));
    //        System.err.println("State of the last executed round: "+r.toString());
    //        if(tomLayer.getInExec() != -1) {
    //            Round r2 = manager.getExecution(tomLayer.getInExec()).getLastRound();
    //            if(r2 != null) {
    //                System.err.println("State of the round in execution: "+r2.toString());
    //            }
    //        }
    //        System.err.println("Execution manager: "+ tomLayer.execManager);
    //        System.err.println("Server communication system queues: "+
    //                scs.toString());
            //System.err.println("Pending requests: " +
            //        tomLayer.clientsManager.getPendingRequests());
    //        System.err.println("Requests timers: " + tomLayer.requestsTimer);
            System.err.println("---------- ---------- ----------");
        }
    }
}
