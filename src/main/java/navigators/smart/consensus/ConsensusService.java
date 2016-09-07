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
 * You should have received a copy of the GNU General Public License along with SMaRt.  If not, see <http://www.gnu.org/licenses/>.
 */

package navigators.smart.consensus;

import navigators.smart.statemanagment.TransferableState;
import navigators.smart.tom.core.messages.TOMMessage;

/**
 * This Interface represents a generic ConsensusService to be used by the TOMLayer
 * to provide total order multicaste despite f byzantine failures.
 *
 * @author Christian Spann 
 */
public interface ConsensusService {

    /**
     * Returns the number of the last successfully decided round
     * @return The roundnumber of the last descision
     */
    public long getLastExecuted();

    /**
     * Notifies the service of a new request to be decided. This is needed to
     * detect malicious leaders that do not propose messages from some clients.
     * @param msg The message to be decided upon
     */
    public void notifyNewRequest(TOMMessage msg);

    /**
     * Notifies the service thet the given request has been decided. This happens
     * if we receive a state  transfer
     * @param msg
     */
    public void notifyRequestDecided(TOMMessage msg);

    /**
     * Returns the id of this replicas consensus service
     * @return The consensusservice id
     */
    public Integer getId();

    /**
     * Notifies the service of a successful state transfer to indicate leaderchanges
     * and so on. The decided requests need to be indicated seperately because
     * the consensus doesn't know the internal structure of the batches and therefor
     * cannot see which single requests where decided in the transferred state.
     * @param state
     */
    public void deliverState(TransferableState state);

    /**
     * Returns the id of the leader of the given round
     * @param cons The Consensus that we wish to get the Proposer for
     * @return The id of the leader for the given consensus
     */
    public int getProposer(Consensus<?> cons);

    /**
     * Indicates that the layer that uses the service starts to deliver a state
     */
    public void startDeliverState();

    /**
     * Called when the TOMLayer finished the delivery of the request and notified
     * the consensuslayer of all the batched requests that where decided with this
     * consensus.
     * @param cons The finished consensus.
     */
    public void deliveryFinished(Consensus<?> cons);

	public void start();
	
	
//	/** 
//	 * Returns some currently interesting systems parameters to get a good feeling of the
//	 * current state of the system.
//	 */
//	public String getCurrentStats();
//	
//	/**
//	 * Returns a space separated String describing every returned part of the
//	 * getCurrentStats(). Should be directly usable with gnuplot
//	 * @return 
//	 */
//	public String getCurrentStatsHeader();

}
