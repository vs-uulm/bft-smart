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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;

import navigators.smart.tom.core.messages.TOMMessage;

public class TOMUtil {

	// private static final int BENCHMARK_PERIOD = 10000;

	// some message types
	public static final int RR_REQUEST = 0;
	public static final int RR_REPLY = 1;
	public static final int RR_DELIVERED = 2;
	public static final int RT_TIMEOUT = 3;
	public static final int RT_COLLECT = 4;
	public static final int RT_LEADER = 5;
	public static final int SM_REQUEST = 6;
	public static final int SM_REPLY = 7;

	// the signature engine used in the system and the signatureSize
	private Signature signatureEngine;
	private int signatureSize;

	// lock to make signMessage and verifySignature reentrant
//	private static ReentrantLock lock = new ReentrantLock();

	// private static Storage st = new Storage(BENCHMARK_PERIOD);
	// private static int count=0;
	
	public TOMUtil() throws NoSuchAlgorithmException, InvalidKeyException, SignatureException{
		signatureEngine = Signature.getInstance("SHA1withRSA");

		signatureEngine.initSign(TOMConfiguration.getRSAPrivateKey());

		signatureEngine.update("a".getBytes());
		byte[] signature = signatureEngine.sign();

		signatureSize = signature.length;
	}

	public int getSignatureSize() {
		return signatureSize;
	}

	/**
	 * Sign a message.
	 * 
	 * @param key
	 *            the private key to be used to generate the signature
	 * @param message
	 *            the message to be signed
	 * @throws SignatureException 
	 */
	public synchronized void signMessage(PrivateKey key, TOMMessage message) {
		try{
		signatureEngine.update(message.getBytes());
		message.serializedMessageSignature = signatureEngine.sign();
		} catch(SignatureException e){
			e.printStackTrace();
		}
	}

	/**
	 * Verify the signature of a message.
	 * 
	 * @param key
	 *            the public key to be used to verify the signature
	 * @param message
	 *            the signed message
	 * @param signature
	 *            the signature to be verified
	 * @return the signature
	 */
	public synchronized boolean verifySignature(PublicKey key, byte[] message, byte[] signature) {
//		long startTime = System.nanoTime();
		try{
			signatureEngine.initVerify(key);

			signatureEngine.update(message);

			boolean result = signatureEngine.verify(signature);
			/*
			 * st.store(System.nanoTime()-startTime); //statistics about
			 * signature execution time count++; if (count%BENCHMARK_PERIOD==0){
			 * System
			 * .out.println("#-- (TOMUtil) Signature verification benchmark:--"
			 * ); System.out.println("#Average time for " + BENCHMARK_PERIOD +
			 * " signature verifications (-10%) = " + st.getAverage(true) / 1000
			 * + " us "); System.out.println("#Standard desviation for " +
			 * BENCHMARK_PERIOD + " signature verifications (-10%) = " +
			 * st.getDP(true) / 1000 + " us ");
			 * System.out.println("#Average time for " + BENCHMARK_PERIOD +
			 * " signature verifications (all samples) = " +
			 * st.getAverage(false) / 1000 + " us ");
			 * System.out.println("#Standard desviation for " + BENCHMARK_PERIOD
			 * + " signature verifications (all samples) = " + st.getDP(false) /
			 * 1000 + " us "); System.out.println("#Maximum time for " +
			 * BENCHMARK_PERIOD + " signature verifications (-10%) = " +
			 * st.getMax(true) / 1000 + " us ");
			 * System.out.println("#Maximum time for " + BENCHMARK_PERIOD +
			 * " signature verifications (all samples) = " + st.getMax(false) /
			 * 1000 + " us ");
			 * 
			 * count = 0; st = new Storage(BENCHMARK_PERIOD); }
			 */
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public static String byteArrayToString(byte[] b) {
		String s = "";
		for (int i = 0; i < b.length; i++) {
			s = s + b[i];
		}

		return s;
		// Logger.println(s);
	}
}
