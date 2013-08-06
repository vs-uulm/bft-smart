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
package navigators.smart.paxosatwar.executionmanager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import navigators.smart.paxosatwar.messages.MessageFactory;
import navigators.smart.paxosatwar.messages.PaxosMessage;
import navigators.smart.paxosatwar.messages.Propose;
import navigators.smart.paxosatwar.messages.VoteMessage;
import navigators.smart.paxosatwar.requesthandler.RequestHandler;
import navigators.smart.paxosatwar.roles.Acceptor;
import navigators.smart.paxosatwar.roles.Proposer;
import navigators.smart.statemanagment.TransferableState;
import navigators.smart.tom.core.TOMLayer;
import navigators.smart.tom.util.TOMConfiguration;

import org.junit.Before;
import org.junit.Test;

/**
 * 
 * @author Christian Spann
 */
public class ExecutionManagerTest {
	
	private ExecutionManager mng;
	Acceptor acceptor;
	Proposer proposer ;
	Integer[] acceptors = {0,1,2,3};
	int f;
	Integer me;
	long initialTimeout;
	TOMLayer tom ;
	LeaderModule lm;
	RequestHandler handlr;
	
	@Before
	public void setUp(){
		acceptor = mock(Acceptor.class);
		proposer = mock(Proposer.class);
		f = 1;
		me = 0;
		initialTimeout = 600000;
		tom = mock(TOMLayer.class);
		lm = mock(LeaderModule.class);
		handlr = mock(RequestHandler.class);
		TOMConfiguration conf = mock(TOMConfiguration.class);
		when(tom.getConf()).thenReturn(conf);
		when(conf.getPaxosHighMark()).thenReturn(100);
		when(conf.getRevivalHighMark()).thenReturn(10);
		mng = new ExecutionManager(acceptor, proposer, acceptors, f, me, initialTimeout, tom);
		mng.setRequestHandler(handlr);
	}

	@Test
	public void testCheckLimits_initial() {
		Integer[] others = {1,2,3}; //list of the other acceptors
		
		//test initial configuration
		when(tom.isRetrievingState()).thenReturn(false);
		assertTrue(mng.checkLimits(new Propose( 0l, 0, 1, 0, null,null)));
		assertFalse(mng.thereArePendentMessages(0l));
		
		//test initial ooc message
		when(tom.isRetrievingState()).thenReturn(false);
		assertFalse(mng.checkLimits(new Propose( 1l, 0, 1, 0, null,null)));
		assertTrue(mng.thereArePendentMessages(1l));
		//test initial ooc message with state transfer
		when(tom.isRetrievingState()).thenReturn(false);
		assertFalse(mng.checkLimits(new Propose( 99l, 0, 1, 0, null,null)));
		verify(tom).requestStateTransfer(me, others, 1, 99l);
		assertTrue(mng.thereArePendentMessages(1l));
	}
	
	@Test
	public void testCheckLimits_normal() {
		Integer[] others = {1,2,3}; //list of the other acceptors
		//test normal configuration
		when(tom.isRetrievingState()).thenReturn(false);
		assertTrue(mng.checkLimits(new Propose(0l, 0, 0, 0, null,null)));
		assertFalse(mng.thereArePendentMessages(0l));
		//test normal execution wrong leader msg
		when(tom.isRetrievingState()).thenReturn(false);
		assertFalse(mng.checkLimits(new Propose(1l, 0, 0, 0, null,null)));
		assertTrue(mng.thereArePendentMessages(1l));
		//test normal execution ooc msg
		when(tom.isRetrievingState()).thenReturn(false);
		assertFalse(mng.checkLimits(new Propose(1l, 0, 1, 0, null,null)));
		assertTrue(mng.thereArePendentMessages(1l));
		//test normal execution ooc msg with state transfer
		when(tom.isRetrievingState()).thenReturn(false);
		assertFalse(mng.checkLimits(new Propose(101l, 0, 1, 0, null,null)));
		verify(tom).requestStateTransfer(me, others, 1, 101l);
		assertTrue(mng.thereArePendentMessages(101l));
	}
	
	@Test
	public void testCheckLimits_initial_StateTransfer() {
		//STATE TRANSFER ENABLED
		//test initial configuration
		when(tom.isRetrievingState()).thenReturn(true);
		assertFalse(mng.checkLimits(new Propose(0l, 0, 1, 0, null,null)));
		assertTrue(mng.thereArePendentMessages(0l));
		//test initial ooc message
		when(tom.isRetrievingState()).thenReturn(true);
		assertFalse(mng.checkLimits(new Propose(1l, 0, 1, 0, null,null)));
		assertTrue(mng.thereArePendentMessages(1l));
		//test initial ooc message with state transfer
		when(tom.isRetrievingState()).thenReturn(true);
		assertFalse(mng.checkLimits(new Propose(99l, 0, 1, 0, null,null)));
		assertTrue(mng.thereArePendentMessages(1l));
	}

