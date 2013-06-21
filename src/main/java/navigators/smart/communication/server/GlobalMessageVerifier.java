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

import navigators.smart.tom.core.messages.SystemMessage;

/**
 *
 * @author Christian Spann 
 */
public interface GlobalMessageVerifier<A> {

    /**
     * Generates a hash for the given messagedata
     * @param message The message to generate the hash for
     */
    public void generateHash(SystemMessage message);

    /**
     * Returns the decoded verification data if valid, null otherwise. With that
     * Functionality it is possible to use e.g. the USIG mechanism in ebawa
     * where monotonically rising numbers are generated.
     * @param data The data to check
     * @return The deserialised verification data if the data was valid
     */
    public A verifyHash(ByteBuffer data);

    /**
     * Initialises this verifier.
     */
    public void authenticateAndEstablishAuthKey();
}
