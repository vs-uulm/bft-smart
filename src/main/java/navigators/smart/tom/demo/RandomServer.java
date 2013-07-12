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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.tom.ServiceReplica;
import navigators.smart.tom.util.DebugInfo;

/**
 *
 * @author Joao Sousa
 */
public class RandomServer extends ServiceReplica {

    private int value = 0;
    private int iterations = 0;

    public RandomServer(int id) throws IOException{
        super(id);
    }


    @Override
    public byte[] executeCommand(int clientId, long timestamp, byte[] nonces, byte[] command, DebugInfo info) {
        iterations++;
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(command));
            int argument = input.readInt();
            int operator = input.readInt();

            System.out.println("[server] Argument: " + argument);
            switch (operator) {
                case 0:
                    value = value + argument;
                    System.out.println("[server] Operator: +");
                    break;
                case 1:
                    value = value - argument;
                    System.out.println("[server] Operator: -");
                    break;
                case 2:
                    value = value * argument;
                    System.out.println("[server] Operator: *");
                    break;
                case 3:
                    value = value / argument;
                    System.out.println("[server] Operator: /");
                    break;
            }
            //value += increment;
            if (info == null) System.out.println("[server] (" + iterations + ") Current value: " + value);
            else System.out.println("[server] (" + iterations + " / " + info.eid + ") Current value: " + value);
            ByteArrayOutputStream out = new ByteArrayOutputStream(4);
            new DataOutputStream(out).writeInt(value);
            return out.toByteArray();
        } catch (IOException ex) {
            Logger.getLogger(CounterServer.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    public static void main(String[] args){
        if(args.length < 1) {
            System.out.println("Use: java RandomServer <processId>");
            System.exit(-1);
        }
        try {
        new RandomServer(Integer.parseInt(args[0]));
        } catch (IOException ex) {
            Logger.getLogger(RandomServer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    protected byte[] serializeState() {

        byte[] b = new byte[4];
        for (int i = 0; i < 4; i++) {
            int offset = (b.length - 1 - i) * 8;
            b[i] = (byte) ((value >>> offset) & 0xFF);
        }
        return b;

        //throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected void deserializeState(byte[] state) {

        int value = 0;
        for (int i = 0; i < 4; i++) {
            int shift = (4 - 1 - i) * 8;
            value += (state[i] & 0x000000FF) << shift;
        }

        this.value = value;
    }

}
