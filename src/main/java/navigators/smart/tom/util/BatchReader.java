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

import navigators.smart.tom.core.messages.TOMMessage;

/**
 * Batch format: N_MESSAGES(int) + N_MESSAGES*[MSGSIZE(int),MSG(byte)] +
 *               TIMESTAMP(long) + N_NONCES(int) + NONCES(byte[])
 *
 */
public final class BatchReader {

    private ByteBuffer proposalBuffer;
    private final boolean useSignatures;
    private final int signatureSize;

    /**
     * Reads batches
     * @param batch The batch to read
     * @param useSignatures Where signatures used 
     * @param signatureSize The size of the signatures used
     */
    public BatchReader(byte[] batch, boolean useSignatures, int signatureSize) {
        proposalBuffer = ByteBuffer.wrap(batch);
        this.useSignatures = useSignatures;
        this.signatureSize = signatureSize;
    }
    
    /* 
     * Deserializes a batch of TOMMessages and creates the required
     * Nonces for each request. 
     */
    public TOMMessage[] deserialiseRequests() {
        //obtain the timestamps to be delivered to the application
        long timestamp = proposalBuffer.getLong();

        int numberOfNonces = proposalBuffer.getInt();

        Random rnd = null;
        if(numberOfNonces > 0){
            rnd = new Random(proposalBuffer.getLong());
        }

        int numberOfMessages = proposalBuffer.getInt();

        TOMMessage[] requests = new TOMMessage[numberOfMessages];

        for (int i = 0; i < numberOfMessages; i++) {

            //read the message and its signature from the batch
            int messageSize = proposalBuffer.getInt();

            byte[] message = new byte[messageSize];
            proposalBuffer.get(message);

            byte[] signature = null;
            if(useSignatures){
                signature = new byte[signatureSize];
                proposalBuffer.get(signature);
            }
            //obtain the nonces to be delivered to the application
            byte[] nonces = new byte[numberOfNonces];
            if (nonces.length > 0) {
                rnd.nextBytes(nonces);
            }
            TOMMessage tm = new TOMMessage(ByteBuffer.wrap(message));

            tm.setBytes(message);
            tm.serializedMessageSignature = signature;
            tm.nonces = nonces;
            tm.timestamp = timestamp;
            requests[i] = tm;

        }
        return requests;
    }
}
