package navigators.smart.tom.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import navigators.smart.consensus.Consensus;
import navigators.smart.tom.core.messages.SystemMessage;
import navigators.smart.tom.core.messages.TOMMessage;
import org.apache.commons.math.stat.descriptive.SummaryStatistics;
import org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics;

/**
 *
 * @author Chritian Spann <christian.spann at uni-ulm.de>
 */
@SuppressWarnings("LoggerStringConcat")
public class Statistics {

	private static final Logger TIMESTAMP_LOGGER = Logger.getLogger("navigators.smart.timestamplogger");
	private static boolean isfine;
	public static final String SERVER_STATS_FILE = "serverstats.log";
	public static final String CLIENT_STATS_FILE = "clientstats.log";
	public static final String STATS_DIR = "navigators.smart.statsdir";
	public PrintWriter serverstatswriter, clientstatswriter;
	private Long[] sent;
	private Long[] recv;
	private boolean isLeader;
	// Vars for dynamic header extension of stats files
	private volatile boolean headerPrinted = false;
	private volatile String paramname = "";
	private volatile String headerExtension = "";
	private long start;
	// Map holding the client statistic objects
	private final Map<Integer, ClientStats> clientstatsmap = Collections.synchronizedMap(new HashMap<Integer, ClientStats>());
	// other maps to store various interesting times
	private final Map<Consensus<?>, Long> consensusstarts = Collections.synchronizedMap(new HashMap<Consensus<?>, Long>());
	// statistics objects
	private final SynchronizedSummaryStatistics rtt = new SynchronizedSummaryStatistics();
	private final SynchronizedSummaryStatistics crtt = new SynchronizedSummaryStatistics();
	private final SynchronizedSummaryStatistics dec = new SynchronizedSummaryStatistics();
	private final SynchronizedSummaryStatistics consensusduration = new SynchronizedSummaryStatistics();
	private final SynchronizedSummaryStatistics decisionduration = new SynchronizedSummaryStatistics();
	public static Statistics stats;

