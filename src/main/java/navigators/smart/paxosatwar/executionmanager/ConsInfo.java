/**
 * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and
 * the authors indicated in the
 *
 * @author tags
 *
 * This file is part of SMaRt.
 *
 * SMaRt is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * SMaRt is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SMaRt. If not, see <http://www.gnu.org/licenses/>.
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