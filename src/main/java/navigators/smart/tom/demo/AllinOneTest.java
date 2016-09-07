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
import java.util.logging.Level;
import java.util.logging.Logger;
import navigators.smart.tom.util.TOMConfiguration;


public class AllinOneTest {

    public static void main(String args[]){
        try {
            
            new ThroughputLatencyTestServer(0,1000,10);
            new ThroughputLatencyTestServer(1,1000,10);
            new ThroughputLatencyTestServer(2,1000,10);
            new ThroughputLatencyTestServer(3,1000,10);
//            Thread.sleep(5000);
             
            TOMConfiguration conf1 = new TOMConfiguration(1001,"config");
            new ThroughputTestClient(1001, 10000,
                1, 1, conf1).run();
        } catch (IOException ex) {
            Logger.getLogger(AllinOneTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
