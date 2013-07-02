/*
 * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and
 * the authors indicated in the
 *
 * @author tags
 *
 * This file is part of SMaRt.
 *
 * SMaRt is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * SMaRt is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SMaRt. If not, see <http://www.gnu.org/licenses/>.
 */
package navigators.smart.paxosatwar.executionmanager;


import org.junit.*;
import static org.junit.Assert.*;

/**
 *
 * @author Christian Spann
 */
public class LeaderModuleTest {
	
	LeaderModule instance;
	
	public LeaderModuleTest() {
	}

	@BeforeClass
	public static void setUpClass() throws Exception {
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
	}
	
	@Before
	public void setUp() {
		instance = new LeaderModule(4,0);
	}
	
	@After
	public void tearDown() {
	}

	/**
	 * Test of setLeaderInfo method, of class LeaderModule.
	 */
	@Test
	public void testSetLeaderInfo() {
		System.out.println("setLeaderInfo");
		Long exec = 5l;
		Integer r = 0;
		Integer leader = 3;
		
		// Check addition of random value and equality for return value for
		// both methods
		instance.setLeaderInfo(exec, r, leader);
		assertEquals(leader, instance.getLeader(exec));
		assertEquals(leader, instance.getLeader(exec,r));
		
		
		// Check addition of higher round
		r = 3;
		instance.setLeaderInfo(exec, r, leader);
		assertEquals(leader, instance.getLeader(exec,r));
		
		
		// Check addition of existant value
		exec = 0l;
		r = 0;
		assertEquals((Integer)0, instance.getLeader(exec));
		instance.setLeaderInfo(exec, r, leader);
		assertEquals(leader, instance.getLeader(exec));
		assertEquals(leader, instance.getLeader(exec,r));
		
	}

	/**
	 * Test of freezeRound method, of class LeaderModule.
	 */
	@Test
	public void testFreezeRound() {
		System.out.println("freezeRound");
		Long exec = 0l;
		Integer r = 0;
		Integer leader = 1;
		
		instance.decided(exec,r);
		instance.collectRound(exec, r);
		
		assertEquals(instance.getLeader(exec,1),leader);
		
		exec = 1l;
		assertTrue(instance.checkLeader(exec, r, leader));
		
	}

	/**
	 * Test of decided method, of class LeaderModule.
	 */
	@Test
	public void testDecided() {
		System.out.println("decided");
		Long exec = 0l;
		Integer r = 0;
		Integer leader = 0;
		
		// Check normal decision // leadership for 1 is set
		instance.decided(exec,r);
		assertEquals(instance.getLeader(1l), leader);

	}
	
	/**
	 * Test of decided method, of class LeaderModule.
	 */
	@Test
	public void testDecided_gaps(){
		System.out.println("decided gaps");
		Long exec = 0l;
		Integer r = 0;
		
		// Check decision that would introduce gaps
		exec+=2;
		instance.decided(exec,r);
		assertNull(instance.getLeader(exec));
		
	}
	
	/**
	 * Test of decided method, of class LeaderModule.
	 */
	@Test
	public void testDecided_existantLeader(){
		System.out.println("decided existant leader");
		Long exec = 1l;
		Integer r = 0;
		Integer leader = 3;
		
		// Check decision that exists, first the leader for 2 is set to 3,
		// then 2 is decided 
		instance.setLeaderInfo(exec, r, leader);
		exec = 0l;
		instance.decided(exec,r);
		assertNotEquals(instance.getLeader(exec+1), (Integer)0);
	}

	/**
	 * Test of checkAndSetLeader method, of class LeaderModule.
	 */
	@Test
	public void testCheckAndSetLeader() {
		System.out.println("checkAndSetLeader");
		Long exec = 5l;
		Integer r = 0;
		Integer leader = 3;
		
		// Check addition of random higher value which will not be added
		// because it would introduce a gap
		boolean result = instance.checkLeader(exec, r, leader);
		assertFalse(result);
		assertNull(instance.getLeader(exec,r));
		
		// Checks addition of a random value again with a different leader
		leader = 0;
		exec = 0l;
		result = instance.checkLeader(exec, r, leader);
		assertTrue(result);
		assertEquals(leader, instance.getLeader(exec,r));
	}

	/**
	 * Test of getLeader method, of class LeaderModule.
	 */
	@Test
	public void testGetLeader_Long_Integer() {
		System.out.println("getLeader");
		Long exec = 0l;
		Integer r = 0;
		Integer leader = 0;
		
		// Check addition of random higher value which will not be added
		// because it would introduce a gap
		assertEquals(leader, instance.getLeader(exec,r));
		assertEquals(null, instance.getLeader(exec+1,r));
	}

	/**
	 * Test of getLeader method, of class LeaderModule.
	 */
	@Test
	public void testGetLeader_Long() {
		System.out.println("getLeader");
		Long exec = 0l;
		Integer leader = 0;
		
		// Check addition of random higher value which will not be added
		// because it would introduce a gap
		assertEquals(leader, instance.getLeader(exec));
		assertEquals(null, instance.getLeader(exec+1));
	}


	/**
	 * Test of removeStableConsenusInfo method, of class LeaderModule.
	 */
	@Test
	public void testRemoveStableConsenusInfo() {
		System.out.println("removeStableConsenusInfo");
		Long c = null;
		instance.removeStableConsenusInfo(c);
		// TODO review the generated test code and remove the default call to fail.
		fail("The test case is a prototype.");
	}

	/**
	 * Test of removeAllStableConsenusInfo method, of class LeaderModule.
	 */
	@Test
	public void testRemoveAllStableConsenusInfo() {
		System.out.println("removeAllStableConsenusInfo");
		Long c = null;
		instance.removeAllStableConsenusInfo(c);
		// TODO review the generated test code and remove the default call to fail.
		fail("The test case is a prototype.");
	}

	/**
	 * Test of removeMultipleStableConsenusInfos method, of class LeaderModule.
	 */
	@Test
	public void testRemoveMultipleStableConsenusInfos() {
		System.out.println("removeMultipleStableConsenusInfos");
		Long cStart = null;
		Long cEnd = null;
		instance.removeMultipleStableConsenusInfos(cStart, cEnd);
		// TODO review the generated test code and remove the default call to fail.
		fail("The test case is a prototype.");
	}

	/**
	 * Test of getState method, of class LeaderModule.
	 */
	@Test
	public void testGetState() {
		System.out.println("getState");
		byte[] expResult = null;
		byte[] result = instance.getState();
		assertEquals(expResult, result);
		// TODO review the generated test code and remove the default call to fail.
		fail("The test case is a prototype.");
	}

	/**
	 * Test of setState method, of class LeaderModule.
	 */
	@Test
	public void testSetState() throws Exception {
		System.out.println("setState");
		byte[] state = null;
		instance.setState(state);
		// TODO review the generated test code and remove the default call to fail.
		fail("The test case is a prototype.");
	}
}
