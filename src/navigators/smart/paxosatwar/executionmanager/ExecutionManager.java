/**
 * Copyright (c) 2007-2009 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
 * 
 * This file is part of SMaRt.
 * 
 * SMaRt is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * SMaRt is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with SMaRt.  If not, see <http://www.gnu.org/licenses/>.
 */

package navigators.smart.paxosatwar.executionmanager;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.consensus.Consensus;
import navigators.smart.consensus.MeasuringConsensus;
import navigators.smart.paxosatwar.messages.MessageFactory;
import navigators.smart.paxosatwar.messages.PaxosMessage;
import navigators.smart.paxosatwar.messages.Propose;
import navigators.smart.paxosatwar.messages.VoteMessage;
import navigators.smart.paxosatwar.requesthandler.RequestHandler;
import navigators.smart.paxosatwar.roles.Acceptor;
import navigators.smart.paxosatwar.roles.Proposer;
import navigators.smart.statemanagment.TransferableState;
import navigators.smart.tom.core.TOMLayer;


/**
 * This classe manages consensus instances. Each execution is a consensus
 * instance. It can have several rounds if there were problems during consensus.
 *
 * @author Alysson
 */
@SuppressWarnings("LoggerStringConcat")
public final class ExecutionManager{
	
	private static final Logger log = Logger.getLogger(ExecutionManager.class.getCanonicalName());

    private LeaderModule lm;

    private Acceptor acceptor; // Acceptor role of the PaW algorithm
    private Proposer proposer; // Proposer role of the PaW algorithm
    private Integer me; // This process ID
    private Integer[] acceptors; // Process ID's of all replicas, including this one
    private Integer[] otherAcceptors; // Process ID's of all replicas, except this one

    private Map<Long, Execution> executions = new TreeMap<Long, Execution>(); // Executions
    private ReentrantLock executionsLock = new ReentrantLock(); //lock for executions table

    // Paxos messages that were out of context (that didn't belong to the execution that was/is is progress
    private Map<Long, List<PaxosMessage>> outOfContext = new HashMap<Long, List<PaxosMessage>>();
    // Proposes that were out of context (that belonged to future executions, and not the one running at the time)
    private Map<Long, Propose> outOfContextProposes = new HashMap<Long, Propose>();
    private ReentrantLock outOfContextLock = new ReentrantLock(); //lock for out of context

    private boolean stopped = false; // Is the execution manager stopped?
    // When the execution manager is stopped, incoming paxos messages are stored here
    private Queue<PaxosMessage> stoppedMsgs = new LinkedList<PaxosMessage>();
    private Round stoppedRound = null; // round at which the current execution was stoppped
    private ReentrantLock stoppedMsgsLock = new ReentrantLock(); //lock for stopped messages

    public final int quorumF; // f replicas
    public final int quorum2F; // f * 2 replicas
    public final int quorumStrong; // ((n + f) / 2) replicas
    public final int quorumFastDecide; // ((n + 3 * f) / 2) replicas

    private TOMLayer tomLayer; // TOM layer associated with this execution manager
    private RequestHandler requesthandler;
    private long initialTimeout; // initial timeout for rounds
    private int paxosHighMark; // Paxos high mark for consensus instances
    /** ISTO E CODIGO DO JOAO, PARA TRATAR DA TRANSFERENCIA DE ESTADO */
    private int revivalHighMark; // Paxos high mark for consensus instances when this replica EID equals 0
    /******************************************************************/

