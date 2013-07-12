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
 */package navigators.smart.tom.util;

import java.nio.ByteBuffer;
import java.util.Random;

/**
 * Batch format: TIMESTAMP(long) + N_NONCES(int) + SEED(long) +
 *               N_MESSAGES(int) + N_MESSAGES*[MSGSIZE(int),MSG(byte),SIG(byte)] +
 *               
 *
 * The methods does not try to enforce any constraint, so be correct when using it.
 *
 */
public final class BatchBuilder {
	
    private Random rnd = new Random();

    /** build buffer */
    public byte[] createBatch(long timestamp, int numberOfNonces, int numberOfMessages, int totalMessagesSize, byte[][] messages, byte[][] signatures) {
        int size = 16 + //timestamp 8, nonces 4, nummessages 4
                (numberOfNonces > 0 ? 8 : 0) + //seed if needed
                (numberOfMessages*(4+(signatures != null ?signatures[0].length:0)))+ // msglength + signature for each msg
                totalMessagesSize; //size of all msges
        
        ByteBuffer  proposalBuffer = ByteBuffer.allocate(size);

        proposalBuffer.putLong(timestamp);

        proposalBuffer.putInt(numberOfNonces);

        if(numberOfNonces>0){
            proposalBuffer.putLong(rnd.nextLong());
        }

        proposalBuffer.putInt(numberOfMessages);

        for (int i = 0; i < numberOfMessages; i++) {
        	proposalBuffer.putInt(messages[i].length);
            proposalBuffer.put(messages[i]);

            if(signatures != null) {
                proposalBuffer.put(signatures[i]);
            }
        }

        return proposalBuffer.array();
    }


}
