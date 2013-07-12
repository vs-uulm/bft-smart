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

/**
 *
 * @author Christian Spann
 */
public class GroupManager {
	
	public final Integer me; // This process ID
    public final Integer[] acceptors; // Process ID's of all replicas, including this one
    public final Integer[] otherAcceptors; // Process ID's of all replicas, except this one

	GroupManager(Integer[] acceptors, Integer me) {
		this.acceptors = acceptors;
		this.me = me;
		otherAcceptors = new Integer[acceptors.length - 1];
		int c = 0;
		for (int i = 0; i < acceptors.length; i++) {
			if (!acceptors[i].equals(me)) {
				otherAcceptors[c++] = acceptors[i];
			}
		}
	}
}
