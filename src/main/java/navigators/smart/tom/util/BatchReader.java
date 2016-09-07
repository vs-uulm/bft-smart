/**
 * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
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

package navigators.smart.tom.util;

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
