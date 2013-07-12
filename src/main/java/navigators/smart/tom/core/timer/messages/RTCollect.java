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

/**
 * This class represents a proof sent by a replica to the leader for the consensus being started
 * 
 */
public class RTCollect implements Serializable{
    
    /**
	 * 
	 */
	private static final long serialVersionUID = -6846631994024920503L;
	private Integer newLeader; // New leader for the next consensus being started
    private Long lastConsensus; // Last consensus executed, or being executed
    private Integer reqId; // Request ID associated with the timeout
    
    /**
     * Creates a new instance of TimerRequestCollect
     * @param newLeader New leader for the next consensus being started
     * @param lastConsensus Last consensus executed, or being executed
     * @param reqId Request ID associated with the timeout
     */
    public RTCollect(Integer newLeader, Long lastConsensus, Integer reqId) {
        this.newLeader = newLeader;
        this.lastConsensus = lastConsensus;
        this.reqId = reqId;
    }

    /**
     * Retrieves the new leader for the next consensus being started
     * @return The new leader for the next consensus being started
     */
    public Integer getNewLeader(){
        return this.newLeader;
    }

    /**
     * Retrieves the request ID associated with the timeout
     * @return The request ID associated with the timeout
     */
    public Integer getReqId(){
        return this.reqId;
    }

    /**
     * Retrieves the last consensus executed, or being executed
     * @return The last consensus executed, or being executed
     */
    public Long getLastConsensus(){
        return this.lastConsensus;
    }
}
