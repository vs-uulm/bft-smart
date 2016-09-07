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

import navigators.smart.communication.ServerCommunicationSystem;
import navigators.smart.tom.TOMReceiver;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.Storage;
import navigators.smart.tom.util.TOMConfiguration;
import org.apache.commons.math.stat.descriptive.SynchronizedDescriptiveStatistics;


public class ThroughputLatencyTestServer extends TOMReceiver {
    
    private Integer id;
    private int interval;
    private long numDecides=0;
    private long lastDecideTimeInstant;
    private double max=0;
    private long totalOps;
//    private long startTimeInstant;
    private int averageIterations;
//    Storage st;
    //Storage consensusLatencySt;
//    Storage totalLatencySt1;
//    Storage batchSt1;
//    Storage totalLatencySt2;
//    Storage batchSt2;
//    private SynchronizedDescriptiveStatistics averageOps;
    
    public ThroughputLatencyTestServer(Integer id, int interval, int averageIterations) throws IOException {
        super(new TOMConfiguration(id, "./config"));
        this.id = id;
        this.interval = interval;
        this.totalOps = 0;
        this.averageIterations = averageIterations;
//        st = new Storage(averageIterations);
//        averageOps = new SynchronizedDescriptiveStatistics(averageIterations);
        System.out.println("#ThroughputLatencyTestServer throughput interval= "+interval+ " msgs");
        System.out.println("#ThroughputLatencyTestServer measurement interval for average calculations = "+averageIterations+ " throughput intervals ");
        System.out.println("Average throughput time;");
    }
    
//    public void run(){
//        //create the configuration object
//        TOMConfiguration conf = new TOMConfiguration(id,"./config");
//        try {
//            //create the communication system
//            cs = new ServerCommunicationSystem(conf);
//            System.out.println("#ThroughputLatencyTestServer throughput interval= "+interval+ " msgs");
//            System.out.println("#ThroughputLatencyTestServer average throughput interval= "+averageIterations+ " throughput intervals ");
////            startTimeInstant = System.currentTimeMillis();
//        } catch (Exception ex) {
//            Logger.getLogger(ThroughputLatencyTestServer.class.getName()).log(Level.SEVERE, null, ex);
//            throw new RuntimeException("Unable to build a communication system.");
//        }
//        //build the TOM server stack
//        this.init(cs,conf);
//
//        /**IST OE CODIGO DO JOAO, PARA TENTAR RESOLVER UM BUG */
//        cs.start();
//        service.start();
//        /******************************************************/
//    }
    
    public void receiveOrderedMessage(TOMMessage msg){
        long receiveInstant =  System.currentTimeMillis();          

        totalOps++;

        byte[] request = msg.getContent();
        int remoteId = ByteBuffer.wrap(request).getInt();

        if (remoteId ==-2){
           //does nothing, it's a request from the throughput client
        }
        else if (remoteId==-1){
            //send back totalOps
//        	System.out.println("Client "+msg.getSender()+" requests ops");
            byte[] command = new byte[12];
            ByteBuffer buf = ByteBuffer.wrap(command);
            buf.putInt(-1);
            buf.putLong(totalOps);
            TOMMessage reply = new TOMMessage(id,msg.getSequence(),
                    command);
            cs.send(new Integer[]{msg.getSender()},reply);
        }
        else {
            //echo msg to client
            //System.out.println("Echoing msg to client");
            TOMMessage reply = new TOMMessage(id,msg.getSequence(),
                    msg.getContent());
            cs.send(new Integer[]{msg.getSender()},reply);
        }

        //do throughput calculations
        numDecides++;

//        totalLatencySt1.storeDuration(msg.requestTotalLatency);
//        batchSt1.storeDuration(msg.consensusBatchSize);

        if (numDecides == 1) {
            lastDecideTimeInstant = receiveInstant;
        } else if (numDecides==interval) {
            long elapsedTime = receiveInstant - lastDecideTimeInstant;
            //double opsPerSec_ = ((double)interval)/(elapsedTime/1000.0);
            double opsPerSec = interval/(((double)elapsedTime/1000));
            if (opsPerSec>max)
                max = opsPerSec;
            
//            averageOps.addValue(opsPerSec);
//            System.out.println(opsPerSec+"; "+max+":");
//            st.storeDuration( Math.round(opsPerSec));
//            batchSt2.storeDuration(batchSt1.getAverage(true));
//            totalLatencySt2.storeDuration(totalLatencySt1.getAverage(true));
//            batchSt1.reset();
//            totalLatencySt1.reset();

            /*
            DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd-HH:mm:ss");
            Date date = new Date();
            String dataActual = dateFormat.format(date);
            System.out.println("("+dataActual+") Last "+interval+" decisions were done at a rate of " + opsPerSec + " ops per second");
            System.out.println("("+dataActual+") Maximum throughput until now: " + max + " ops per second");
            */
            //TODO: colocar impressão do consensus batch size
            //System.out.println("Msg: "+msg.getId() +" Duration of exec: "+(System.currentTimeMillis()-lastDecideTimeInstant)/1000 + "s Ops/sec: " + opsPerSec);
            
//            if (averageOps.getN()==averageIterations){
////                System.out.println("#Average/Std dev. throughput: "+st.getAverage(true)+"/"+st.getDP(true));
//                System.out.println("#Avg/Std dev. throughput for "+averageIterations+" times " + interval+ " msgs: "+averageOps.getMean()+"/"+averageOps.getStandardDeviation());
//                System.out.println("#Peak throughput: " + max +" Total Ops until now: "+totalOps);
//                //System.out.println("#Average/Std dev. consensus latency: " + consensusLatencySt.getAverage(true) + "/" + consensusLatencySt.getDP(true));
////                System.out.println("#Average/Std dev. total latency: " + totalLatencySt2.getAverage(true) + "/" + totalLatencySt2.getDP(true));
////                System.out.println("#Average/Std dev. batch size: " + batchSt2.getAverage(true) + "/" + batchSt2.getDP(true));
////                st.reset();
//                averageOps.clear();
//                //consensusLatencySt.reset();
////                totalLatencySt2.reset();
////                batchSt2.reset();
//            }
            numDecides = 0;           
        }
    }
    
    public static void main(String[] args){
        try {
        if(args.length < 3) {
            System.out.println("Use: java ThroughputLatencyTestServer <processId> <throughput/latency measurement interval (in messages)> <average throughput interval (number of measurement intervals)>");
            System.exit(-1);
        }
            new ThroughputLatencyTestServer(new Integer(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
        } catch (IOException ex) {
            Logger.getLogger(ThroughputLatencyTestServer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public byte[] getState() {
        return new byte[0];
    }

    public void setState(byte[] state) {
    	//unused
    }

    public void receiveUnorderedMessage(TOMMessage msg) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
