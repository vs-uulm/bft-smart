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
 */package navigators.smart.tom.core.timer.messages;

import java.io.Serializable;
import java.security.SignedObject;

/**
 * This class serves as placeholder for the proofs of a RT leader change
 * 
 */

public class RTLeaderChange implements Serializable {

    /**
	 * 
	 */
	private static final long serialVersionUID = -2335883456723868265L;
	public SignedObject[] proof; // Proofs for the new leader
    public Integer newLeader; // Replica ID of the new leader
    public Long start; // ID of the consensus to be started

    /**
     * Creates a new instance of RTLeaderChangeMessage
     * @param proof Proofs for the new leader
     * @param nl Replica ID of the new leader
     * @param start ID of the consensus to be started
     */
    public RTLeaderChange(SignedObject[] proof, Integer nl, Long start) {
        this.proof = proof;
        this.newLeader = nl;
        this.start = start;
    }

    /**
     * Checks if the new leader for the consensus being started is valid
     * according to an array of RTCollect proofs
     *
     * @param collect RTCoolect proofs that can confirm (or not) that newLeader is valid for the consensus being started
     * @param f MAximum number of faulty replicas that can exist
     * @return True if newLeader is valid, false otherwise
     */
    public boolean isAGoodStartLeader(RTCollect[] collect, int f) {
        int c = 0;
        for (int i = 0; i < collect.length; i++) {
            if (collect[i] != null && collect[i].getNewLeader().equals(newLeader)){
                c++;
            }
        }

        if (c <= f) {
            return false;
        }
        //there are at least f+1 collect messages that indicate newLeader as the new leader

        c = 0;
        for (int i = 0; i < collect.length; i++) {
            if (collect[i] != null && (start.longValue()-1) <= collect[i].getLastConsensus()) {
                c++;
            }
        }

        //returns true if the number of processes that last executed start-1 is greater than f
        return (c > f);
    }
}