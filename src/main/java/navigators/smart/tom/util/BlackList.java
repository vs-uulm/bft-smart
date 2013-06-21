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

package navigators.smart.tom.util;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents the blacklist of the EBAWA algorithm. Servers are distinguished by positive integers.
 * The class takes two arguments: The number of servers present in order to calculate the leader for the
 * given view and the number f of faults to tolerate. The blacklist cannot grow beyond f to maintain safety
 * and liveness because more than f malicious leaders cannot be handled anyways. Therefore the oldest entry
 * in the blacklist is discarded in the case where the list would grow beyond f.
 * 
 * @author Christian Spann 
 */
public class BlackList {
	
	private static final Logger log = Logger.getLogger(BlackList.class.getName());

    private final boolean[] array;
    private final TreeSet<Long> blacklist = new TreeSet<Long> ();
	private final Set<Integer> whitelist = new HashSet<Integer>();
	private final Set<Integer>listedservers = new HashSet<Integer>();
	private final HashMap<Integer,Long> failedviews = new HashMap<Integer,Long>();
    private int f; //number of allowed malicious servers

    private int servers;

    /**
     * Creates a blacklist with the given parameters
     * @param servers The number of replicas participating 
     * @param f The number of tolerated faults
     */
    public BlackList(int servers,int f) {
        array = new boolean[servers];
		for (int i = 0;i<servers;i++){
			whitelist.add(i);
		}
        this.servers = servers;
        this.f = f;
    }

    /**
     * Checks if the leader of the given view (view % #servers) is
     * present in the blacklist and shall not become leader.
     * @param view The view to be checked
     * @return true if the leader is blacklisted, false otherwhise
     */
    public boolean contains(long view){
        return array[(int) (view%servers)];
    }
	
	/**
	 * Returns the set of servers that are currently expected to be "correct".
	 * @return A set of probably correct servers
	 */
	public List<Integer> getCorrect(){
		return new LinkedList<Integer>(whitelist);
	}

    /**
     * Adds the leader of the given view in the first position of
     * the queue.
     * @param view The view where the leader failed to propose
     */
	@SuppressWarnings("boxing")
	public void addFirst(long view){
        int server = (int) (view % servers);
		whitelist.remove(server);
		
		// Check if the views leader is blacklisted already
		Long lastfailedview = failedviews.put(server,view);
		if(lastfailedview > view){
			//Readd the old one as it is newer, we are done
			failedviews.put(server,lastfailedview);
		} else if(lastfailedview < view) {
			// Add the new one to the blacklist and remove the old one
			blacklist.add(view);
			blacklist.remove(lastfailedview);
		}
		
		// Handle new additions
		if(lastfailedview == null){
			blacklist.add(view);
			
			//remove last if list > f
			if(blacklist.size()>f){
				Long oldest = blacklist.first();
				server = (int) (oldest % servers);
				whitelist.add(server);
				array[server]=false;
			}
			if(log.isLoggable(Level.FINE)){
				log.fine("blacklisting " + server);
			}
		}
    }

    /**
     * Replaces the first entry in the blacklist. This is needed to cope
     * with repeated merge operations.
     * @param view The view where the leader failed to propose
     */
    @SuppressWarnings("boxing")
	public void replaceFirst(long  view){
		long previousview = view-1;
		
		//Remove previous view if existant
		if (blacklist.remove(previousview)){
			int oldserver = (int) (previousview % servers);
			whitelist.add(oldserver);
			array[oldserver] = false;
		}
        
       addFirst(view);
    }
    
    public boolean checkLGView(long current, long lgv){
		if (lgv == current - 1) {
			return true;
		} else {
			//check if lgv was last valid skip or propose
			while (current < lgv-1){
				if(!array[(int)++current % servers]){
					return false;
				}
			}
			return true;
		}
    }
}
