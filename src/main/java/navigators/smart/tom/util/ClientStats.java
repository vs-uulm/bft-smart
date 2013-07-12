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
