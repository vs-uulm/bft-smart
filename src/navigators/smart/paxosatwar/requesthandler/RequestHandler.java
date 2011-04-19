/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package navigators.smart.paxosatwar.requesthandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.SignedObject;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.communication.ServerCommunicationSystem;
import navigators.smart.paxosatwar.executionmanager.Execution;
import navigators.smart.paxosatwar.executionmanager.ExecutionManager;
import navigators.smart.paxosatwar.executionmanager.LeaderModule;
import navigators.smart.paxosatwar.executionmanager.ProofVerifier;
import navigators.smart.paxosatwar.executionmanager.Round;
import navigators.smart.paxosatwar.requesthandler.timer.RTInfo;
import navigators.smart.tom.core.TOMLayer;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.core.timer.messages.ForwardedMessage;
import navigators.smart.tom.core.timer.messages.RTCollect;
import navigators.smart.tom.core.timer.messages.RTLeaderChange;
import navigators.smart.tom.core.timer.messages.RTMessage;
import navigators.smart.tom.util.TOMConfiguration;
import navigators.smart.tom.util.TOMUtil;

/**
 * This class handles Requests
 * @author Christian Spann <christian.spann at uni-ulm.de>
 */
public class RequestHandler extends Thread {
    
    private static final Logger log = Logger.getLogger(RequestHandler.class.getCanonicalName());
	
    private static final Long IDLE = Long.valueOf(-1l);
    
    private final ExecutionManager execManager; // Execution manager

    private final LeaderModule lm; // Leader module
    private final ProofVerifier verifier; // Acceptor role of the PaW algorithm

    private final TOMConfiguration conf;

      /** The id of the consensus being executed (or -1 if there is none) */
    private Long inExecution = IDLE;
    private Long lastExecuted = IDLE;
    private Long nextExecution = Long.valueOf(0);
    private Map<Integer, RTInfo> timeoutInfo = new HashMap<Integer, RTInfo>();
    private ReentrantLock lockTI = new ReentrantLock();

     /* The locks and conditions used to wait upon creating a propose */
    private ReentrantLock leaderLock = new ReentrantLock();
    private Condition iAmLeader = leaderLock.newCondition();
    private ReentrantLock messagesLock = new ReentrantLock();
    private Condition haveMessages = messagesLock.newCondition();
    private ReentrantLock proposeLock = new ReentrantLock();
    private Condition canPropose = proposeLock.newCondition();

    /* flag that indicates that the lader changed between the last propose and
    this propose. This flag is changed on updateLeader (to true) and decided
    (to false) and used in run.*/
    private boolean leaderChanged = true;

    private final TOMLayer tomlayer;

    private final ServerCommunicationSystem communication;

    public RequestHandler(ServerCommunicationSystem com, ExecutionManager execmng, LeaderModule lm, ProofVerifier a, TOMConfiguration conf, TOMLayer tom){
        this.execManager = execmng;
        this.lm = lm;
        this.verifier = a;
        this.tomlayer = tom;
        this.conf = conf;
        this.communication = com;

    }

     public void imAmTheLeader() {
        leaderLock.lock();
        iAmLeader.signal();
        leaderLock.unlock();
    }

    /**
     * Sets which consensus was the last to be executed
     * @param last ID of the consensus which was last to be executed
     */
    public void setLastExec(Long last) { // TODO:  Condiçao de corrida?
        this.lastExecuted = last;
        this.nextExecution = new Long(last.longValue()+1);
    }

    /**
     * Gets the ID of the consensus which was established as the last executed
     * @return ID of the consensus which was established as the last executed
     */
    public Long getLastExec() {
        return this.lastExecuted;
    }

    /**
     * Sets which consensus is being executed at the moment. If the value is set to -1 
     * a new Proposal is triggered if this replica is the leader.
     *
     * @param inEx ID of the consensus being executed at the moment
     */
    public void setInExec(Long inEx) {
        if(log.isLoggable(Level.FINEST))
            log.finest("Modifying state from " + this.inExecution + " to " + inEx);

        proposeLock.lock();
        this.inExecution = inEx;
        if (inEx.equals(IDLE) && !tomlayer.isRetrievingState()) { //code of joao for state transfer
            canPropose.signalAll();
        }
        proposeLock.unlock();
    }

    /**
     * This method blocks until the PaW algorithm is finished
     */
    public void waitForPaxosToFinish() {
        proposeLock.lock();
        canPropose.awaitUninterruptibly();
        proposeLock.unlock();
    }

    /**
     * Gets the ID of the consensus currently beign executed
     *
     * @return ID of the consensus currently beign executed (if no consensus ir executing, -1 is returned)
     */
    public Long getInExec() {
        return this.inExecution;
    }

