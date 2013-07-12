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
 */
package navigators.smart.tests;

import java.nio.ByteBuffer;
import java.util.Random;
import navigators.smart.communication.server.HMacVerifierFactory;
import navigators.smart.communication.server.PTPMessageVerifier;

/**
 *
 * @author Chritian Spann
 */
public class MACPerformanceTests {

    public static void main(String[] args) {
        HMacVerifierFactory factory = new HMacVerifierFactory();
        PTPMessageVerifier v = factory.generateMessageVerifier();
        v.authenticateAndEstablishAuthKey();
        Random rnd = new Random();
        ByteBuffer data = ByteBuffer.allocate(1000);
        byte[] hash;
        int rounds = 10000000;
        long creation = 0;
        long verification = 0;
        for (int i = 0; i < rounds; i++) {
            rnd.nextBytes(data.array());
            long before = System.nanoTime();
            hash = v.generateHash(data.array());
            long after = System.nanoTime();
            creation += after - before;
            
            //test verification
            ByteBuffer hashbuf = ByteBuffer.wrap(hash);
            before = System.nanoTime();
            v.verifyHash(data,hashbuf);
            after = System.nanoTime();
            verification += after - before;
        }
        System.out.println("Generation of hashes: "+creation/rounds);
        System.out.println("Verification: "+verification/rounds);
    }
}
