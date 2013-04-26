package navigators.smart.paxosatwar.messages;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import navigators.smart.tests.util.TestHelper;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class PaxosMessageTest {
	
	@Test
	public void testSerialiseFreeze() {
		PaxosMessage msg = new PaxosMessage(MessageFactory.FREEZE,0l,0,0);
		ByteBuffer buf = ByteBuffer.allocate(msg.getMsgSize());
		msg.serialise(buf);
		buf.rewind();
		PaxosMessage msg2 = new PaxosMessage(buf);
		assertEquals(msg,msg2);
	}
	
		@Test
	public void testSerialiseWeakStrongDecide() {
		PaxosMessage msg = new VoteMessage(MessageFactory.WEAK, 0l, 0, 0,TestHelper.createTestByte());
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
		Collect msg = new Collect(0l,0,0, new CollectProof(freezes, 1));
		msg.getProof().setSignature(TestHelper.createTestByte());
		ByteBuffer buf = ByteBuffer.allocate(msg.getMsgSize());
		msg.serialise(buf);
		buf.rewind();
		Collect msg2 = new Collect(buf);
		assertEquals(msg,msg2);
		
		//Test with empty freezes list
		freezes.clear();
		msg = new Collect(0l,0,0, new CollectProof(freezes, 1));
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
		Collect msg = new Collect(0l,0,0, new CollectProof(freezes, 1));
		msg.getProof().setSignature(TestHelper.createTestByte());
		ByteBuffer buf = ByteBuffer.allocate(msg.getMsgSize());
		msg.serialise(buf);
		buf.rewind();
		Collect msg2 = new Collect(buf);
		assertEquals(msg,msg2);
	}

}
