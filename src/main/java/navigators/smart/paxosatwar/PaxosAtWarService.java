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
 */
package navigators.smart.paxosatwar;

import java.util.logging.Level;
import java.util.logging.Logger;
import navigators.smart.consensus.Consensus;
import navigators.smart.consensus.ConsensusService;
import navigators.smart.paxosatwar.executionmanager.ExecutionManager;
import navigators.smart.paxosatwar.executionmanager.LeaderModule;
import navigators.smart.paxosatwar.messages.PaWMessageHandler;
import navigators.smart.paxosatwar.requesthandler.timer.RequestsTimer;
import navigators.smart.statemanagment.StateManager;
import navigators.smart.statemanagment.TransferableState;
import navigators.smart.tom.core.TOMLayer;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.TOMConfiguration;

/**
 *
 * @author Christian Spann 
 */
public class PaxosAtWarService implements ConsensusService {

	private static final Logger log = Logger.getLogger(PaxosAtWarService.class.getCanonicalName());
	/** Module managing the current and past leaders*/
	private final LeaderModule lm;
	/** Manages the seperate executions */
	private final ExecutionManager execmng;
	/** Manage timers for pending requests */
	public RequestsTimer requestsTimer;
	/** Handler for PaWMessages*/
	private final PaWMessageHandler<?> msghandler;
	/** Handler for state management */
	private final StateManager statemgr;
	/** TOM Configuration object */
	private final TOMConfiguration conf;
	/** TOMLayer for state reception */
	private final TOMLayer tom;

	/**
	 * Creates a new PaxosAtWar instance with the given modules that handle
	 * several internal tasks
	 * @param lm The LeaderManager
	 * @param manager The ExecutionManager
	 * @param msghandler The MessageHandler for PaxosAtWar Messages
	 */
	public PaxosAtWarService(LeaderModule lm, ExecutionManager manager, PaWMessageHandler<?> msghandler, TOMConfiguration conf, TOMLayer tom) {
		this.lm = lm;
		this.execmng = manager;
		this.msghandler = msghandler;
		this.statemgr = tom.getStateManager();
		this.conf = conf;
		this.tom = tom;
		//do not create a timer manager if the timeout is 0
		if (conf.getRequestTimeout() == 0) {
			log.info("Not using Requeststimer");
			this.requestsTimer = null;
		} else {
			// Create requests timers manager (a thread)
			// FIXME Requeststimer is not fully implemented and anyways problematic with state transfers.
            this.requestsTimer = new RequestsTimer(manager.getRequestHandler(), conf.getRequestTimeout());
		}
	}

	@Override
	public long getLastExecuted() {
		return execmng.state.getLastExec();
	}

	@Override
	public void notifyNewRequest(TOMMessage msg) {
		if (requestsTimer != null) {
			requestsTimer.watch(msg);
		}
		execmng.getRequestHandler().notifyChangedConditions();
	}

	@Override
	public void notifyRequestDecided(TOMMessage msg) {
		if (requestsTimer != null) {
			requestsTimer.unwatch(msg);
		}
	}

	@Override
	public Integer getId() {
		return execmng.getProcessId();
	}

	@Override
	public String toString() {
		return "Consensus in execution: " + execmng.state.getInExec() + " last executed consensus: " + execmng.state.getLastExec();
	}

	@Override
	public void startDeliverState() {
		//nothing to do here
	}

	@SuppressWarnings("boxing")
	@Override
	public void deliverState(TransferableState state) {
		log.log(Level.FINE, "Delivering state for {0}", state.lastEid);
		if (requestsTimer != null) {
			requestsTimer.unwatchAll(); //clear timer table TODO this is not fully BFT...
		}
		Long lastCheckpointEid = state.lastCheckpointEid;
		Long lastEid = state.lastEid;
		if (state.leadermodulestate != null) {
			try {
				lm.setState(state.leadermodulestate);
			} catch (ClassNotFoundException e) {
				log.severe(e.getLocalizedMessage());
			}
		}
		//add leaderinfo of the last checkpoint
		lm.setLeaderInfo(Long.valueOf(lastCheckpointEid), state.lastCheckpointRound, state.lastCheckpointLeader);
		//add leaderinfo for previous message batches
		for (long eid = lastCheckpointEid + 1; eid <= lastEid; eid++) {
			lm.setLeaderInfo(Long.valueOf(eid), state.getMessageBatch(eid).round, state.getMessageBatch(eid).leader);
		}
		//deliver the state to executionmanager
		execmng.deliverState(state);
		lm.removeAllStableConsenusInfo(state.lastEid-3);
		//check if we need to propose
		execmng.getRequestHandler().notifyChangedConditions();
	}

	@Override
	public void deliveryFinished(Consensus<?> cons) {
		if (conf.isStateTransferEnabled()) {
			if (log.isLoggable(Level.FINER)) {
				log.finer("The state transfer protocol is enabled");
			}
			if (conf.getCheckpoint_period() > 0) {
				if ((cons.getId().longValue() > 0) && ((cons.getId().longValue() % conf.getCheckpoint_period()) == 0)) {
					if (log.isLoggable(Level.FINER)) {
						log.finer("Performing checkpoint for consensus " + cons.getId());
					}

					byte[] recvstate = tom.getState();
					statemgr.saveState(cons.getId(), cons.getDecisionRound(), cons.getProposer(), lm.getState(), recvstate);

				} else {
					if (log.isLoggable(Level.FINER)) {
						log.finer("Storing message batch in the state log for consensus " + cons.getId());
					}
					statemgr.saveBatch(cons.getDecision(), cons.getId(), cons.getDecisionRound(), cons.getProposer());
				}
			}
		}
		execmng.processingFinished(cons);
		if (cons.getId().longValue() > 2) {
			Long stableConsensus = cons.getId().longValue() - 3;
			lm.removeStableConsenusInfo(stableConsensus);
		}
	}

	@Override
	public void start() {
		//nothing to do for paw
	}		@Override	public void shutdown(){		requestsTimer.shutdown();	}
}
