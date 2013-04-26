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

import navigators.smart.tom.TOMReceiver;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.TOMConfiguration;

public class ThroughputLatencyTestServer extends TOMReceiver {

    private Integer id;
    private int interval;
    private long numDecides = 0;
    private long lastDecideTimeInstant;
    private double max = 0;
    private long totalOps;
    private ByteBuffer state;

    public ThroughputLatencyTestServer(Integer id, int interval, int statesize) throws IOException {
        super(new TOMConfiguration(id, "./config"));
        this.id = id;
        this.interval = interval;
        this.totalOps = 0;
        state = ByteBuffer.allocate(statesize < 8 ? 8 : statesize);
        System.out.print("TLTS throughput int: " + interval + " msgs");
        System.out.print(" - state size: " + statesize + " (min 8 bytes)");
        System.out.println("Avg throughput times, state tfs: ");
    }

    @Override
    public void receiveOrderedMessage(TOMMessage msg) {
        long receiveInstant = System.currentTimeMillis();

        totalOps++;

        byte[] request = msg.getContent();
        int remoteId = ByteBuffer.wrap(request).getInt();

        TOMMessage reply; //will hold the replz if there is one to be sent

        switch (remoteId) {
            case -2:
                //does nothing, it's a request from the throughput client
				System.out.println("Received throughput request");
                break;

            case -1:
                //send back totalOps
				System.out.println("Received op count request");
                byte[] command = new byte[12];
                ByteBuffer buf = ByteBuffer.wrap(command);
                buf.putInt(-1);
                buf.putLong(totalOps);
                reply = new TOMMessage(id, msg.getSequence(),
                        command);
                cs.send(new Integer[]{msg.getSender()}, reply);
                break;

            default:
                //echo msg to client
				System.out.println("Received echo request");
                reply = new TOMMessage(id, msg.getSequence(),
                        msg.getContent());
                cs.send(new Integer[]{msg.getSender()}, reply);
        }

        numDecides++;

        if (numDecides == 1) {
            lastDecideTimeInstant = receiveInstant;
        } else if (numDecides == interval) {
            long elapsedTime = receiveInstant - lastDecideTimeInstant;
            //double opsPerSec_ = ((double)interval)/(elapsedTime/1000.0);
            double opsPerSec = interval / (((double) elapsedTime / 1000));
            if (opsPerSec > max) {
                max = opsPerSec;
            }

            System.out.println(opsPerSec +";"+tomlayer.getStateManager().getAndResetStateTransferCount() + ";");

            numDecides = 0;
        }

    }

    public static void main(String[] args) {
        try {
            if (args.length < 3) {
                System.out.println("Use: java ThroughputLatencyTestServer <processId> <throughput/latency measurement interval (in messages)> <size of transferred state)>");
                System.exit(-1);
            }
            new ThroughputLatencyTestServer(new Integer(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
        } catch (IOException ex) {
            Logger.getLogger(ThroughputLatencyTestServer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public byte[] getState() {
        state.rewind();
        state.putLong(totalOps);
        return state.array();
    }

    @Override
    public void setState(byte[] state) {
        ByteBuffer inbuf = ByteBuffer.wrap(state);
        totalOps = inbuf.getLong();
    }

    @Override
    public void receiveUnorderedMessage(TOMMessage msg) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
