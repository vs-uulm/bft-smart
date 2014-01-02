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
 */package navigators.smart.communication.server;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import navigators.smart.communication.MessageHandler;
import navigators.smart.tom.core.messages.SystemMessage;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.Storage;
import navigators.smart.tom.util.TOMConfiguration;


public class SimpleBenchmark {

    /**
     * @param args the command line arguments
     */
    @SuppressWarnings("rawtypes")
	public static void main(String[] args) throws Exception {

        TOMConfiguration conf = new TOMConfiguration(Integer.parseInt(args[0]),"./config");
        LinkedBlockingQueue<SystemMessage> inQueue = new LinkedBlockingQueue<SystemMessage>();
        Map<SystemMessage.Type,MessageHandler> msgHandlers = new HashMap<SystemMessage.Type, MessageHandler>();

        ServersCommunicationLayer scl = new ServersCommunicationLayer(conf, inQueue,msgHandlers, new HMacVerifierFactory(), null);

        int id = conf.getProcessId();
        int n = conf.getN();

        Integer[] targets = new Integer[n-1];

        System.out.println("n = "+n);

        for (int i=1; i<n; i++) {
            targets[i-1] = i;
        }

        int iteractions = Integer.parseInt(args[1]);

        int warmup = iteractions/2;
        int test = iteractions/2;

        for(int i=0; i<warmup; i++) {
            String msg = "m"+i;

            //System.out.println("sending "+msg);

            if(id == 0) {
                long time = System.nanoTime();

                scl.send(targets, new TOMMessage(id,i,msg.getBytes()));
                int rec = 0;

                while(rec < n-1) {
                    inQueue.take();
                    rec++;
                }

                //System.out.println();
                System.out.println("Roundtrip "+((System.nanoTime()-time)/1000.0)+" us");
            } else {
                TOMMessage m = (TOMMessage) inQueue.take();
                scl.send(new Integer[]{m.getSender()}, new TOMMessage(id,i,m.getContent()));
            }
        }

        System.out.println("Beginning the real test with "+test+" roundtrips");
        Storage st = new Storage(test);

        for(int i=0; i<test; i++) {
            String msg = "m"+i;
            if(id == 0) {
                long time = System.nanoTime();

                scl.send(targets, new TOMMessage(id,i,msg.getBytes()));
                int rec = 0;

                while(rec < n-1) {
                    inQueue.take();
                    rec++;
                }

                st.storeDuration(System.nanoTime()-time);
            } else {
                TOMMessage m = (TOMMessage) inQueue.take();
                scl.send(new Integer[]{m.getSender()}, new TOMMessage(id,i,m.getContent()));
            }
        }

        System.out.println("Average time for "+test+" executions (-10%) = "+st.getAverage(true)/1000+ " us ");
        System.out.println("Standard desviation for "+test+" executions (-10%) = "+st.getDP(true)/1000 + " us ");
        System.out.println("Maximum time for "+test+" executions (-10%) = "+st.getMax(true)/1000+ " us ");
        System.out.println("Average time for "+test+" executions (all samples) = "+st.getAverage(false)/1000+ " us ");
        System.out.println("Standard desviation for "+test+" executions (all samples) = "+st.getDP(false)/1000 + " us ");
        System.out.println("Maximum time for "+test+" executions (all samples) = "+st.getMax(false)/1000+ " us ");

        //scl.shutdown();
    }
}

