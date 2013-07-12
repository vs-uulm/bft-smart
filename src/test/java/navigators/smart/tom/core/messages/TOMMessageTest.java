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
package navigators.smart.tom.core.messages;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import navigators.smart.tests.util.TestHelper;

import org.junit.Test;

/**
 * 
 * @author Christian Spann
 */
public class TOMMessageTest {
	
	@Test
	public void testMsgSize(){
		TOMMessage msg = new TOMMessage(1, 3, TestHelper.createTestByte());
		ByteBuffer buf = ByteBuffer.allocate(1024);
		msg.serialise(buf);
		assert(msg.getMsgSize()==buf.position());
	}

	@Test
	public void testSerialise() {
		TOMMessage msg = new TOMMessage(1, 3, TestHelper.createTestByte());
		
		ByteBuffer buf = ByteBuffer.allocate(msg.getMsgSize());
		
		msg.serialise(buf);
		
		buf.rewind();
		
		TOMMessage msg2 = new TOMMessage(buf);
		
		assertEquals(msg,msg2);
		
	}

	@Test
	public void testCompareTo() {
		TOMMessage msg = new TOMMessage(1, 1, TestHelper.createTestByte());
		TOMMessage msg2 = new TOMMessage(1, 2, TestHelper.createTestByte());
		
		assert(msg.compareTo(msg2)<0);
		assert(msg2.compareTo(msg)>0);
		assert(msg.compareTo(msg)==0);
	}

}
