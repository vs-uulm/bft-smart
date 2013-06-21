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
package navigators.smart.clientsmanagement;

import java.security.PublicKey;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantLock;

import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.TOMConfiguration;

public class ClientData {

    ReentrantLock clientLock = new ReentrantLock();
    private Integer clientId;
    private PublicKey publicKey = null;
    private int lastMessageReceived = -1;
    private long lastMessageReceivedTime = 0;
    private int lastMessageExecuted = -1;
    private PendingRequests pendingRequests = new PendingRequests();
	private int maxPendingRequests = 1;
    private Queue<TOMMessage> proposedRequests = new LinkedList<TOMMessage>();
    private TOMMessage lastReplySent = null;

    /**
     * Class constructor. Just store the clientId.
     *
     * @param clientId The id of this client
	 * @param maxPending The number of pending Requests stored for this client
     */
    public ClientData(Integer clientId, int maxPending) {
        this.clientId = clientId;
		this.maxPendingRequests = maxPending;
    }
    
    public Integer getClientId() {
        return clientId;
    }

    @SuppressWarnings("boxing")
    public PublicKey getPublicKey() {
        if(publicKey == null) {
            publicKey = TOMConfiguration.getRSAPublicKey(clientId);
        }

        return publicKey;
    }

    public int getLastMessageExecuted() {
        return lastMessageExecuted;
    }

    public int getLastMessageReceived() {
        return lastMessageReceived;
    }

    public long getLastMessageReceivedTime() {
        return lastMessageReceivedTime;
    }

    public void setLastReplySent(TOMMessage lastReplySent) {
        this.lastReplySent = lastReplySent;
    }

    public TOMMessage getLastReplySent() {
        return lastReplySent;
    }
    
	public TOMMessage proposeReq() {
		TOMMessage ret = pendingRequests.poll();
		if(ret != null){
			if(!proposedRequests.add(ret)){
				//if its not possible to add to the proposed list readd to pending
				pendingRequests.addFirst(ret);
				ret = null;
			}
		}
		return ret;
	}

	public boolean hasPendingRequests() {
		return !pendingRequests.isEmpty();
	}

    public TOMMessage getRequestById(Integer reqId) {
		TOMMessage ret =  pendingRequests.getById(reqId);
		if (ret == null){
			for(TOMMessage msg : proposedRequests){
                if (msg.getId().equals(reqId)) {
					ret = msg;
					break;
				}
			}
		}
		return ret;
	}

	public boolean addRequest(TOMMessage request) {
		//Keep pending requests at a reasonable size
		if(pendingRequests.size() > maxPendingRequests) {
			pendingRequests.remove();
		}
		return pendingRequests.add(request);
	}

	public boolean removeRequest(TOMMessage request) {
		lastMessageExecuted = request.getSequence();
		boolean result = pendingRequests.remove(request) || proposedRequests.remove(request);
		//remove outdated messages from this client
		for(Iterator<TOMMessage> it = pendingRequests.iterator();it.hasNext();){
            if (it.next().getSequence() < request.getSequence()) {
				it.remove();
		}
        }
		for(Iterator<TOMMessage> it = proposedRequests.iterator();it.hasNext();){
            if (it.next().getSequence() < request.getSequence()) {
				it.remove();
		}
        }
		return result;
	}

	public int getAllPendingRequests() {
		return pendingRequests.size()+proposedRequests.size();
	}
	
	public int getPendingRequests() {
		return pendingRequests.size();
	}
	
	public int getProposedRequests() {
		return proposedRequests.size();
	}

	/**
	 * Update this client data set with this received message by storing the sequence
	 * number and the reception time.
	 * 
	 * @param request The request to record
	 */
	void recordRequestInfo(TOMMessage request) {
        this.lastMessageReceived = request.getSequence();
        this.lastMessageReceivedTime = request.receptionTime;
	}
}
