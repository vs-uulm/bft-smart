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
package navigators.smart.communication.server;

import java.nio.ByteBuffer;


/**
 *
 * This Interface represents a messageverifier, that creates hashes for messages
 * to send and verfies messages received. To represent the capabilities of a 
 * Unique Sequential Identifier Generator the result of a verification is not
 * a boolean but an object containing the appended verification information.
 *
 * @param <A> This is the Type of the objects returned when authenticating a message
 *
 * @author Christian Spann 
 */
public interface MessageVerifier {

    /**
     * Initialises this verifier.
     */
    public void authenticateAndEstablishAuthKey();

    /**
     * Returns the size of the generated hashes by this verifier
     * @return
     */
    public int getHashSize();

    /**
     * Returns the decoded verification data if valid, null otherwise. With that
     * Functionality it is possible to use e.g. the USIG mechanism in ebawa
     * where monotonically rising numbers are generated.
     * @param data The data to check
     * @param receivedHash The hash
     * @return True is the hash of the data equals the given hash, false if not
     */
    public boolean verifyHash(ByteBuffer data, ByteBuffer receivedHash);
    
    

}
