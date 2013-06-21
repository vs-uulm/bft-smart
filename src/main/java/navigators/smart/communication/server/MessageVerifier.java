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