    /**
     * Creates a new instance of ExecutionManager
     *
     * @param acceptor Acceptor role of the PaW algorithm
     * @param proposer Proposer role of the PaW algorithm
     * @param acceptors Process ID's of all replicas, including this one
     * @param f Maximum number of replicas that can be faulty
     * @param me This process ID
     * @param initialTimeout initial timeout for rounds
     * @param tom The Tomlayer that is used by this instance
     */
    public ExecutionManager(Acceptor acceptor, Proposer proposer,
            Integer[] acceptors, int f, Integer me, long initialTimeout, TOMLayer tom, LeaderModule lm) {
        this.acceptor = acceptor;
        this.proposer = proposer;
        this.acceptors = acceptors;
        this.me = me;
        this.initialTimeout = initialTimeout;
        this.quorumF = f;
        this.quorum2F = 2 * f;
        this.quorumStrong = (int) Math.ceil((acceptors.length + f) / 2);
        this.quorumFastDecide = (int) Math.ceil((acceptors.length + 3 * f) / 2);
        this.lm = lm;
        setTOMLayer(tom);
    }

    /**
     * Sets the TOM layer associated with this execution manager
     * @param tom The TOM layer associated with this execution manager
     */
    private void setTOMLayer(TOMLayer tom) {
        this.tomLayer = tom;
        this.paxosHighMark = tom.getConf().getPaxosHighMark();
        /** ISTO E CODIGO DO JOAO, PARA TRATAR DA TRANSFERENCIA DE ESTADO */
        this.revivalHighMark = tom.getConf().getRevivalHighMark();
        /******************************************************************/
    }

    public void setRequestHandler(RequestHandler reqhandlr){
        this.requesthandler = reqhandlr;
    }

    /**
     * Returns the TOM layer associated with this execution manager
     * @return The TOM layer associated with this execution manager
     */
    public TOMLayer getTOMLayer() {
        return tomLayer;
    }

    /**
     * Returns this process ID
     * @return This process ID
     */
    public Integer getProcessId() {
        return me;
    }

    /**
     * Returns the process ID's of all replicas, including this one
     * @return Array of the process ID's of all replicas, including this one
     */
    public Integer[] getAcceptors() {
        return acceptors;
    }

    /**
     * Returns the process ID's of all replicas, except this one
     * @return Array of the process ID's of all replicas, except this one
     */
    public Integer[] getOtherAcceptors() {
        if (otherAcceptors == null) {
            otherAcceptors = new Integer[acceptors.length - 1];
            int c = 0;
            for (int i = 0; i < acceptors.length; i++) {
                if (!acceptors[i].equals(me)) {
                    otherAcceptors[c++] = acceptors[i];
                }
            }
        }

        return otherAcceptors;
    }

    /**
     * Returns the acceptor role of the PaW algorithm
     * @return The acceptor role of the PaW algorithm
     */
    public Acceptor getAcceptor() {
        return acceptor;
    }

    /**
     * Returns the Proposer role of this PaW algorithm
     * @return The proposer
     */
    public Proposer getProposer(){
        return proposer;
    }

    /**
     * Stops this execution manager
     */
    public void stop() {
    	if(log.isLoggable(Level.FINE))
    		log.fine("(ExecutionManager.stoping) Stoping execution manager");
        stoppedMsgsLock.lock();
        this.stopped = true;
        if (!requesthandler.isIdle()) {
            stoppedRound = getExecution(requesthandler.getInExec()).getLastRound();
            stoppedRound.getTimeoutTask().cancel(false);
            if(log.isLoggable(Level.FINE))
                log.fine("Stopping round " + stoppedRound.getNumber() + " of consensus " + stoppedRound.getExecution().getId());

        }
        stoppedMsgsLock.unlock();
    }

    /**
     * Restarts this execution manager
     */
    public void restart() {
    	if(log.isLoggable(Level.FINE))
            log.fine("Starting execution manager");
        stoppedMsgsLock.lock();
        this.stopped = false;

        // We don't want to use timeouts inside paxos anymore
        /*if (stoppedRound != null) {
            acceptor.scheduleTimeout(stoppedRound);
            stoppedRound = null;
        }*/

        //process stopped messages
        while(!stoppedMsgs.isEmpty()){
            acceptor.processMessage(stoppedMsgs.remove());
        }
        stoppedMsgsLock.unlock();
        if(log.isLoggable(Level.FINE))
            log.fine("Finished stopped messages processing");
    }

