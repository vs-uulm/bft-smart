/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package navigators.smart.statemanagment;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.*;
import static org.junit.Assert.*;

/**
 *
 * @author spann
 */
public class SMMessageTest {
	
	public SMMessageTest() {
	}

	@BeforeClass
	public static void setUpClass() throws Exception {
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
	}
	
	@Before
	public void setUp() {
	}
	
	@After
	public void tearDown() {
	}

	/**
	 * Test of getState method, of class SMMessage.
	 */
//	@Test
//	public void testGetState() {
//		System.out.println("getState");
//		SMMessage instance = null;
//		TransferableState expResult = null;
//		TransferableState result = instance.getState();
//		assertEquals(expResult, result);
//		// TODO review the generated test code and remove the default call to fail.
//		fail("The test case is a prototype.");
//	}

	/**
	 * Test of getType method, of class SMMessage.
	 */
//	@Test
//	public void testGetType() {
//		System.out.println("getType");
//		SMMessage instance = null;
//		int expResult = 0;
//		int result = instance.getType();
//		assertEquals(expResult, result);
//		// TODO review the generated test code and remove the default call to fail.
//		fail("The test case is a prototype.");
//	}

	/**
	 * Test of getEid method, of class SMMessage.
	 */
//	@Test
//	public void testGetEid() {
//		System.out.println("getEid");
//		SMMessage instance = null;
//		Long expResult = null;
//		Long result = instance.getEid();
//		assertEquals(expResult, result);
//		// TODO review the generated test code and remove the default call to fail.
//		fail("The test case is a prototype.");
//	}

	/**
	 * Test of getReplica method, of class SMMessage.
//	 */
//	@Test
//	public void testGetReplica() {
//		System.out.println("getReplica");
//		SMMessage instance = null;
//		int expResult = 0;
//		int result = instance.getReplica();
//		assertEquals(expResult, result);
//		// TODO review the generated test code and remove the default call to fail.
//		fail("The test case is a prototype.");
//	}

	/**
	 * Test of serialise method, of class SMMessage.
	 */
	@Test
	public void testSerialise() throws Exception{
		System.out.println("serialise");
		ByteBuffer out = ByteBuffer.allocate(1024);
		TransferableState state = new TransferableState();
		SMMessage instance = new SMMessage(0, 123l, 456, 789, state);
		instance.getMsgSize();
		instance.serialise(out);
		assertEquals(instance.getMsgSize(), out.position());
		out.flip();
		assertEquals(instance, new SMMessage(out));
	}

//	/**
//	 * Test of getMsgSize method, of class SMMessage.
//	 */
//	@Test
//	public void testGetMsgSize() {
//		System.out.println("getMsgSize");
//		SMMessage instance = null;
//		int expResult = 0;
//		int result = instance.getMsgSize();
//		assertEquals(expResult, result);
//		// TODO review the generated test code and remove the default call to fail.
//		fail("The test case is a prototype.");
//	}
}
