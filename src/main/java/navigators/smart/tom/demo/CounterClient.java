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
import java.nio.ByteBuffer;

import navigators.smart.tom.ServiceProxy;


/**
 * Example client that updates a BFT replicated service (a counter).
 * @author Christian Spann 
 */
public class CounterClient {

    public static void main(String[] args) throws IOException {
        if(args.length < 2) {
            System.out.println("Usage: java CounterClient <process id> <increment>");
            System.out.println("       if <increment> equals 0 the request will be read-only");
            System.exit(-1);
        }

        ServiceProxy counterProxy = new ServiceProxy(Integer.parseInt(args[0]));

        int i=0;
        int inc = Integer.parseInt(args[1]);
        ByteBuffer buf = ByteBuffer.allocate(4);
        ByteBuffer replybuf;


        //sends 1000 requests to replicas and then terminates
        while(i<1000){
            buf.putInt(inc);
            buf.rewind();
	    replybuf = ByteBuffer.wrap(counterProxy.invoke(buf.array(),(inc == 0)));
	    System.out.println("Counter value: "+replybuf.getInt());
	    i++;
        }
    }

}
