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
 */
package navigators.smart.paxosatwar.executionmanager;

import java.io.Serializable;
import static navigators.smart.paxosatwar.executionmanager.Round.ROUND_ZERO;

/**
 * This class represents a tuple formed by a round number and the replica ID of
 * that round's leader
 *
 * @author Christian Spann
 */
public class ConsInfo implements Serializable {

	public Integer round;
	public Integer leaderId;

	public ConsInfo(Integer l) {
		this.round = ROUND_ZERO;
		this.leaderId = l;
	}

	public ConsInfo(Integer round, Integer l) {
		this.round = round;
		this.leaderId = l;
	}

	@Override
	public String toString() {
		return "R " + round + " | L " + leaderId;
	}
}