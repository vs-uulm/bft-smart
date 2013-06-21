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

import java.text.NumberFormat;
import navigators.smart.tom.core.messages.TOMMessage;
import org.apache.commons.math.stat.descriptive.SummaryStatistics;

/**
 *
 * @author Chritian Spann 
 */
class ClientStats {

	//The timestamp of the last received msg
	private long received;
	//The timestamp of the last decoded msg
	private long decoded;
	//The timestamp of the last sent msg
	private long sent;
	//Statistics holding the average decoding time
	private SummaryStatistics decoding = new SummaryStatistics();
	//Statistics holding the average processing time including the agreement
	private SummaryStatistics totalduration = new SummaryStatistics();

	void receivedMsg() {
		received = System.nanoTime();
	}

	void decodedMsg(TOMMessage sm) {
		decoded = System.nanoTime();
		decoding.addValue(decoded - received);
	}

	void sentMsg(TOMMessage sm) {
		sent = System.nanoTime();
		totalduration.addValue(sent - received);
	}

	@Override
	public String toString() {
		//format output nicely
		NumberFormat df = NumberFormat.getNumberInstance();
		//print csv style
		StringBuilder ret = new StringBuilder();
		
		if (decoding.getN() != 0) {
			ret.append(decoding.getN()).append(" ")
					.append(df.format(decoding.getMean())).append(" ")
					.append(df.format(decoding.getStandardDeviation())).append(" ")
					.append(df.format(decoding.getVariance()));
		} else {
			ret.append("0 0 0 0 ");
		}
		ret.append(df.format(totalduration.getMean()) + " "
				+ df.format(totalduration.getStandardDeviation()) + " "
				+ df.format(totalduration.getVariance()));
		
		return ret.toString();
	}
}
