/**
 * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the
 *
 * @author tags
 *
 * This file is part of SMaRt.
 *
 * SMaRt is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * SMaRt is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with SMaRt. If not, see <http://www.gnu.org/licenses/>.
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
