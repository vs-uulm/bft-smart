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
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 *
 * @author Christian Spann 
 */
public class HMacVerifier implements PTPMessageVerifier {

	private static final Logger log = Logger.getLogger(HMacVerifier.class.getName());
	private static final String MAC_ALGORITHM = "HmacMD5";
	private static final String PASSWORD = "newcs";
	private SecretKey authKey;
	private Mac macSend;
	private Mac macReceive;
	private int macSize;

	//TODO Implement this properly for live usage if intended
	public void authenticateAndEstablishAuthKey() {
		if (authKey != null) {
			return;
		}

		try {
			//if (conf.getProcessId() > remoteId) {
			// I asked for the connection, so I'm first on the auth protocol
			//DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
			//} else {
			// I received a connection request, so I'm second on the auth protocol
			//DataInputStream dis = new DataInputStream(socket.getInputStream());
			//}

			SecretKeyFactory fac = SecretKeyFactory.getInstance("PBEWithMD5AndDES");
			PBEKeySpec spec = new PBEKeySpec(PASSWORD.toCharArray());
			authKey = fac.generateSecret(spec);

			macSend = Mac.getInstance(MAC_ALGORITHM);
			macSend.init(authKey);
			macReceive = Mac.getInstance(MAC_ALGORITHM);
			macReceive.init(authKey);
			macSize = macSend.getMacLength();
		} catch (InvalidKeySpecException ex) {
			log.log(Level.SEVERE, null, ex);
		} catch (InvalidKeyException ex) {
			log.log(Level.SEVERE, null, ex);
		} catch (NoSuchAlgorithmException ex) {
			log.log(Level.SEVERE, null, ex);
		}
	}

	/**
	 * Generates the hash for the given message
	 *
	 * @param messageData The data to hash
	 * @return The generated hash
	 */
	public byte[] generateHash(byte[] messageData) {
		byte[] hash = macSend.doFinal(messageData);
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Hashed: {0}", Arrays.toString(messageData));
		}
		return hash;
	}

	/**
	 * Returns the size of the provided hashes
	 *
	 * @return The hashsize
	 */
	public int getHashSize() {
		return macSize;
	}

	/**
	 * Verifies the given data with the given hash. The Bytebuffers position will be reset to where it was upon return.
	 *
	 * @param data The data to check
	 * @param receivedHash The provided hash to compare to
	 * @return true if the hash fits the data, false otherwhise
	 */
	@Override
	public boolean verifyHash(ByteBuffer data, ByteBuffer receivedHash) {
		data.mark();
		macReceive.update(data);
		data.reset();
		assert (receivedHash.capacity() == macSize) : "Receivedhash buffer is not of the correct size";
		byte[] computedHash = macReceive.doFinal();
		boolean verified = Arrays.equals(computedHash, receivedHash.array());
		if (!verified) {
			log.log(Level.SEVERE, "Verification Failure: hash {0} and received hash {1} differ", new Object[]{Arrays.toString(computedHash), Arrays.toString(receivedHash.array())});
		}
		return verified;
	}
}
