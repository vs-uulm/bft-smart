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
 */package navigators.smart.tom.demo;

import java.io.IOException;import java.util.logging.Level;import java.util.logging.Logger;import navigators.smart.communication.ServerCommunicationSystem;import navigators.smart.tom.ServiceReplica;import navigators.smart.tom.core.messages.TOMMessage;import navigators.smart.tom.util.DebugInfo;

public class ThroughputTestServer extends ServiceReplica {
    
//    private ServerCommunicationSystem cs;
    private int id;
    private int interval;
    private long numDecides=0;
    private long lastDecideTimeInstant;
    private long max=0;
    
    public ThroughputTestServer(int id, int interval) throws IOException {
        super(id);
        this.id = id;
        this.interval = interval;
    }

    public void receiveOrderedMessage(TOMMessage msg){

        long receiveInstant =  System.currentTimeMillis();          

        numDecides++;

        if (numDecides == 1) {

            lastDecideTimeInstant = receiveInstant;

        } else if (numDecides==interval) {

            long elapsedTime = receiveInstant - lastDecideTimeInstant;

            double opsPerSec_ = interval/(elapsedTime/1000.0);

            long opsPerSec = Math.round(opsPerSec_);

            if (opsPerSec>max)

                max = opsPerSec;

            System.out.println("Last "+interval+" decisions were done at a rate of " + opsPerSec + " ops per second");

            System.out.println("Maximum throughput until now: " + max + " ops per second");

            numDecides = -1;

        }

    }

    

    public static void main(String[] args){

        if(args.length < 2) {

            System.out.println("Use: java ThroughputTestServer <processId> <measurement interval (in messages)>");

            System.exit(-1);

        }
        try {
        new ThroughputTestServer(Integer.parseInt(args[0]),Integer.parseInt(args[1])).run();
        } catch (IOException ex) {
            Logger.getLogger(ThroughputTestServer.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public byte[] getState() {
        return null;
    }

    public void setState(byte[] state) {

    }

    public void receiveUnorderedMessage(TOMMessage msg) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected byte[] serializeState() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected void deserializeState(byte[] state) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public byte[] executeCommand(int clientId, long timestamp, byte[] nonces, byte[] command, DebugInfo info) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}

