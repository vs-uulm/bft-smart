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
	 * Test of a collect, the leader of the next round shall be set accordingly.
	 */
	@Test
	public void testCollectRound() {
		Long exec = 0l;
		Integer r = 0;
		Integer leader = 1;
		
		instance.collectRound(exec, r);
		
		assertEquals(instance.getLeader(exec,1),leader);
	}

//	/**
//	 * Test of decided method, of class LeaderModule.
//	 */
//	@Test
//	public void testDecided() {
//		System.out.println("decided");
//		Long exec = 0l;
//		Integer r = 0;
//		Integer leader = 0;
//		
//		// Check normal decision // leadership for 1 is set
//		instance.decided(exec,r);
//		assertEquals(instance.getLeader(1l), leader);
//
//	}
	
//	/**
//	 * Test of decided method, of class LeaderModule.
//	 */
//	@Test
//	public void testDecided_gaps(){
//		System.out.println("decided gaps");
//		Long exec = 0l;
//		Integer r = 0;
//		
//		// Check decision that would introduce gaps
//		exec+=2;
//		instance.decided(exec,r);
//		assertNull(instance.getLeader(exec));
//		
//	}
	
//	/**
//	 * Test of decided method, of class LeaderModule.
//	 */
//	@Test
//	public void testDecided_existantLeader(){
//		System.out.println("decided existant leader");
//		Long exec = 1l;
//		Integer r = 0;
//		Integer leader = 3;
//		
//		// Check decision that exists, first the leader for 2 is set to 3,
//		// then 2 is decided 
//		instance.setLeaderInfo(exec, r, leader);
//		exec = 0l;
//		instance.decided(exec,r);
//		assertNotEquals(instance.getLeader(exec+1), (Integer)0);
//	}

	/**
	 * Test of checkAndSetLeader method, of class LeaderModule.
	 */
	@Test
	public void testCheckLeader() {
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
	 * Test of checkAndSetLeader method, of class LeaderModule.
	 */
//	@Test
//	public void testCheckandSetLeader() {
//		System.out.println("checkAndSetLeader");
//		Long exec = 5l;
//		Integer r = 0;
//		Integer leader = 3;
//		
//		// Check addition of random higher value which will not be added
//		// because it would introduce a gap
//		boolean result = instance.checkAndSetLeader(exec, r, leader);
//		assertFalse(result);
//		assertNull(instance.getLeader(exec,r));
//		
//		// Checks addition of a random value again with a different leader
//		leader = 0;
//		exec = 1l;
//		result = instance.checkAndSetLeader(exec, r, leader);
//		assertTrue(result);
//		assertEquals(leader, instance.getLeader(exec,r));
//	}

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
		Long c = -1l;
		instance.removeStableConsenusInfo(c);
		assertNull(instance.getLeader(-1l));
	}

	/**
	 * Test of removeAllStableConsenusInfo method, of class LeaderModule.
	 */
	@Test
	public void testRemoveAllStableConsenusInfo() {
		System.out.println("removeAllStableConsenusInfo");
		
		instance.setLeaderInfo(1l, 0, 0);
		
		instance.removeAllStableConsenusInfo(0l);
		
		assertNull(instance.getLeader(0l));
		assertNull(instance.getLeader(-1l));
		assertEquals(instance.getLeader(1l),(Integer)0);
	}

	/**
	 * Test of removeMultipleStableConsenusInfos method, of class LeaderModule.
	 */
	@Test
	public void testRemoveMultipleStableConsenusInfos() {
		instance.setLeaderInfo(1l, 0, 0);
		instance.setLeaderInfo(2l, 0, 0);
		
		instance.removeMultipleStableConsenusInfos(0l, 1l);
		
		assertNull(instance.getLeader(0l));
		assertNull(instance.getLeader(1l));
		assertEquals(instance.getLeader(2l),(Integer)0);
		assertEquals(instance.getLeader(-1l),(Integer)0);
		
		
		
	}

	/**
	 * Test of getState method, of class LeaderModule.
	 */
	@Test
	public void testGetandSetState() throws ClassNotFoundException {
		System.out.println("getState");
		instance.collectRound(0l, 0);
		
		byte[] result = instance.getState();
		
		instance = new LeaderModule(4, 0);
		instance.setState(result);
		assertTrue(instance.checkLeader(0l, 1, 1));
		
	}

	
}

