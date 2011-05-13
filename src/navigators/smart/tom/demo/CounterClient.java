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

import navigators.smart.tom.ServiceProxy;


/**
 * Example client that updates a BFT replicated service (a counter).
 * @author Christian Spann <christian.spann@uni-ulm.de>
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
