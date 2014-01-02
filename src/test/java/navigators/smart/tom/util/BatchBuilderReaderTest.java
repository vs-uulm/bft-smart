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
package navigators.smart.tom.util;

import java.nio.ByteBuffer;
import navigators.smart.tests.util.MyTestHelper;

import navigators.smart.tom.core.messages.TOMMessage;

import org.junit.Assert;
import org.junit.Test;

/**
 * 
 * @author Christian Spann
 */

public class BatchBuilderReaderTest {

	@Test
	public void testCreateBatch() {
		BatchBuilder bb = new BatchBuilder();
		long timestamp = 2341;
		int numberOfNonces = 0;
		int numberOfMessages = 3;
		
		int totalMessageSize = 0; //total size of the messages being batched
        byte[][] messages = new byte[numberOfMessages][]; //bytes of the message (or its hash)
        TOMMessage[] tommsgs = new TOMMessage[numberOfMessages];
        byte[][] signatures = null;

        // Fill the array of bytes for the messages/signatures being batched
        
        for (int i = 0; i<numberOfMessages; i++) {
            TOMMessage msg = new TOMMessage(0, 0, MyTestHelper.createTestByte());
            tommsgs[i] = msg;
            ByteBuffer buf = ByteBuffer.allocate(msg.getMsgSize());
            msg.serialise(buf);
            messages[i] = buf.array();
            totalMessageSize += messages[i].length;
        }
		
		
		
		byte[] batch = bb.createBatch(timestamp, numberOfNonces, numberOfMessages, totalMessageSize, messages, signatures);
		
		
		BatchReader br = new BatchReader(batch, false,0);
		
		TOMMessage[] msges = br.deserialiseRequests();
		
		Assert.assertArrayEquals(tommsgs, msges);
	}
	@Test
	public void testCreateBatchWithSigs() {
		BatchBuilder bb = new BatchBuilder();
		long timestamp = 2341;
		int numberOfNonces = 0;
		int numberOfMessages = 3;
		
		int totalMessageSize = 0; //total size of the messages being batched
		byte[][] messages = new byte[numberOfMessages][]; //bytes of the message (or its hash)
		TOMMessage[] tommsgs = new TOMMessage[numberOfMessages];
		byte[][] signatures = null;
		
		// Fill the array of bytes for the messages/signatures being batched
		
		for (int i = 0; i<numberOfMessages; i++) {
			TOMMessage msg = new TOMMessage(0, 0, MyTestHelper.createTestByte());
			msg.setBytes(MyTestHelper.createTestByte());
			tommsgs[i] = msg;
			ByteBuffer buf = ByteBuffer.allocate(msg.getMsgSize());
			msg.serialise(buf);
			messages[i] = buf.array();
			totalMessageSize += messages[i].length;
		}
		
		
		
		byte[] batch = bb.createBatch(timestamp, numberOfNonces, numberOfMessages, totalMessageSize, messages, signatures);
		
		
		BatchReader br = new BatchReader(batch, false,0);
		
		TOMMessage[] msges = br.deserialiseRequests();
		
		Assert.assertArrayEquals(tommsgs, msges);
	}

}
