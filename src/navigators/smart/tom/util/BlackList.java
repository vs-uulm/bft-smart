/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package navigators.smart.tom.util;

import java.util.Deque;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents the blacklist of the EBAWA algorithm. Servers are distinguished by positive integers.
 * The class takes two arguments: The number of servers present in order to calculate the leader for the
 * given view and the number f of faults to tolerate. The blacklist cannot grow beyond f to maintain safety
 * and liveness because more than f malicious leaders cannot be handled anyways. Therefore the oldest entry
 * in the blacklist is discarded in the case where the list would grow beyond f.
 * 
 * @author Christian Spann <christian.spann at uni-ulm.de>
 */
public class BlackList {
	
	private static final Logger log = Logger.getLogger(BlackList.class.getName());

    private final boolean[] array;
    private final Deque<Integer> queue;
    private int f; //number of allowed malicious servers

    private int servers;

    /**
     * Creates a blacklist with the given parameters
     * @param servers The number of replicas participating 
     * @param f The number of tolerated faults
     */
    public BlackList(int servers,int f) {
        array = new boolean[servers];
        queue = new LinkedList<Integer>();
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
     * Adds the leader of the given view in the first position of
     * the queue.
     * @param view The view where the leader failed to propose
     */
	@SuppressWarnings("boxing")
	public void addFirst(long view){
        int server = (int) (view % servers);
        queue.addFirst(server);
        array[server]=true;
        //remove last if list > f
        if(queue.size()>f){
        	Integer oldest = queue.removeLast();
        	array[oldest]=false;
        }
        if(log.isLoggable(Level.FINE)){
    		log.fine("blacklisting " + server);
    	}
    }

    /**
     * Replaces the first entry in the blacklist. This is needed to cope
     * with repeated merge operations.
     * @param view The view where the leader failed to propose
     */
    @SuppressWarnings("boxing")
	public void replaceFirst(long  view){
        int server = (int)(view % servers);
        if(!queue.isEmpty()){
	        int oldfirst = queue.removeFirst();
	        array[oldfirst] = false;
        }
        queue.addFirst(server);
        array[server]=true;
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
