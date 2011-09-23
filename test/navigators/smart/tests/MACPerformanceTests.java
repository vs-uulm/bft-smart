/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package navigators.smart.tests;

import java.nio.ByteBuffer;
import java.util.Random;
import navigators.smart.communication.server.HMacVerifierFactory;
import navigators.smart.communication.server.PTPMessageVerifier;

/**
 *
 * @author Chritian Spann <christian.spann at uni-ulm.de>
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
