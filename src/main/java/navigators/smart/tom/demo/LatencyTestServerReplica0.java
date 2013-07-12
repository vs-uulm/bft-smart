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

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.communication.ServerCommunicationSystem;
import navigators.smart.tom.ServiceReplica;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.DebugInfo;
import navigators.smart.tom.util.TOMConfiguration;


public class LatencyTestServerReplica0 extends ServiceReplica {

    private ServerCommunicationSystem cs;
    private int id;

    /** Creates a new instance of TOMServerPerformanceTest */
    public LatencyTestServerReplica0(int id) throws IOException {
        super(id);
        this.id = id;
    }

    public void receiveOrderedMessage(TOMMessage msg){
        TOMMessage reply = new TOMMessage(id,msg.getSequence(),
                msg.getContent());

        cs.send(new Integer[]{msg.getSender()},reply);
    }

    public static void main(String[] args){
        try {
        /*
        if(args.length < 1) {
            System.out.println("Use: java LatencyTestServer <processId>");
            System.exit(-1);
        }
        new LatencyTestServer(Integer.parseInt(args[0])).run();
        */
        new LatencyTestServerReplica0(0).run();
        } catch (IOException ex) {
            Logger.getLogger(LatencyTestServerReplica0.class.getName()).log(Level.SEVERE, null, ex);
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