    /**
     * Checks whether the given execution is currently executed
     * @param exec The execution to check
     * @return true if it is currently executed, false if not
     */
    public boolean isInExec(Long exec) {
    	return inExecution.equals(exec);
    }

    /**
     * This is the main code for this thread. It basically waits until this replica becomes the leader,
     * and when so, proposes a value to the other acceptors
     */
    @Override
    public void run() {
        /*
        Storage st = new Storage(BENCHMARK_PERIOD/2);
        long start=-1;
        int counter =0;
         */
        if(log.isLoggable(Level.INFO))
            log.info("Running."); // TODO: isto n podia passar para fora do ciclo?
        while (true) {
            // blocks until this replica learns to be the leader for the current round of the current consensus
            leaderLock.lock();
            if(log.isLoggable(Level.FINER))
                log.finer("Next leader for eid=" + (getNextExec()) + ": " + lm.getLeader(getNextExec()));
            if (!lm.getLeader(getNextExec()).equals(conf.getProcessId())) {
                iAmLeader.awaitUninterruptibly();
            }
            leaderLock.unlock();
             if(log.isLoggable(Level.FINER))
                log.finer("I'm the leader.");

            // blocks until there are requests to be processed/ordered
            messagesLock.lock();
            if (!tomlayer.clientsManager.hasPendingRequests()) {
                haveMessages.awaitUninterruptibly();
            }
            messagesLock.unlock();
             if(log.isLoggable(Level.FINER))
                log.finer("There are messages to be ordered.");

            // blocks until the current consensus finishes
            proposeLock.lock();
            if (!isIdle() && !leaderChanged) { //there is some consensus running and the leader not changed
                if(log.isLoggable(Level.FINER))
                    log.finer("Waiting that consensus " + inExecution + " terminates.");
                canPropose.awaitUninterruptibly();
            }
            proposeLock.unlock();
            if(log.isLoggable(Level.FINER))
                    log.finer("I can try to propose.");
            if ((lm.getLeader(getNextExec()).equals(conf.getProcessId())) && //I'm the leader
                    (tomlayer.clientsManager.hasPendingRequests()) && //there are messages to be ordered TODO this is double checking?
                    (isIdle() || leaderChanged)) { //there is no consensus in execution

                leaderChanged = false;

                // Sets the current execution
                setInExec(nextExecution);

                //getExecution and if its not created create it
                //TODO make this better
                execManager.getExecution(inExecution);

                execManager.getProposer().startExecution(inExecution,tomlayer.createPropose());

            /*
            if (counter>=BENCHMARK_PERIOD/2)
            st.store(System.nanoTime()-start);

            counter++;
             */
            } else {
                /*
                System.out.println("I should be the leader, there should be messages to order and no consensus running:");
                System.out.println(">>leader: " + lm.getLeader(getLastExec()+1,0));
                System.out.println(">>consenso em exec?: " + getInExec());
                 */
            }
        /*
        if (st.getCount()==BENCHMARK_PERIOD/2){
        System.out.println("---------------------------------------------");
        System.out.println("CREATE_PROPOSE total delay: Average time for "+BENCHMARK_PERIOD/2+" executions (-10%) = "+st.getAverage(true)/1000+ " us ");
        System.out.println("CREATE_PROPOSE total delay: Standard desviation for "+BENCHMARK_PERIOD/2+" executions (-10%) = "+st.getDP(true)/1000 + " us ");
        System.out.println("CREATE_PROPOSE total delay: Average time for "+BENCHMARK_PERIOD/2+" executions (all samples) = "+st.getAverage(false)/1000+ " us ");
        System.out.println("CREATE_PROPOSE total delay: Standard desviation for "+BENCHMARK_PERIOD/2+" executions (all samples) = "+st.getDP(false)/1000 + " us ");
        System.out.println("CREATE_PROPOSE total delay: Maximum time for "+BENCHMARK_PERIOD/2+" executions (-10%) = "+st.getMax(true)/1000+ " us ");
        System.out.println("CREATE_PROPOSE total delay: Maximum time for "+BENCHMARK_PERIOD/2+" executions (all samples) = "+st.getMax(false)/1000+ " us ");
        System.out.println("---------------------------------------------");

        st = new Storage(BENCHMARK_PERIOD/2);
        counter=0;
        }
         */
        }
    }

	public Long getNextExec() {
		return nextExecution;
	}

