/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package navigators.smart.tom.util;

import java.text.NumberFormat;
import navigators.smart.tom.core.messages.TOMMessage;
import org.apache.commons.math.stat.descriptive.SummaryStatistics;

/**
 *
 * @author Chritian Spann <christian.spann at uni-ulm.de>
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
