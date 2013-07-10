/**
 * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
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
 * You should have received a copy of the GNU General Public License along with 
 * SMaRt.  If not, see <http://www.gnu.org/licenses/>.
 */

package navigators.smart.paxosatwar.executionmanager;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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

//    private LeaderModule lm;
	public final RunState state = new RunState();
	
    final Acceptor acceptor;	// Acceptor role of the PaW algorithm
    final Proposer proposer;	// Proposer role of the PaW algorithm
	private GroupManager gm;	// Manager for the group of replicas
	
	final ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(5); // scheduler for timeouts

    private Map<Long, Execution> executions = new TreeMap<Long, Execution>(); // Executions
    private ReentrantReadWriteLock executionsLock = new ReentrantReadWriteLock(); //lock for executions table

    // Paxos messages that were out of context (that didn't belong to the execution that was/is is progress
    private Map<Long, List<PaxosMessage>> outOfContext = new HashMap<Long, List<PaxosMessage>>();
    // Proposes that were out of context (that belonged to future executions, and not the one running at the time)
    private Map<Long, List<Propose>> outOfContextProposes = new HashMap<Long, List<Propose>>();
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
	public final int n; //The number of replicas

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
            Integer[] acceptors, int f, Integer me, long initialTimeout, 
			TOMLayer tom) {
        this.acceptor = acceptor;
        this.proposer = proposer;
		this.gm = new GroupManager(acceptors,me);
        this.initialTimeout = initialTimeout;
        this.quorumF = f;
        this.quorum2F = 2 * f;
        this.quorumStrong = (int) Math.ceil((acceptors.length + f) / 2);
        this.quorumFastDecide = (int) Math.ceil((acceptors.length + 3 * f) / 2);
		this.n = acceptors.length;
        setTOMLayer(tom);
		createExecution(0l);
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
        return gm.me;
    }

    /**
     * Returns the process ID's of all replicas, including this one
     * @return Array of the process ID's of all replicas, including this one
     */
    public Integer[] getAcceptors() {
        return gm.acceptors;
    }

    /**
     * Returns the process ID's of all replicas, except this one
     * @return Array of the process ID's of all replicas, except this one
     */
    public Integer[] getOtherAcceptors() {
        return gm.otherAcceptors;
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
        if (!isIdle()) {
            stoppedRound = getExecution(state.getInExec()).getLastRound();
            stoppedRound.cancelTimeout();
            if(log.isLoggable(Level.FINE))
                log.fine("Stopping round " + stoppedRound.getNumber() 
						+ " of consensus " + stoppedRound.getExecution().getId());

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
			Long consId = msg.eid;

			executionsLock.readLock().lock();
			// Old message -> discard. Do not discard messages for the last 
			// consensus as it might still freeze.
			if(consId < state.getLastExec()-1 && ! (executions.containsKey(consId) 
					/*&& executions.get(consId).isActive()*/)){	
				log.log(Level.FINE, "{0} IS OLD - discarding",msg);
				return false;
			}
			executionsLock.readLock().unlock();

			// This lock is required to block the addition of messages during ooc processing
			outOfContextLock.lock();

			//check if we are in a state transfer
			if(handleStateTransfer(msg)){
				return false;
			}

			boolean canProcessTheMessage = false;
			
			if ( 	// Check revival bounds -> not idle and lastmsg == -1 
					//(revived indicator) and msgid >= revivalHighmark
					(!(/*isIdle() &&*/ state.isInStartedState()
					&& consId.longValue() >= (state.getLastExec() + revivalHighMark))
					// Msg is within high marks (or the is replica synchronizing)
					&&  (consId.longValue() < (state.getLastExec() + paxosHighMark)))) { 

				//just an optimization to avoid calling the lock in normal case
				if(stopped) {
					handleStopped(consId, msg);
					return false;
				}

				// msg is a normal ooc msg between boundaries
				if (consId.longValue() >= (state.getNextExecID())) {						
					if (log.isLoggable(Level.FINER)) {
						log.finer("Adding "+ msg +" to out of context set");
					}
					addOutOfContextMessage(msg);
				} else {
					if (log.isLoggable(Level.FINEST)) {
						log.finest("Message for execution " + consId 
								+ " can be processed directly");
					}
					canProcessTheMessage = true;
				}
			} else if ((isIdle() && state.isInStartedState()
					// Replica is revived and idle TODO this is an unclear case
					&& consId.longValue() >= (state.getLastExec() + revivalHighMark))			
					|| (consId.longValue()> 0 
						// Message is beyond highmark
						&& consId.longValue() >= (state.getLastExec() + paxosHighMark))) {			
				if (log.isLoggable(Level.FINE)) {
					log.fine(msg + " is beyond the paxos highmark, adding it to"
							+ " ooc set and checking if state transfer is needed");
				}
				addOutOfContextMessage(msg);
				//request statetx to recover from idle state
				tomLayer.requestStateTransfer(gm.me, getOtherAcceptors(),
						msg.getSender(), consId);	
			} else {
				log.log(Level.WARNING, "{0} missed all statements - DISCARDING...",msg);
			}
			return canProcessTheMessage;
		} finally {
			outOfContextLock.unlock();
		}
    }
	
	private void handleStopped(Long consId, PaxosMessage msg) {
		try {
			stoppedMsgsLock.lock();
			if (stopped) {
				if (log.isLoggable(Level.FINEST)) {
					log.finest("Adding " + msg + " for execution " + consId + " to stopped");
				}
				//store for later execution
				stoppedMsgs.add(msg);
			}
		} finally {
			stoppedMsgsLock.unlock();
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
     * Removes a consensus's execution from this manager. This must be done
     * even if the consensus is active, as we know for sure that at least 2f+1
     * replicas made progress in the following rounds and so have decided.
     * @param id ID of the consensus's execution to be removed
     * @return The consensus's execution that was removed
     */
    public void removeExecution(Long id) {
        executionsLock.writeLock().lock();
        /******* BEGIN EXECUTIONS CRITICAL SECTION *******/

        Execution e = executions.remove(id);
        // Cleanup stuff, clear lists etc.
        e.cleanUp();
       
        /******* END EXECUTIONS CRITICAL SECTION *******/
        executionsLock.writeLock().unlock();
        
        outOfContextLock.lock();
        /******* BEGIN OUTOFCONTEXT CRITICAL SECTION *******/
        
        outOfContextProposes.remove(id);
        outOfContext.remove(id);
        
        /******* END OUTOFCONTEXT CRITICAL SECTION *******/
        outOfContextLock.unlock();
        
//        Execution execution = executions.remove(id);

        

//        return execution;
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
	public Execution createExecution(Long eid) {
		try{
			executionsLock.writeLock().lock();
			/******* BEGIN EXECUTIONS CRITICAL SECTION *******/
			Execution execution = executions.get(eid);

			//there is no execution with the given eid
			if (execution == null && state.getLastExec() < eid) {
				//let's create one...
				execution = new Execution(eid, this, new MeasuringConsensus(eid, System.currentTimeMillis()),
						initialTimeout);
				//...and add it to the executions table
				executions.put(eid, execution);

				/******* END EXECUTIONS CRITICAL SECTION *******/				
				return execution;
			}
			throw new RuntimeException("Creating Execution twice or creating an execution for an old eid");
        
		} finally {
			executionsLock.writeLock().unlock();
		}
    }
	
	/**
	 * Returns the execution for the given execution id
	 * @param eid The id of the desired execution
	 * @return The execution
	 */
	public Execution getExecution(Long eid){
		try{
			executionsLock.readLock().lock();
			return executions.get(eid);
		}finally{
			executionsLock.readLock().unlock();
		}
		
	}


	/**
	 * Handles all OOC Messages that are present for the given execution. This
	 * method must be called ONLY when it is clear, that the execution 
	 * <code>eid</code> is already active, i.e. the previous decision is finished.
	 * 
	 * @param eid The Execution ID to process
	 */
    public void processOOCMessages(Long eid) {
		
    	Execution execution = executions.get(eid);
    	
    	// We already dropped this execution.
    	if(execution == null){
    		return;
    	}
		try {
//			Guard all by the outofcontextlock			
			outOfContextLock.lock();
			
			/******* BEGIN OUTOFCONTEXT CRITICAL SECTION *******/
			// First check for a propose...
			if (thereArePendentMessages(eid)){

				List<Propose> prop = outOfContextProposes.remove(eid);
				if (prop != null) {
					for(VoteMessage v:prop){
						if(log.isLoggable(Level.FINEST))
							log.finest("(" + eid + ") Processing an out of context propose for "+eid);
						acceptor.processMessage(v);
					}
				}

				//then we have to put the pending paxos messages
				List<PaxosMessage> messages = outOfContext.remove(eid);
				if (messages != null) {
					if(log.isLoggable(Level.FINEST))
						log.finest("(" + eid + ") Processing " + messages.size() + " out of context messages");
					for (Iterator<PaxosMessage> i = messages.iterator(); i.hasNext();) {
						acceptor.processMessage(i.next());
						if (execution.isDecided()) {
							if(log.isLoggable(Level.FINER))
								log.finer(" execution " + eid + " decided.");
							//break; //Should not break here if freezes occurred
							//TODO round checking could be useful but is probably not possible
						}
					}
					if(log.isLoggable(Level.FINEST))
						log.finest(" (" + eid + ") Finished out of context processing");
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

		if (m.paxosType == MessageFactory.PROPOSE) {
			List<Propose> p = outOfContextProposes.get(m.eid);
			if(p == null){
				p = new LinkedList<Propose>();
				outOfContextProposes.put(m.eid, p);
			}
			p.add((Propose)m);
		} else {
			List<PaxosMessage> messages = outOfContext.get(m.eid);
			if (messages == null) {
				messages = new LinkedList<PaxosMessage>();
				outOfContext.put(m.eid, messages);
			}
			messages.add(m);

			if (log.isLoggable(Level.FINER))
				if (outOfContext.size() > 0 ) {
					log.finer("out-of-context size: " + outOfContext.size());
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
	
	public void executionDecided(Execution e){		

		//verify if there is a next proposal to be executed
		//(it only happens if the previous consensus were decided in a
		//round > 0
//		acceptor.executeAcceptedPendent(nextExec);

		// Inform tomlayer of the execution
		tomLayer.decided(e.getConsensus());
	}
	
	/**
	 * This method is called when the execution of the current consensus is finished
	 * and so it is clear that the results are stable. Only then, we can start
	 * the next execution to process as we do not have c
	 * 
	 * @param cons The consensus that was finished.
	 */
    public void processingFinished(Consensus<?> cons) {
		Execution e = getExecution(cons.getId());
		if (log.isLoggable(Level.FINER)){
				log.log(Level.FINER,"{0} processing finished, {1} ooc props, "
						+ "{2} executions with ooc msgs",new Object[]{cons.getId(),
						outOfContext.size(),outOfContextProposes.size()});
		}
		if (e != null && !e.isExecuted()){
			e.setExecuted();
			//set this consensus as the last executed
			state.execFinished(e.getId());
			long nextExec = e.getId()+1;
			createExecution(nextExec);
			// Process pending messages for the next execution
			processOOCMessages(nextExec);

			//define the last stable consensus... the stable consensus can
			//be removed from the leaderManager and the executionManager
			if (cons.getId().longValue() > 2) {
				Long stableConsensus = cons.getId().longValue() - 3;
				removeExecution(stableConsensus);
			}
		}
    	requesthandler.notifyChangedConditions();
    }

    public void deliverState(TransferableState txstate){
    	
        Long lastEid = txstate.lastEid;
        //set this consensus as the last executed
		state.deliverState(txstate.lastEid);
		
        //define the last stable consensus... the stable consensus can
        //be removed from the leaderManager and the executionManager
        if (lastEid.longValue() > 2) {
            @SuppressWarnings("boxing")
			Long stableConsensus = lastEid.longValue() - 3;
            executionsLock.writeLock().lock();
            //tomLayer.lm.removeStableMultipleConsenusInfos(lastCheckpointEid, stableConsensus);
            for(Iterator<Long> it = executions.keySet().iterator();it.hasNext();){
            	Long i = it.next();
            	if(i.longValue() <= stableConsensus.longValue()){
            		it.remove();
            	}
            }
            executionsLock.writeLock().unlock();
            
            removeOutOfContexts(stableConsensus);
        }

        //define that end of this execution
        //stateManager.setWaiting(-1);
		// process ooc messages within the ooc lock 
		// idle mode is set within this call to prevent simulataneous message processing of the next consensus
		processOOCMessages(state.getNextExecID());
    }
	
	/**
	 * Starts a new Execution when the requesthandler recognizes that
	 * there are pending requests.
	 * @param value The value to be decided on
	 * @param leader The leader for this execution
	 */
	public void startNextExecution(byte[] value) {
//		try {
//			executionsLock.lock();

			//getExecution and if its not created create it
			Execution exec = getExecution(state.getInExec());

			// Sets the current execution to the upcoming one
			state.startExecution(exec.eid);
			
			// Check if we neet to propose
			proposer.startExecution(exec.eid, value);
//		} finally {
//			executionsLock.unlock();
//		}
	}
	
	/**
	 * Checks if there are active executions in this manager or if the current
	 * execution is still running.
		 * @return 
	 */
	public boolean isIdle() {
		switch (state.state) {
		case RUNNING:
			log.fine("ExecManager is currently in RUNNING state");
			return false;
		default:
			return true;
//			// Check existant executions
//			try {
//				executionsLock.readLock().lock();
//				for (Execution e : executions.values()) {
//					if (e.isActive()) {
//						log.fine(e + " is still active");
//						return false;
//					}
//				}
//				return true;
//			} finally {
//				executionsLock.readLock().unlock();
//			}
		}
//		return inExecution.equals(IDLE) 
//					&& !getExecution(lastExecuted).isActive();
	}

	private boolean handleStateTransfer(PaxosMessage msg) {
		boolean isRetrievingState = tomLayer.isRetrievingState();

			if (isRetrievingState){
				if (log.isLoggable(Level.FINEST)) {
					log.finest(" I'm waiting for a state and received " + msg 
							+ " at execution " + state.getInExec() + " last execution is " 
							+ state.getLastExec());
					}
				//just an optimization to avoid calling the lock in normal case
				if(stopped) {
					handleStopped(msg.eid, msg);
				} else {
					if (log.isLoggable(Level.FINER)) {
						log.finer("Adding "+ msg +" to out of context set");
					}
					addOutOfContextMessage(msg);
				}
				return true;
			}
			return false;
	}
	
	public static class RunState {
		/**
		 * The id of the consensus being executed (or -1 if there is none)
		 */
//		private static final Long STARTED = Long.valueOf(-1l);
		private Long inExecution = 0l;
//		private Long lastExecuted = STARTED;
//		private Long nextExecution = Long.valueOf(0);
		private final ReentrantLock statelock = new ReentrantLock(); // Lock for the state
		
		/*
		 * Indicates if an execution is in progress, or if it has finished
		 * and the next one did not start yet.
		 */
		
		private IdleState state = IdleState.STARTED;
		
		enum IdleState {
			STARTED, IDLE, RUNNING;
		}

//		/**
//		 * Sets which consensus was the last to be executed
//		 *
//		 * @param last ID of the consensus which was last to be executed
//		 */
//		public void setLastExec(Long last) {
//			try {
//				statelock.lock();
//				if (last > lastExecuted) {
//					this.lastExecuted = last;
//				}
//			} finally {
//				statelock.unlock();
//			}
//		}

		/**
		 * Gets the ID of the consensus which was established as the last executed
		 *
		 * @return ID of the consensus which was established as the last executed
		 */
		public Long getLastExec() {
			try {
				statelock.lock();
				return this.inExecution-1;
			} finally {
				statelock.unlock();
			}
		}

		/**
		 * Gets the ID of the consensus which will be executed next
		 *
		 * @return ID of the consensus  which will be executed next
		 */
		public Long getNextExecID() {
			try {
				statelock.lock();
				return this.inExecution+1;
			} finally {
				statelock.unlock();
			}
		}

		/**
		 * Sets which consensus is being executed at the moment. If the value is set to -1 a new Proposal is triggered if this replica is the leader.
		 *
		 * @param inEx ID of the consensus being executed at the moment
		 */
		public void execFinished(Long inEx) {
			try {
				statelock.lock();
				if(!inEx.equals(inExecution)){
					throw new IllegalStateException("We finish an execution ("
							+ inEx 
							+ ") that is not currently running, current: "
							+inExecution);
				}
				if (log.isLoggable(Level.FINEST)) {
					log.finest("Modifying state from " + this.inExecution + " to " + (inExecution+1));
				}
				state = IdleState.IDLE;
				this.inExecution = this.inExecution+1;
			} finally {
				statelock.unlock();
			}
		}
		
		public void startExecution(long exec) {
			try {
				statelock.lock();
				if(exec == inExecution){
					if (log.isLoggable(Level.FINEST)) {
						log.finest("Modifying state from IDLE to RUNNING");
					}
					state = IdleState.RUNNING;
				} else {
					if (log.isLoggable(Level.FINEST)) {
						log.finest("Not modifying state from IDLE to RUNNING, "
								+ "rerunning an old execution");
					}
				}
			} finally {
				statelock.unlock();
			}
		}
		
		public void deliverState(long eid){
			try {
				statelock.lock();
				if (log.isLoggable(Level.FINEST)) {
					log.finest("Setting state to");
				}
				this.inExecution = eid + 1;
//				this.nextExecution = inExecution + 1;
				state = IdleState.RUNNING;
			} finally {
				statelock.unlock();
			}
		}


		/**
		 * Gets the ID of the consensus currently beign executed
		 *
		 * @return ID of the consensus currently beign executed (if no consensus ir executing, -1 is returned)
		 */
		public Long getInExec() {
			try {
				statelock.lock();
				return this.inExecution;
			} finally {
				statelock.unlock();

			}	
		}
		
		/**
		 * Returns true if this executionmanager was just started.
		 * @return
		 */
		public boolean isInStartedState(){
			try{
				statelock.lock();
				return state.equals(IdleState.STARTED);
			} finally {
				statelock.unlock();
			}
		}

//		/**
//		 * Check if this execution is not running, if so, start it.
//		 * @param eid The execution id to check
//		 */
//		public void checkInExec(Long eid) {
//			if (eid.intValue() == nextExecution) {
//				nextExecution();
//			}
//		}
	}
}