    /**
     * Invoked when a timeout for a TOM message is triggered.
     *
     * @param requestList
     * @return True if the request is still pending and the timeout was not triggered before, false otherwise
     */
    public boolean requestTimeout(List<TOMMessage> requestList) {
        List<byte[][]> serializedRequestList = new LinkedList<byte[][]>();

        //verify if the request is still pending
        for (Iterator<TOMMessage> i = requestList.listIterator(); i.hasNext();) {
            TOMMessage request = i.next();
            if (tomlayer.clientsManager.isPending(request.getId())) {
                RTInfo rti = getTimeoutInfo(request.getId());
                if (!rti.isTimeout(conf.getProcessId().intValue())) {
                    serializedRequestList.add(
                            new byte[][]{request.getBytes(), request.serializedMessageSignature});
                    timeout(conf.getProcessId().intValue(), request, rti);
                    if(log.isLoggable(Level.FINE))
                        log.fine("Must send timeout for reqId=" + request.getId());
                }
            }
        }

        if (!requestList.isEmpty()) {
            sendTimeoutMessage(serializedRequestList);
            return true;
        } else {
            return false;
        }
    }

    public void forwardRequestToLeader(TOMMessage request) {
        @SuppressWarnings("boxing")
		Integer leaderId = lm.getLeader(getLastExec() + 1, 0);
        if(log.isLoggable(Level.FINE))
                    log.fine("Forwarding " + request + " to " + leaderId);
        communication.send(new Integer[]{leaderId}, new ForwardedMessage(conf.getProcessId(), request));
    }

    /**
     * Sends a RT-TIMEOUT message to other processes.
     *
     * @param request the message that caused the timeout
     */
    @SuppressWarnings("boxing")
    public void sendTimeoutMessage(List<byte[][]> serializedRequestList) {
        communication.send(execManager.getOtherAcceptors(),
                new RTMessage(TOMUtil.RT_TIMEOUT, -1, conf.getProcessId(), serializedRequestList));
    }

    /**
     * Sends a RT-COLLECT message to other processes
     * TODO: Se se o novo leader for este processo, nao e enviada nenhuma mensagem. Isto estara bem feito?
     * @param reqId ID of the message which triggered the timeout
     * @param collect Proof for the timeout
     */
    public void sendCollectMessage(Integer reqId, RTCollect collect) {
        RTMessage rtm = new RTMessage(TOMUtil.RT_COLLECT, reqId,
                conf.getProcessId(), verifier.sign(collect));

        if (collect.getNewLeader().equals(conf.getProcessId())) {
            RTInfo rti = getTimeoutInfo(reqId);
            collect((SignedObject) rtm.getContent(), conf.getProcessId(), rti);
        } else {
            Integer[] target = {collect.getNewLeader()};
            this.communication.send(target, rtm);
        }

    }

    /**
     * Sends a RT-LEADER message to other processes. It also updates the leader
     *
     * @param reqId ID of the message which triggered the timeout
     * @param timeout Timeout number
     * @param rtLC Proofs for the leader change
     */
    public void sendNewLeaderMessage(Integer reqId, RTLeaderChange rtLC) {
        RTMessage rtm = new RTMessage(TOMUtil.RT_LEADER, reqId, conf.getProcessId(), rtLC);
        //br.ufsc.das.util.Logger.println("Atualizando leader para "+rtLC.newLeader+" a partir de "+rtLC.start);
        updateLeader(reqId, rtLC.start, rtLC.newLeader);

        communication.send(execManager.getOtherAcceptors(), rtm);
    }

    /**
     * Updates the leader of the PaW algorithm. This is triggered upon a timeout
     * for a pending message.
     *
     * @param reqId ID of the message which triggered the timeout
     * @param start Consensus where the new leader belongs
     * @param newLeader Replica ID of the new leader
     * @param timeout Timeout number
     */
	private void updateLeader(Integer reqId, Long start, Integer newLeader) {
        lm.addLeaderInfo(start, Round.ROUND_ZERO, newLeader); // update the leader
        leaderChanged = true;

        leaderLock.lock(); // Signal the TOMlayer thread, if this replica is the leader
        if (lm.getLeader(getNextExec()).equals(conf.getProcessId())) {
            iAmLeader.signal();
        }
        leaderLock.unlock();

        removeTimeoutInfo(reqId); // remove timeout infos
        //requestsTimer.startTimer(clientsManager.getPending(reqId)); // restarts the timer
        execManager.restart(); // restarts the execution manager
    }

