/*
 * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, 
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
 */
package navigators.smart.consensus;

/**
 * This class stands for an instance of a consensus which measures the executiontime
 * @param <E> Type of the decidedupon values
 */
public class MeasuringConsensus<E> extends Consensus<E> {

    public final long startTime;            // the consensus start time
    public volatile long executionTime;     // consensus execution time
    public volatile int batchSize = 0;      //number of messages included in the batch

    /**
     * Creates a new instance of MeasuringConsensus
     * @param eid The execution ID for this consensus
     * @param startTime The consensus start time
     */
    public MeasuringConsensus(Long eid, long startTime) {
        super(eid);
        this.startTime = startTime;
    }
	
	@Override
	public void decided(byte[] value, Integer round, Integer proposer){
		executionTime = System.currentTimeMillis() - startTime;
		super.decided(value, batchSize, proposer);
	}
}