	static {
		TIMESTAMP_LOGGER.setUseParentHandlers(false);
		isfine = TIMESTAMP_LOGGER.isLoggable(Level.FINE);
		FileHandler timingfile;
		try {
			timingfile = new FileHandler("%t/timings.log", false);
			timingfile.setFormatter(new SimpleFormatter());
			timingfile.setLevel(Level.ALL);
			TIMESTAMP_LOGGER.addHandler(timingfile);

		} catch (IOException ex) {
			Logger.getLogger(Statistics.class.getName()).log(Level.SEVERE, null, ex);
		} catch (SecurityException ex) {
			Logger.getLogger(Statistics.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	public static void init(TOMConfiguration conf) {
		stats = new Statistics(conf);
	}

	private Statistics(TOMConfiguration conf) {
		try {
			sent = new Long[conf.getN()];
			recv = new Long[conf.getN()];
			Arrays.fill(sent, 0l);
			Arrays.fill(recv, 0l);
			isLeader = conf.getProcessId() == 0;

			File statsdir;
			if (System.getProperty(STATS_DIR) != null) {
				statsdir = new File(System.getProperty(STATS_DIR));
			} else {
				statsdir = new File("stats");
			}

			//setup stats logging
			statsdir.mkdirs();
			serverstatswriter = new PrintWriter(new BufferedWriter(new FileWriter(statsdir + "/" + conf.getHost(conf.getProcessId()) + "_" + SERVER_STATS_FILE)));
			clientstatswriter = new PrintWriter(new BufferedWriter(new FileWriter(statsdir + "/" + conf.getHost(conf.getProcessId()) + "_" + CLIENT_STATS_FILE)));
			
		} catch (IOException ex) {
			Logger.getLogger(Statistics.class.getName()).log(Level.SEVERE, null, ex);
			System.exit(1);
		}
	}
	
	public void extendParam(String paramname){
		this.paramname += paramname;
	}
	public void extendStats(String name){
		headerExtension += name+" StdDev Var 95%";
	}

	public void printStats(String param, SummaryStatistics ... stats) {
		if(!headerPrinted){
			headerPrinted = true;
			serverstatswriter.println(paramname + "\"Client rtt\" Rtt Decoding" + headerExtension);
			clientstatswriter.println("Client Count Decoding StdDev Var \"Total Duration\" StdDev Var");
		}
		NumberFormat nf = NumberFormat.getNumberInstance();
		String serverstats = nf.format(crtt.getMean()) + " " + nf.format(rtt.getMean()) + " " + nf.format(dec.getMean());
		for(int i = 0;i<stats.length;i++){
			serverstats += " "+stats[i].getMean()+" "+stats[i].getStandardDeviation()+" "+stats[i].getVariance()+" "+get95ConfidenceIntervalWidth(stats[i]);
		}
		serverstatswriter.println(serverstats);
		serverstatswriter.flush();

		for (Integer i : clientstatsmap.keySet()) {
			clientstatswriter.println(i + " " + clientstatsmap.get(i).toString());
		}
		reset();
	}
	
	public void printAndClose() {
		printStats("");
		close();
	}
	
	public void close(){
		serverstatswriter.close();
		clientstatswriter.close();
	}

	/**
	 * Store the start time of this consensus for measurement
	 * @param c 
	 */
	public void consensusStarted(Consensus<TOMMessage> c) {
		consensusstarts.put(c, System.currentTimeMillis());
	}

	public void consensusDone(Consensus<TOMMessage> c) {
		long time = System.currentTimeMillis();
		long consensusstart = consensusstarts.remove(c);
		consensusduration.addValue(time - consensusstarts.remove(c));
	}

	public void decodedMsg(int remoteId, SystemMessage sm) {
		dec.addValue(System.nanoTime() - recv[remoteId]);
		if (isfine) {
			TIMESTAMP_LOGGER.fine("[" + remoteId + "]Decoded msg " + sm);
		}
	}

	public void verifiedMac(int sender, TOMMessage sm) {
		if (isfine) {
			TIMESTAMP_LOGGER.fine("[" + sender + "]Verified mac: " + System.nanoTime() + " : " + sm);
		}
	}

	public void receivedMsg(int remoteId) {
		recv[remoteId] = System.nanoTime();
		if (isfine) {
			TIMESTAMP_LOGGER.fine("[" + remoteId + "]Recv raw: " + System.nanoTime());
		}
	}

	public void newRound() {
		if (isLeader) {
			if (start != 0) {
				long time = System.nanoTime();
				//calculate client round trip time
				crtt.addValue((time - start) / 1000000);
//				//calculate decoding time: current time - max of server replicas
//				dec.addValue((time - getMax(recv)));
				for (int i = 0; i < sent.length; i++) {
					if (sent[i] != 0l && recv[i] != 0l) {
						rtt.addValue((recv[i] - sent[i]) / 1000000);
						sent[i] = 0l;
						recv[i] = 0l;
					}
				}
			}
			start = System.nanoTime();
		}
	}

	public void sentMsgToServer(int remoteId) {
		if (isfine) {
			TIMESTAMP_LOGGER.fine("[" + remoteId + "]Sent msg: " + System.nanoTime());
		}
		if (sent[remoteId] == 0L) {
			sent[remoteId] = System.nanoTime();
		}
	}

	public void receivedMsgFromClient(int sender) {
		newRound();
		getClientStats(sender).receivedMsg();
		if (isfine) {
			TIMESTAMP_LOGGER.fine("[" + sender + "]Recv raw: " + System.nanoTime());
		}
	}

	public void decodedMsgFromClient(int sender, TOMMessage sm) {
		getClientStats(sender).decodedMsg(sm);
		if (isfine) {
			TIMESTAMP_LOGGER.fine("[" + sender + "]Dec raw: " + System.nanoTime() + " : " + sm);
		}
	}

	public void sentMsgToClient(int i, TOMMessage sm) {
		getClientStats(i).sentMsg(sm);
	}

	public void sendingMsgToServer(Integer[] targets, SystemMessage sm) {
		if (isfine) {
			TIMESTAMP_LOGGER.fine("Sending " + sm + " to " + Arrays.toString(targets) + ": " + System.nanoTime());
		}
	}

	private <T extends Comparable<T>> T getMax(T[] a) {
		T max = a[0];
		for (T t : a) {
			max = max.compareTo(t) < 0 ? t : max;
		}
		return max;
	}

	/**
	 * Returns the statistics object for this client. If none is existant
	 * a new one is created and returned.
	 * @param client The client that sent the msg
	 * @return The statistics holder for this client
	 */
	private ClientStats getClientStats(Integer client) {
		ClientStats clientstats = clientstatsmap.get(client);
		if (clientstats == null) {
			clientstats = new ClientStats();
			clientstatsmap.put(client, clientstats);
		}
		return clientstats;
	}

	private void reset() {
		rtt.clear();
		dec.clear();
		decisionduration.clear();
		crtt.clear();
	}
	
	public static double get95ConfidenceIntervalWidth(SummaryStatistics summaryStatistics) {
		double a = 1.960; // 95% confidence interval width for standard deviation
		return a * summaryStatistics.getStandardDeviation() / Math.sqrt(summaryStatistics.getN());
	}
}