    /**
     * Checks if this message can execute now. If it is not possible,
     * it is stored in outOfContextMessages
     *
     * @param msg the received message
     * @return true in case the message can be executed, false otherwise
     */
    public final boolean checkLimits(PaxosMessage msg) {
		try{
			// This lock is required to block the addition of messages during ooc processing
			outOfContextLock.lock();
			Long consId = msg.getNumber();
			Long lastConsId = requesthandler.getLastExec();


			// Old message -> discard. Do not discard messages for the last consensus as it
			// might still freeze.
			if(consId < lastConsId){	
				return false;
			}

			boolean isRetrievingState = tomLayer.isRetrievingState();

			if (isRetrievingState && log.isLoggable(Level.FINEST)) {
				log.finest(" I'm waiting for a state and received " + msg + " at execution " + requesthandler.getInExec() + " last las execution is " + lastConsId);
			}

			boolean canProcessTheMessage = false;

			if (    // this switch is to redirect ooc messages when we are receiving a state transfer
					isRetrievingState || 
					// Check revival bounds -> not idle and lastmsg == -1 (revived indicator) and msgid >= revivalHighmark
					(!(requesthandler.isIdle() && lastConsId.longValue() == -1 && consId.longValue() >= (lastConsId.longValue() + revivalHighMark))
					// Msg is within the low and high marks (or the is replica synchronizing)
					&& (consId.longValue() >= lastConsId.longValue() && (consId.longValue() < (lastConsId.longValue() + paxosHighMark))))) { 

				//just an optimization to avoid calling the lock in normal case
				if(stopped) {
					try {
						stoppedMsgsLock.lock();
						if (stopped) {
							if (log.isLoggable(Level.FINEST)) {
								log.finest("Adding " + msg + " for execution " + consId + " to stopped");
						}
						//store for later execution
						stoppedMsgs.add(msg);
					}} finally {
						stoppedMsgsLock.unlock();
					}
				}

				if (isRetrievingState ||															// add to ooc when retrieving state
						consId.longValue() > (lastConsId.longValue() + 1)) {						// or msg is a normal ooc msg between boundaries
					if (log.isLoggable(Level.FINEST)) {
						log.finest("Adding "+ msg +" to out of context set");
					}
					addOutOfContextMessage(msg);													//store it as an ahead of time message (out of context)
				} else {
					if (log.isLoggable(Level.FINEST)) {
						log.finest("Message for execution " + consId + " can be processed directly");
					}
					canProcessTheMessage = true;													//msg should be processed normally
				}
			} else if ((requesthandler.isIdle() && lastConsId.longValue() == -1 
					&& consId.longValue() >= (lastConsId.longValue() + revivalHighMark))			// Replica is revived and idle TODO this is an unclear case
					|| (consId.longValue() >= (lastConsId.longValue() + paxosHighMark))) {			// Message is beyond highmark
				if (log.isLoggable(Level.FINE)) {
					log.fine(msg + " is beyond the paxos highmark, adding it to ooc set and checking if state transfer is needed");
				}
				addOutOfContextMessage(msg);														//add to ooc 
				tomLayer.requestStateTransfer(me, getOtherAcceptors(), msg.getSender(), consId);	//request statetx to recover from idle state
			}
			return canProcessTheMessage;
		} finally {
			outOfContextLock.unlock();
		}
    }

    /**
     * Informs if there are messages till to be processed associated the specified consensus's execution
     * @param eid The ID for the consensus execution in question
     * @return True if there are still messages to be processed, false otherwise
     */
    public boolean thereArePendentMessages(Long eid) {
    	
    	/******* BEGIN OUTOFCONTEXT CRITICAL SECTION *******/
        outOfContextLock.lock();
        boolean result = outOfContextProposes.get(eid) != null || outOfContext.get(eid) != null;
        if(outOfContextProposes.size()>100 && log.isLoggable(Level.FINER)){
        	log.finer(" oocProposes has size "+ outOfContextProposes.size());
        }
        outOfContextLock.unlock();
        /******* END OUTOFCONTEXT CRITICAL SECTION *******/
        
        return result;
    }