    /**
     * This method is invoked when the comunication system needs to deliver a message related to timeouts
     * for a pending TOM message
     * @param msg The timeout related message being delivered
     */
    @SuppressWarnings("unchecked")
	public void deliverTimeoutRequest(RTMessage msg) {
        switch (msg.getRTType()) {
            case TOMUtil.RT_TIMEOUT:
                 {
                     if(log.isLoggable(Level.FINE))
                        log.fine("Receiving timeout message from " + msg.getSender());
                    List<byte[][]> serializedRequestList = (List<byte[][]>) msg.getContent();

                    for (Iterator<byte[][]> i = serializedRequestList.iterator(); i.hasNext();) {
                        byte[][] serializedRequest = i.next();

                        if (serializedRequest == null || serializedRequest.length != 2) {
                            return;
                        }

                        TOMMessage request = null;

                        //deserialize the message
                        try {
                            ByteBuffer buf = ByteBuffer.wrap(serializedRequest[0]);
                            request = new TOMMessage(buf);
                        } catch (Exception e) {
                            e.printStackTrace();
                            if(log.isLoggable(Level.WARNING))
                                log.warning("Invalid request.");
                            return;
                        }

                        request.setBytes(serializedRequest[0]);
                        request.serializedMessageSignature = serializedRequest[1];

                        if (tomlayer.clientsManager.requestReceived(request, false, true)) { //Is this a pending message?
                            RTInfo rti = getTimeoutInfo(request.getId());
                            timeout(msg.getSender().intValue(), request, rti);
                        }
                    }
                }
                break;
            case TOMUtil.RT_COLLECT:
                 {
                     if(log.isLoggable(Level.FINE))
                        log.fine("Receiving collect for message " + msg.getReqId() + " from " + msg.getSender());
                    SignedObject so = (SignedObject) msg.getContent();
                    if (verifier.verifySignature(so, msg.getSender().intValue())) { // valid signature?
                        try {
                            RTCollect rtc = (RTCollect) so.getObject();
                            Integer reqId = rtc.getReqId();

                            Integer nl = chooseNewLeader();

                            if (conf.getProcessId().equals(nl) && nl.equals(rtc.getNewLeader())) { // If this is process the new leader?
                                RTInfo rti = getTimeoutInfo(reqId);
                                collect(so, msg.getSender(), rti);
                            }
                        } catch (ClassNotFoundException cnfe) {
                            cnfe.printStackTrace(System.err);
                        } catch (IOException ioe) {
                            ioe.printStackTrace(System.err);
                        }
                    }
                }
                break;
            case TOMUtil.RT_LEADER:
                 {
                     if(log.isLoggable(Level.FINE))
                        log.fine("I received newLeader from " + msg.getSender());
                    RTLeaderChange rtLC = (RTLeaderChange) msg.getContent();
                    RTCollect[] rtc = getValidProofs(msg.getReqId(), rtLC.proof);

                    if (rtLC.isAGoodStartLeader(rtc, conf.getF())) { // Is it a legitm and valid leader?
                        if(log.isLoggable(Level.FINE))
                            log.fine("Updating leader to " + rtLC.newLeader + " onwards from from round " + rtLC.start);
                        updateLeader(msg.getReqId(), rtLC.start, rtLC.newLeader);
                    //FALTA... eliminar dados referentes a consensos maiores q start.
                    }
                }
                break;
        }
    }

    /**
     * Retrieves the timeout information for a given timeout. If the timeout
     * info does not exist, we create one.
     *
     * @param reqId ID of the message which triggered the timeout
     * @return The timeout information
     */
    public RTInfo getTimeoutInfo(Integer reqId) {
        lockTI.lock();
        RTInfo ti = timeoutInfo.get(reqId);
        if (ti == null) {
            ti = new RTInfo(this.conf, reqId);
            timeoutInfo.put(reqId, ti);
        }
        lockTI.unlock();
        return ti;
    }

    /**
     * Removes the timeout information for a given timeout.
     *
     * @param reqId ID of the message which triggered the timeout
     * @return The timeout information
     */
    private void removeTimeoutInfo(Integer reqId) {
        lockTI.lock();
        timeoutInfo.remove(reqId);
        lockTI.unlock();
    }