	@Test
	public void testCheckLimits_normal_StateTransfer() {
		//STATE TRANSFER ENABLED
		
		//test normal configuration
		when(tom.isRetrievingState()).thenReturn(true);
		assertFalse(mng.checkLimits(new Propose(1l, 0, 1, 0, null,null)));
		assertTrue(mng.thereArePendentMessages(1l));
		//test normal execution ooc msg
		when(tom.isRetrievingState()).thenReturn(true);
		assertFalse(mng.checkLimits(new Propose(2l, 0, 1, 0, null,null)));
		assertTrue(mng.thereArePendentMessages(2l));
		//test normal execution ooc msg with state transfer
		when(tom.isRetrievingState()).thenReturn(true);
		assertFalse(mng.checkLimits(new Propose(101l, 0, 1, 0, null,null)));
		assertTrue(mng.thereArePendentMessages(101l));
	}
	
	@Test
	public void testThereArePendentMessages() {
		//test initial ooc message
		when(tom.isRetrievingState()).thenReturn(false);
		assertFalse(mng.checkLimits(new Propose(1l, 0, 1, 0, null,null)));
		assertTrue(mng.thereArePendentMessages(1l));
		//test initial ooc message
		when(tom.isRetrievingState()).thenReturn(false);
		assertFalse(mng.checkLimits(new VoteMessage(MessageFactory.WEAK, 2l, 0, 1, new byte[0])));
		assertTrue(mng.thereArePendentMessages(2l));
	}

	@Test
	public void testRemoveExecution() {
		//test initial ooc message
		when(tom.isRetrievingState()).thenReturn(false);
		assertFalse(mng.checkLimits(new Propose( 1l, 0, 1, 0, null,null)));
		assertTrue(mng.thereArePendentMessages(1l));
//		mng.removeExecution(1l);
//		assertFalse(mng.thereArePendentMessages(1l));
		//test initial ooc message
		when(tom.isRetrievingState()).thenReturn(false);
		assertFalse(mng.checkLimits(new VoteMessage(MessageFactory.WEAK, 2l, 0, 1, new byte[0])));
		assertTrue(mng.thereArePendentMessages(2l));
//		mng.removeExecution(2l);
//		assertFalse(mng.thereArePendentMessages(2l));
	}

	@Test
	public void testRemoveOutOfContexts() {
		//test initial ooc message
		when(tom.isRetrievingState()).thenReturn(false);
		assertFalse(mng.checkLimits(new Propose( 1l, 0, 1, 0, null,null)));
		assertTrue(mng.thereArePendentMessages(1l));
		
		//test initial ooc message
		when(tom.isRetrievingState()).thenReturn(false);
		assertFalse(mng.checkLimits(new VoteMessage(MessageFactory.WEAK, 2l, 0, 1, new byte[0])));
		assertTrue(mng.thereArePendentMessages(2l));
		mng.removeOutOfContexts(0);
		assertTrue(mng.thereArePendentMessages(1l));
		assertTrue(mng.thereArePendentMessages(2l));
		mng.removeOutOfContexts(1);
		assertFalse(mng.thereArePendentMessages(1l));
		assertTrue(mng.thereArePendentMessages(2l));
		mng.removeOutOfContexts(2);
		assertFalse(mng.thereArePendentMessages(2l));
	}

	@SuppressWarnings("unused")
	@Test
	public void testGetExecution() {
		Execution exec = mng.getExecution(0l);
//		assertEquals(exec, mng.removeExecution(exec.getId()));
		
		//test initial ooc message
		when(tom.isRetrievingState()).thenReturn(false);
		PaxosMessage msg = new Propose( 1l, 0, 1,  0, null, null);
		VoteMessage weak = new VoteMessage(MessageFactory.WEAK, 1l, 0, 1,new byte[0]);
		assertFalse(mng.checkLimits(msg));
		assertFalse(mng.checkLimits(weak));
//		exec = mng.getExecution(1l);
//		verify(acceptor).processMessage(msg);
//		verify(acceptor).processMessage(weak);
//		assertEquals(exec, mng.removeExecution(exec.getId()));
	}

//	@Test
//	public void testDecided() {
//		mng.getExecution(0l);
//		mng.executionFinished(new Consensus<Object>(0l));
//		verify(mng).executionFinished(0l);
//
//		//verify with removal of stable consensus
//		mng.getExecution(0l);
//		mng.executionFinished(new Consensus<Object>(3l));
//		verify(mng).executionFinished(3l);
//		verify(lm).removeStableConsenusInfo(0l);
//		assertNull(mng.removeExecution(0l));
//	}

	@Test
	public void testDeliverState() {
		when(tom.isRetrievingState()).thenReturn(false);
		assertFalse(mng.checkLimits(new Propose(1l, 0, 1, 0, null,null)));
		assertTrue(mng.thereArePendentMessages(1l));
		
		TransferableState state = new TransferableState(0l,0,0,10l,null,null,null,null);
		mng.getExecution(5l);
		mng.deliverState(state);
//		assertNull(mng.removeExecution(5l));
		assertFalse(mng.thereArePendentMessages(1l));
	}

}