    /**
     * Removes a consensus's execution from this manager
     * @param id ID of the consensus's execution to be removed
     * @return The consensus's execution that was removed
     */
    public Execution removeExecution(Long id) {
        executionsLock.lock();
        /******* BEGIN EXECUTIONS CRITICAL SECTION *******/

        Execution execution = executions.remove(id);

        /******* END EXECUTIONS CRITICAL SECTION *******/
        executionsLock.unlock();
        
        outOfContextLock.lock();
        /******* BEGIN OUTOFCONTEXT CRITICAL SECTION *******/

        outOfContextProposes.remove(id);
        outOfContext.remove(id);

        /******* END OUTOFCONTEXT CRITICAL SECTION *******/
        outOfContextLock.unlock();

        return execution;
    }
    /** ISTO E CODIGO DO JOAO, PARA TRATAR DA TRANSFERENCIA DE ESTADO
     * Removes all ooc messages including the ones with the currentId
     * @param currentId The Id up to which the oocs shall be removed.
     */
    public void removeOutOfContexts(long currentId) {

        outOfContextLock.lock();
        /******* BEGIN OUTOFCONTEXT CRITICAL SECTION *******/

        for(Iterator<Long> it = outOfContextProposes.keySet().iterator();it.hasNext();){
        	if(it.next().longValue()<=currentId)
        		it.remove();
        }
        for(Iterator<Long> it = outOfContext.keySet().iterator();it.hasNext();){
        	if(it.next().longValue()<=currentId)
        		it.remove();
        }
        if(log.isLoggable(Level.FINE))
        	log.fine("There are "+outOfContext.size()+" msgs left in the ooc list");
        /******* END OUTOFCONTEXT CRITICAL SECTION *******/
        outOfContextLock.unlock();
    }
    /********************************************************/

    /**
     * Returns the specified consensus's execution. If it is not existant yet it is created.
     * If there is a pending propose for this execution it will be handled and so will be any
     * pending out of context message.
     *
     * @param eid ID of the consensus's execution to be returned
     * @return The consensus's execution specified
     */
	@SuppressWarnings("rawtypes")
	public Execution getExecution(Long eid) {
		try{
			executionsLock.lock();
			/******* BEGIN EXECUTIONS CRITICAL SECTION *******/

			Execution execution = executions.get(eid);

			//there is no execution with the given eid
			if (execution == null) {
				//let's create one...
				execution = new Execution(this, new MeasuringConsensus(eid, System.currentTimeMillis()),
						initialTimeout);
				//...and add it to the executions table
				executions.put(eid, execution);

				/******* END EXECUTIONS CRITICAL SECTION *******/				
			}
			return execution;
        
		} finally {
            executionsLock.unlock();
		}

    }


