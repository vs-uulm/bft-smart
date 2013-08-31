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

import java.util.HashMap;import java.util.HashSet;import java.util.LinkedList;import java.util.List;import java.util.Map.Entry;import java.util.Set;import java.util.TreeMap;import java.util.logging.Level;import java.util.logging.Logger;/**
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

	private final TreeMap<Long,Integer> viewtoservermap = new TreeMap<Long,Integer>();
	private final Set<Integer> whitelist = new HashSet<Integer>();
//	private final Set<Integer> listedservers = new HashSet<Integer>();
	private final HashMap<Integer, Long> servertoviewmap = new HashMap<Integer, Long>();
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
	public boolean containsLeaderOfView(Long view) {
		return servertoviewmap.containsKey(Integer.valueOf((int)(view % servers)));
	}

	/**
	 * Returns the set of servers that are currently expected to be "correct".
	 * 
	 * @return A set of probably correct servers
	 */
	public List<Integer> getCorrect() {
		return new LinkedList<Integer>(whitelist);
	}

	@Override	public String toString() {		return "BlackList [blacklist=" + viewtoservermap + ", whitelist=" + whitelist				+ ", failedviews="				+ servertoviewmap + ", f=" + f + ", servers=" + servers + "]";	}	/**
	 * Adds the leader of the given view in the first position of the queue.
	 * 
	 * @param view
	 *            The view where the leader failed to propose
	 */
	@SuppressWarnings("boxing")
	public void addFirst(long view) {
		Integer server = (int) (view % servers);
		whitelist.remove(server);

		// Check if the views leader is blacklisted already
		Long lastfailedview = servertoviewmap.put(server, view);
		if (lastfailedview != null) {
			if (lastfailedview > view) {
				// Readd the old one as it is newer, we are done
				servertoviewmap.put(server, lastfailedview);
			} else if (lastfailedview < view) {
				// Add the new one to the blacklist and remove the old one
				viewtoservermap.put(view,server);
				viewtoservermap.remove(lastfailedview);
			}			log.fine("Updating blacklist entry of "+server);
			// do nothing if we handled this view already
		} else {
			// Handle new additions
			viewtoservermap.put(view,server);

			// remove last (smallest) entry which is the first in this case if list > f
			if (viewtoservermap.size() > f) {
				Entry<Long,Integer> oldest = viewtoservermap.pollFirstEntry();
								servertoviewmap.remove(oldest.getValue());
				whitelist.add(oldest.getValue());
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

		// Remove previous view if existant		Integer server;
		if ((server = viewtoservermap.remove(previousview)) !=  null) {			if (log.isLoggable(Level.FINE)) {				log.fine("unblacklisting " + server);			}			servertoviewmap.remove(server);
			whitelist.add(server);
		} else {			log.warning("No entry to replace found for "+previousview);		}

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
				if (!viewtoservermap.containsKey(tmpview)) {
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