    /**
     * Invoked by the TOM layer to notify that a  timeout ocurred in a replica, and to
     * compute the necessary tasks
     * @param a Replica ID where this timeout occurred
     * @param request the request that provoked the timeout
     * @param rti the timeout info for this request
     */
    public void timeout(int acceptor, TOMMessage request, RTInfo rti) {
        rti.setTimeout(acceptor);

        Integer reqId = rti.getRequestId();

        if (rti.countTimeouts() > execManager.quorumF && !rti.isTimeout(conf.getProcessId().intValue())) {
            rti.setTimeout(conf.getProcessId().intValue());

            List<byte[][]> serializedRequestList = new LinkedList<byte[][]>();
            serializedRequestList.add(
                    new byte[][]{request.getBytes(), request.serializedMessageSignature});

            sendTimeoutMessage(serializedRequestList);
        /*
        if (requestsTimer != null) {
        requestsTimer.startTimer(clientsManager.getPending(reqId));
        }
         */
        }

        if (rti.countTimeouts() > execManager.quorumStrong && !rti.isCollected()) {
            rti.setCollected();
            /*
            requestsTimer.stopTimer(clientsManager.getPending(reqId));
             */
            execManager.stop();

            Integer newLeader = chooseNewLeader();

            Long last = isIdle() ? getLastExec() : inExecution;

            if(log.isLoggable(Level.FINE))
                        log.fine("Sending COLLECT to " + newLeader +
                    " for " + reqId + " with last execution = " + last);
            sendCollectMessage(reqId, new RTCollect(newLeader, last, reqId));
        }
    }

    /**
     * Invoked by the TOM layer when a collect message is received, and to
     * compute the necessary tasks
     * @param c Proof from the replica that sent the message
     * @param sender ID of the replica which sent the message
     */
    public void collect(SignedObject c, Integer sender, RTInfo rti) {
        rti.setCollect(sender.intValue(), c);

        if (rti.countCollect() > 2 * conf.getF() && !rti.isNewLeaderSent()) {
            rti.setNewLeaderSent();

            SignedObject collect[] = rti.getCollect();

            RTCollect[] rtc = new RTCollect[collect.length];
            for (int i = 0; i < collect.length; i++) {
                if (collect[i] != null) {
                    try {
                        rtc[i] = (RTCollect) collect[i].getObject();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            RTInfo.NextLeaderAndConsensusInfo nextLeaderCons =
                    rti.getStartLeader(rtc, conf.getF());
            RTLeaderChange rtLC = new RTLeaderChange(collect, nextLeaderCons.leader,
                    nextLeaderCons.cons);

            sendNewLeaderMessage(rti.getRequestId(), rtLC);
        }
    }

    @SuppressWarnings("boxing")
	private Integer chooseNewLeader() {
        Integer lastRoundNumber = Round.ROUND_ZERO; //the number of the last round successfully executed

        Execution lastExec = execManager.getExecution(getLastExec());
        if (lastExec != null) {
            Round lastRound = lastExec.getDecisionRound();
            if (lastRound != null) {
                lastRoundNumber = lastRound.getNumber();
            }
        }

        return (lm.getLeader(getLastExec(), lastRoundNumber) + 1) % conf.getN();
    }

    /**
     * Gets an array of valid RTCollect proofs
     *
     * @param reqId ID of the message which triggered the timeout
     * @param timeout Timeout number
     * @param proof Array of signed objects containing the proofs to be verified
     * @return The sub-set of proofs that are valid
     */
    private RTCollect[] getValidProofs(Integer reqId, SignedObject[] proof) {
        Collection<RTCollect> valid = new HashSet<RTCollect>();
        try {
            for (int i = 0; i < proof.length; i++) {
                if (proof[i] != null && verifier.verifySignature(proof[i], i)) { // is the signature valid?
                    RTCollect rtc = (RTCollect) proof[i].getObject();
                    // Does this proof refers to the specified message id and timeout?
                    if (rtc != null && rtc.getReqId().equals(reqId)) {
                        valid.add(rtc);
                    }

                }
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }

        return valid.toArray(new RTCollect[0]); // return the valid proofs ans an array
    }

     public void setIdle() {
         if(log.isLoggable(Level.FINEST))
            log.finest("Setting Requesthandler to idle after " + this.inExecution);

        proposeLock.lock();
        this.inExecution = IDLE;
        //ot.addUpdate();
        canPropose.signalAll();
        proposeLock.unlock();
    }

    public void notifyNewRequest() {
        messagesLock.lock();
        haveMessages.signal();
        messagesLock.unlock();
    }

    	public boolean isIdle() {
		return inExecution.equals(IDLE);
}

    /* ISTO SAO MAIS COISAS DO JOAO, PARA RETIRAR A THREAD OUTOFCONTEXT */
    public void processOutOfContext() {

        Execution execution = null;

        while (true) {

            Long nextExecution = getNextExec();
            if (execManager.thereArePendentMessages(nextExecution)) {
                if (log.isLoggable(Level.FINER)) {
                    log.finer("Starting processing out of context messages for consensus " + nextExecution);
                }
                execution = execManager.getExecution(nextExecution);
                if (log.isLoggable(Level.FINER)) {
                    log.finer("Finished processing out fo context messages for consensus " + nextExecution);
                }
            } else {
                break;
            }
        }
    }
    /********************************************************************/
}