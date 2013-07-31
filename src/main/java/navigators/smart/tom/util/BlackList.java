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
 */package navigators.smart.tom.util;

import java.security.acl.LastOwnerException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class represents the blacklist of the EBAWA algorithm. Servers are
 * distinguished by positive integers. The class takes two arguments: The number
 * of servers present and the number f of faults to tolerate. The blacklist
 * cannot grow beyond f to maintain safety and liveness because more than f
 * malicious leaders cannot be handled anyways. Therefore the oldest entry in
 * the blacklist is discarded in the case where the list would grow beyond f.
 * 
 * @author Christian Spann
 */
public class BlackList {

	private static final Logger log = Logger.getLogger(BlackList.class
			.getName());

	private final TreeSet<Long> blacklist = new TreeSet<Long>();
	private final Set<Integer> whitelist = new HashSet<Integer>();
	private final Set<Integer> listedservers = new HashSet<Integer>();
	private final HashMap<Integer, Long> failedviews = new HashMap<Integer, Long>();
	private int f; // number of allowed malicious servers

	private int servers;

	/**
	 * Creates a blacklist with the given parameters
	 * 
	 * @param servers
	 *            The number of replicas participating
	 * @param f
	 *            The number of tolerated faults
	 */
	public BlackList(int servers, int f) {
		for (int i = 0; i < servers; i++) {
			whitelist.add(i);
		}
		this.servers = servers;
		this.f = f;
	}

	/**
	 * Checks if the leader of the given view (view % #servers) is present in
	 * the blacklist and shall not become leader.
	 * 
	 * @param view
	 *            The view to be checked
	 * @return true if the leader is blacklisted, false otherwhise
	 */
	public boolean contains(long view) {
		return blacklist.contains(Long.valueOf(view % servers));
	}

	/**
	 * Returns the set of servers that are currently expected to be "correct".
	 * 
	 * @return A set of probably correct servers
	 */
	public List<Integer> getCorrect() {
		return new LinkedList<Integer>(whitelist);
	}

	@Override	public String toString() {		return "BlackList [blacklist=" + blacklist + ", whitelist=" + whitelist				+ ", listedservers=" + listedservers + ", failedviews="				+ failedviews + ", f=" + f + ", servers=" + servers + "]";	}	/**
	 * Adds the leader of the given view in the first position of the queue.
	 * 
	 * @param view
	 *            The view where the leader failed to propose
	 */
	@SuppressWarnings("boxing")
	public void addFirst(long view) {
		int server = (int) (view % servers);
		whitelist.remove(server);

		// Check if the views leader is blacklisted already
		Long lastfailedview = failedviews.put(server, view);
		if (lastfailedview != null) {
			if (lastfailedview > view) {
				// Readd the old one as it is newer, we are done
				failedviews.put(server, lastfailedview);
			} else if (lastfailedview < view) {
				// Add the new one to the blacklist and remove the old one
				blacklist.add(view);
				blacklist.remove(lastfailedview);
			}
			// do nothing if we handled this view already
		} else {
			// Handle new additions
			blacklist.add(view);

			// remove last if list > f
			if (blacklist.size() > f) {
				Long oldest = blacklist.pollFirst();
				server = (int) (oldest % servers);
				whitelist.add(server);
			}
			if (log.isLoggable(Level.FINE)) {
				log.fine("blacklisting " + server);
			}
		}
	}

	/**
	 * Replaces the first entry in the blacklist. This is needed to cope with
	 * repeated merge operations.
	 * 
	 * @param view
	 *            The view where the leader failed to propose
	 */
	@SuppressWarnings("boxing")
	public void replaceFirst(long view) {
		long previousview = view - 1;

		// Remove previous view if existant
		if (blacklist.remove(previousview)) {
			int oldserver = (int) (previousview % servers);
			whitelist.add(oldserver);
		}

		addFirst(view);
	}

	/**
	 * Checks if there are no good views in between the last good view and the
	 * view of the propose. and the current one.
	 * 
	 * @param currentview
	 * @param lgv
	 * @return
	 */
	public boolean checkLGView(long currentview, long lgv) {
		if (lgv == currentview - 1) {
			return true;
		} else {
			long tmpview = lgv + 1;
			// check if lgv was last valid skip or propose
			while (tmpview < currentview) {
				// Check if all view in between lgv+1 and currentview are
				// blacklisted which means that they timed out
				if (!blacklist.contains(tmpview)) {
					return false;
				}
				// if(!array[(int)++currentview % servers]){
				// return false;
				// }
			}
			return true;
		}
	}
}
