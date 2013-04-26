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

package navigators.smart.paxosatwar.requesthandler.timer;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import navigators.smart.paxosatwar.requesthandler.RequestHandler;
import navigators.smart.tom.core.messages.TOMMessage;


/**
 * This thread serves as a manager for all timers of pending requests.
 *
 */
public class RequestsTimer {
	
	private static final Logger log = Logger.getLogger(RequestsTimer.class.getCanonicalName());

    private Timer timer = new Timer("request timer");
    private RequestTimerTask rtTask = null;
    private RequestHandler reqhandler; // TOM layer
    private long timeout;
    private TreeSet<TOMMessage> watched = new TreeSet<TOMMessage>();
    private ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    /**
     * Creates a new instance of RequestsTimer
     * @param reqhandler The requesthandler that handles incoming requests
     * @param timeout The timeout for requests to be delivered
     */
    public RequestsTimer(RequestHandler reqhandler, long timeout) {
        this.reqhandler = reqhandler;
        this.timeout = timeout;
        rtTask = new RequestTimerTask();
        timer.schedule(rtTask,timeout, timeout);
    }

    /**
     * Creates a timer for the given request
     * @param request Request to which the timer is being createf for
     */
    public void watch(TOMMessage request) {
        //long startInstant = System.nanoTime();
		try {
			rwLock.writeLock().lock();
			watched.add(request);
		} finally {
			rwLock.writeLock().unlock();
		}
    }

    /**
     * Cancels a timer for a given request
     * @param request Request whose timer is to be canceled
     */
    public void unwatch(TOMMessage request) {
        //long startInstant = System.nanoTime();
        rwLock.writeLock().lock();
		try {
	        watched.remove(request);
		} finally {
			rwLock.writeLock().unlock();
		}
    }
    
    /**
     * Cancels all timers that are currently set
     * @author spann
     *
     */
    public void unwatchAll(){
		try{
			rwLock.writeLock().lock();
			if(log.isLoggable(Level.FINE))
					log.log(Level.FINE,"Unwatching all {0} requests.",watched.size());
			watched.clear();
		} finally {
			rwLock.writeLock().unlock();
		}
    }

    class RequestTimerTask extends TimerTask {

        @Override
        /**
         * This is the code for the TimerTask. It executes the timeout for the first
         * message on the watched list.
         */
        public void run() {
			try {
				if(log.isLoggable(Level.FINE))
					log.fine("Timoutthread runs");
				rwLock.readLock().lock();

				LinkedList<TOMMessage> pendingRequests = new LinkedList<TOMMessage>();

				for (Iterator<TOMMessage> i = watched.iterator(); i.hasNext();) {
					TOMMessage request = i.next();
					if ((request.receptionTime + timeout) < System.currentTimeMillis()) {
						pendingRequests.add(request);
					} else {
						break;
					}
				}

				if (!pendingRequests.isEmpty()) {
					//Try to send the request to the leader in case the client did not send
					// the message to the leader
					for (ListIterator<TOMMessage> li = pendingRequests.listIterator(); li.hasNext(); ) {
						TOMMessage request = li.next();
						if (!request.timeout) {
							if(log.isLoggable(Level.FINE)) {
							log.log(Level.FINE, "Messages {0} are forwarded to the Leader due to a timeout", pendingRequests);
						}
							reqhandler.forwardRequestToLeader(request);
							request.timeout = true;
							li.remove();
						}
					}
					// the leader failed to propose this request. Elect a new leader
					if (!pendingRequests.isEmpty()) {
						if(log.isLoggable(Level.FINE)) {
							log.log(Level.FINE, "Timeout for messages: {0}", pendingRequests);
						}
						reqhandler.requestTimeout(pendingRequests);
					}

				}
			} finally {
	            rwLock.readLock().unlock();
			}
        }
    }
}
