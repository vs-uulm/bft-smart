/**
 * Copyright (c) 2007-2009 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
 * 
 * This file is part of SMaRt.
 * 
 * SMaRt is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * SMaRt is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with SMaRt.  If not, see <http://www.gnu.org/licenses/>.
 */

package navigators.smart.tom.demo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.tom.ServiceReplica;
import navigators.smart.tom.util.DebugInfo;


/**
 * Example replica that implements a BFT replicated service (a counter).
 * @author Christian Spann <christian.spann@uni-ulm.de>
 */
public class CounterServer extends ServiceReplica {
    
    private int counter = 0;
    private int iterations = 0;
    
    public CounterServer(int id) throws IOException {
        super(id);
    }
    
    
    @Override
    public byte[] executeCommand(int clientId, long timestamp, byte[] nonces, byte[] command, DebugInfo info) {
        iterations++;
        ByteBuffer buf = ByteBuffer.wrap(command); // allocate ByteBuffer for ints changed by cspann
        assert(buf.remaining()==4);
        int increment = buf.getInt();
        counter += increment;
        if (info == null) System.out.println("[server] (" + iterations + ") Counter incremented: " + counter);
        else System.out.println("[server] (" + iterations + " / " + info.eid + ") Counter incremented: " + counter);
        buf = ByteBuffer.allocate(4);
        buf.putInt(counter);
        return buf.array();
    }

    public static void main(String[] args){
        if(args.length < 1) {
            System.out.println("Use: java CounterServer <processId>");
            System.exit(-1);
        }
        try {
        new CounterServer(Integer.parseInt(args[0]));
        } catch (IOException ex) {
            Logger.getLogger(CounterServer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /** ISTO E CODIGO DO JOAO, PARA TRATAR DOS CHECKPOINTS */
    @Override
    protected byte[] serializeState() {

        byte[] b = new byte[4];
        for (int i = 0; i < 4; i++) {
            int offset = (b.length - 1 - i) * 8;
            b[i] = (byte) ((counter >>> offset) & 0xFF);
        }
        return b;
    }

    @Override
    protected void deserializeState(byte[] state) {

        int value = 0;
        for (int i = 0; i < 4; i++) {
            int shift = (4 - 1 - i) * 8;
            value += (state[i] & 0x000000FF) << shift;
        }

        this.counter = value;
    }
    /********************************************************/
}
