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
 */package navigators.smart.tom;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;
import navigators.smart.communication.ForwardedMessageHandler;
import navigators.smart.communication.ServerCommunicationSystem;
import navigators.smart.communication.StateMessageHandler;
import navigators.smart.consensus.ConsensusService;
import navigators.smart.consensus.ConsensusServiceFactory;
import navigators.smart.tom.core.TOMLayer;
import navigators.smart.tom.core.messages.SystemMessage;
import navigators.smart.tom.util.ShutdownThread;
import navigators.smart.tom.util.TOMConfiguration;

/**
 * This class is used to
 * assemble a total order messaging layer
 *
 */
public abstract class TOMReceiver implements TOMRequestReceiver {

    private boolean tomStackCreated = false;

    protected final TOMConfiguration conf;

    protected static ServerCommunicationSystem cs = null; // Server side comunication system

    protected static TOMLayer tomlayer;        protected static ConsensusService service;

    public TOMReceiver( TOMConfiguration conf) throws IOException {
        this.conf = conf;
        init(conf);
    }


    /**
     * Can start a TOMReceiver without initialising the cs in order to do this later.
     * @param conf
     * @param initcs
     * @throws IOException
     */
    public TOMReceiver( TOMConfiguration conf, boolean initcs) throws IOException {
        this.conf = conf;
        if(initcs){
            init(conf);
        }
    }
    
    /**
     * This method initializes the object
     * 
     * TODO merge with ServiceReplica
     *
     * @param conf Total order messaging configuration
     * @throws IOException Is thrown when the init of the Com System fails
     */
    protected void init(TOMConfiguration conf) throws IOException {
        if (tomStackCreated) { // if this object was already initialized, don't do it again
            return;
        }

        cs = getCommunicationSystem();

        tomlayer = new TOMLayer( this, cs, conf);

        cs.addMessageHandler(SystemMessage.Type.FORWARDED,new ForwardedMessageHandler(tomlayer));
        cs.addMessageHandler(SystemMessage.Type.STATE_MSG,new StateMessageHandler(tomlayer));
        cs.setRequestReceiver(tomlayer);

        ConsensusServiceFactory factory = createFactory(cs, conf);

        service = factory.newInstance(tomlayer);
        tomlayer.setConsensusService(service); //set backlink
		//start comlayer when consensus is ready to serve
        cs.start();
		//start consensus to get things going
        service.start();
        Runtime.getRuntime().addShutdownHook(new ShutdownThread(cs,service,tomlayer));

        tomStackCreated = true;
    }

    protected ServerCommunicationSystem getCommunicationSystem() throws IOException{
        return new ServerCommunicationSystem(conf);
    }

    @SuppressWarnings("unchecked")
    protected ConsensusServiceFactory createFactory(ServerCommunicationSystem cs, TOMConfiguration conf){
        String algorithm = conf.getConsensusAlgorithmFactory();
        Class<ConsensusServiceFactory> serviceclass;
        try {
            serviceclass = (Class<ConsensusServiceFactory>) Class.forName(algorithm);
            Object[] initargs = new Object[2];
            initargs[0] = cs;
            initargs[1] = conf;
            ConsensusServiceFactory factory = (ConsensusServiceFactory) serviceclass.getConstructors()[0].newInstance(initargs);
            return factory;
        } catch (InstantiationException ex) {
            Logger.getLogger(TOMReceiver.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(TOMReceiver.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(TOMReceiver.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            Logger.getLogger(TOMReceiver.class.getName()).log(Level.SEVERE, "Failed to load ConsensusServiceFactory: "+algorithm, ex);
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(TOMReceiver.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
	
	public static String getCurrentServerComQueues(){
		return cs.getQueueLengths();
	}
	
	public static String getCurrentServerComQueuesNames(){
		return cs.getQueueNames();
	}
	
	public static String getCurrentPendingRequests(){
		return tomlayer.clientsManager.pendingreqs.toString();
	}		public void shutdown(){		cs.shutdown();		service.shutdown();		tomlayer.shutdown();	}
}

