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
