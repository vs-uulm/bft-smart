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
