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
package navigators.smart.paxosatwar.messages;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import navigators.smart.tests.util.TestHelper;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * 
 * @author Christian Spann 
 */
public class PaxosMessageTest {
	
	@Test
	public void testSerialiseFreeze() {
		PaxosMessage msg = new PaxosMessage(MessageFactory.FREEZE,0l,0,0,0);
		ByteBuffer buf = ByteBuffer.allocate(msg.getMsgSize());
		msg.serialise(buf);
		buf.rewind();
		PaxosMessage msg2 = new PaxosMessage(buf);
		assertEquals(msg,msg2);
	}
	
		@Test
	public void testSerialiseWeakStrongDecide() {
		PaxosMessage msg = new VoteMessage(MessageFactory.WEAK, 0l, 0, 0,TestHelper.createTestByte(),0);
		ByteBuffer buf = ByteBuffer.allocate(msg.getMsgSize());
		msg.serialise(buf);
		buf.rewind();
		VoteMessage msg2 = new VoteMessage(buf);
		assertEquals(msg,msg2);
	}
		
	@Test
	public void testSerialisePropose() {
		PaxosMessage msg = new Propose( 0l, 0, 0, TestHelper.createTestByte(), null);
		ByteBuffer buf = ByteBuffer.allocate(msg.getMsgSize());
		msg.serialise(buf);
		buf.rewind();
		Propose msg2 = new Propose(buf);
		assertEquals(msg,msg2);
	}
	
	@Test
	public void testSerialiseFreezeProof(){
		FreezeProof freeze = new FreezeProof(0, 1l, 1, new byte[0], true,true,false);
		ByteBuffer buf = ByteBuffer.allocate(freeze.getMsgSize());
		freeze.serialise(buf);
		buf.rewind();
		FreezeProof freeze2 = new FreezeProof(buf);
		assertEquals(freeze, freeze2);
		
		freeze = new FreezeProof(0, 1l, 1, new byte[0], false,false,false);
		buf = ByteBuffer.allocate(freeze.getMsgSize());
		freeze.serialise(buf);
		buf.rewind();
		freeze2 = new FreezeProof(buf);
		assertEquals(freeze, freeze2);
		
		freeze = new FreezeProof(0, 1l, 1, TestHelper.createTestByte(), true,true,false);
		buf = ByteBuffer.allocate(freeze.getMsgSize());
		freeze.serialise(buf);
		buf.rewind();
		freeze2 = new FreezeProof(buf);
		assertEquals(freeze, freeze2);
	}
	
	@Test
	public void testSerialiseCollectEmpty() {
		FreezeProof freeze = new FreezeProof(0, 1l, 1,  TestHelper.createTestByte(),true,true,false);
		LinkedList<FreezeProof> freezes = new LinkedList<FreezeProof>();
		freezes.add(freeze);
		Collect msg = new Collect(0l,0,0,0, new CollectProof(freezes, 1));
		msg.getProof().setSignature(TestHelper.createTestByte());
		ByteBuffer buf = ByteBuffer.allocate(msg.getMsgSize());
		msg.serialise(buf);
		buf.rewind();
		Collect msg2 = new Collect(buf);
		assertEquals(msg,msg2);
		
		//Test with empty freezes list
		freezes.clear();
		msg = new Collect(0l,0,0,0, new CollectProof(freezes, 1));
		msg.getProof().setSignature(TestHelper.createTestByte());
		buf = ByteBuffer.allocate(msg.getMsgSize());
		msg.serialise(buf);
		buf.rewind();
		msg2 = new Collect(buf);
		assertEquals(msg,msg2);
		
//		//Test with two freezes null
//		msg = new Collect(0l,0,0, new CollectProof(null, null, 1));
//		msg.getProof().setSignature(TestHelper.createTestByte());
//		buf = ByteBuffer.allocate(msg.getMsgSize());
//		msg.serialise(buf);
//		buf.rewind();
//		msg2 = new Collect(buf);
//		assertEquals(msg,msg2);
	}
	
	@Test
	public void testSerialiseCollectTestByte() {
		byte[] test = TestHelper.createTestByte();
		FreezeProof freeze = new FreezeProof(0, 1l, 1, test, true,true,false);
		LinkedList<FreezeProof> freezes = new LinkedList<FreezeProof>();
		freezes.add(freeze);
		Collect msg = new Collect(0l,0,0,0, new CollectProof(freezes, 1));
		msg.getProof().setSignature(TestHelper.createTestByte());
		ByteBuffer buf = ByteBuffer.allocate(msg.getMsgSize());
		msg.serialise(buf);
		buf.rewind();
		Collect msg2 = new Collect(buf);
		assertEquals(msg,msg2);
	}

}