	/**
	 * Handles all OOC Messages that are present for the given execution.
	 * @param eid The Execution ID to process
	 */
    public void processOOCMessages(Long eid) {
		
    	Execution execution = getExecution(eid);
		try {
//			Guard all by the outofcontextlock			
			outOfContextLock.lock();
			//define that end of the prior execution
			requesthandler.setIdle();
			/******* BEGIN OUTOFCONTEXT CRITICAL SECTION *******/
			// First check for a propose...
			if (thereArePendentMessages(eid)){

				VoteMessage prop = outOfContextProposes.remove(eid);
				if (prop != null) {
					if(log.isLoggable(Level.FINER))
						log.finer("(" + eid + ") Processing an out of context propose for "+eid);
					acceptor.processMessage(prop);
				}

				//then we have to put the pending paxos messages
				List<PaxosMessage> messages = outOfContext.remove(eid);
				if (messages != null) {
					if(log.isLoggable(Level.FINER))
						log.finer("(" + eid + ") Processing " + messages.size() + " out of context messages");
					for (Iterator<PaxosMessage> i = messages.iterator(); i.hasNext();) {
						acceptor.processMessage(i.next());
						if (execution.isDecided()) {
							if(log.isLoggable(Level.FINER))
								log.finer(" execution " + eid + " decided.");
							//break; //Should not break here if freezes occurred
							//TODO round checking could be useful but is probably not possible
						}
					}
					if(log.isLoggable(Level.FINER))
						log.finer(" (" + eid + ") Finished out of context processing");
				}
			}
		} finally {
			/******* END OUTOFCONTEXT CRITICAL SECTION *******/
			outOfContextLock.unlock();
		}
	}

	/**
     * Stores a message established as being out of context (a message that
     * doesn't belong to current executing consensus).
     *
     * @param m Out of context message to be stored
     */
    private void addOutOfContextMessage(PaxosMessage m) {
        outOfContextLock.lock();
        /******* BEGIN OUTOFCONTEXT CRITICAL SECTION *******/

		if (m.getPaxosType() == MessageFactory.PROPOSE) {
			outOfContextProposes.put(m.getNumber(), (Propose) m);
		} else {
			List<PaxosMessage> messages = outOfContext.get(m.getNumber());
			if (messages == null) {
				messages = new LinkedList<PaxosMessage>();
				outOfContext.put(m.getNumber(), messages);
			}
			messages.add(m);

			if (log.isLoggable(Level.FINE))
				if (outOfContext.size() > 0 && outOfContext.size() % 1000 == 0) {
					log.fine("out-of-context size: " + outOfContext.size());
				}
		}

        /******* END OUTOFCONTEXT CRITICAL SECTION *******/
        outOfContextLock.unlock();
    }

    @Override
    public String toString() {
        return stoppedMsgs.toString();
    }

    public RequestHandler getRequestHandler() {
        return requesthandler;
    }

	/**
	 * This method is called when the execution of the current consensus is finished
	 * and so it is clear that the results are stable.
	 * 
	 * @param cons The consensus that was finished.
	 */
    public void executionFinished(Consensus<?> cons) {
         

            //define the last stable consensus... the stable consensus can
            //be removed from the leaderManager and the executionManager
            if (cons.getId().longValue() > 2) {
				Long stableConsensus = cons.getId().longValue() - 3;
                lm.removeStableConsenusInfo(stableConsensus);
                removeExecution(stableConsensus);
            }
			
			requesthandler.executionFinished(cons.getId());
			
            //verify if there is a next proposal to be executed
            //(it only happens if the previous consensus were decided in a
            //round > 0
            acceptor.executeAcceptedPendent(requesthandler.getNextExec());
    }

    public void deliverState(TransferableState state){
        Long lastEid = state.lastEid;
         //set this consensus as the last executed
        requesthandler.setLastExec(lastEid);

        //define the last stable consensus... the stable consensus can
        //be removed from the leaderManager and the executionManager
        if (lastEid.longValue() > 2) {
            @SuppressWarnings("boxing")
			Long stableConsensus = lastEid.longValue() - 3;
            executionsLock.lock();
            //tomLayer.lm.removeStableMultipleConsenusInfos(lastCheckpointEid, stableConsensus);
            for(Iterator<Long> it = executions.keySet().iterator();it.hasNext();){
            	Long i = it.next();
            	if(i.longValue() <= stableConsensus.longValue()){
            		it.remove();
            	}
            }
            executionsLock.unlock();
            lm.removeAllStableConsenusInfo(stableConsensus);
            removeOutOfContexts(stableConsensus);
        }

        //define that end of this execution
        //stateManager.setWaiting(-1);
        requesthandler.executionFinished(state.lastEid);
    }
}
