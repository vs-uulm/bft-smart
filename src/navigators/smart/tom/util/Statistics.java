package navigators.smart.tom.util;

import java.io.*;
import java.net.Inet4Address;
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
import navigators.smart.tom.TOMReceiver;
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
	public static final String RUNNING_STATS_FILE = "runningstats.log";
	public static final String STATS_DIR = "navigators.smart.statsdir";
	public PrintWriter serverstatswriter, clientstatswriter, runningstatswriter;
	private Long[] sent;
	private Long[] recv;
	/** Timeouts on this node */
	private volatile int timeouts;
	/** Viewchanges seen by this node */
	private volatile int viewchanges;
	/** State transfer requests sent by this node */
	private volatile int strequestssent;
	/** State transfer requests received by this node */
	private volatile int strequestsreceived;
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
	// Static reference to have easy access from everywhere
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

			//Setup prefix for replicas 
			String prefix = createPrefix();
			//open statsfiles for writing
			runningstatswriter = createStatsFileWriter(prefix + "_" + RUNNING_STATS_FILE);
			serverstatswriter = createStatsFileWriter( prefix + "_" + SERVER_STATS_FILE);
			clientstatswriter = createStatsFileWriter( prefix + "_" + CLIENT_STATS_FILE);

		} catch (IOException ex) {
			Logger.getLogger(Statistics.class.getName()).log(Level.SEVERE, null, ex);
			System.exit(1);
		}
	}
	
	/**
	 * Checks, creates and returns the stats dir for this jvm instance running
	 * this SMaRt instance
	 * @return The stats directory
	 */
	private static File checkAndGetStatsDir(){
		File statsdir;
		if (System.getProperty(STATS_DIR) != null) {
				statsdir = new File(System.getProperty(STATS_DIR));
		} else {
			statsdir = new File("stats");
		}
		if(!statsdir.exists()){
			//setup stats logging
			statsdir.mkdirs();
		}
		return statsdir;
	}
	
	/**
	 * Creates a statsfile with the given name in the proper stats directory which 
	 * can be specified via the "navigators.smart.statsfile" property
	 * @param name The name of the outputfile
	 * @return The writer to log stats to
	 * @throws IOException 
	 */
	public static PrintWriter createStatsFileWriter(String name) throws IOException{
		return new PrintWriter(new BufferedWriter(new FileWriter(checkAndGetStatsDir() + "/" +name)));
	}
	
	/**
	 * Create a prefix for logfiles using the local hostname and stripping all domains.
	 * @return
	 * @throws IOException 
	 */
	public static String createPrefix() throws IOException{
		String hostname = Inet4Address.getLocalHost().getHostName();
		if (hostname.contains(".")) {
			hostname = hostname.substring(0, hostname.indexOf("."));
		}
		return hostname;
	}

	/**
	 * Extends the output of this Statistics Object by the specified param name. This can be used to identify different output rows with different
	 * parameters.
	 *
	 * @param paramname The name of the parameter that distinguishes the different runs
	 */
	public void extendParam(String paramname) {
		this.paramname = paramname;
	}

	/**
	 * Extend the headers of the printed stats by another statistics. This adds 4 columns to the gnuplot compatible output: the supplied name, StdDev,
	 * Var and 95% (Confidence Interval). These 4 stats will also be printed later on when printstats with the specific stat will be called.
	 *
	 * @param name The name of the statistics to be printed later on.
	 */
	public void extendStats(String name) {
		headerExtension += " "+formatStatsString(name);
	}

	/**
	 * Prints the current statistics to the provided server and clientstats writers into the stats directory of the currently running test.
	 *
	 * @param param The Param must fit the param name that was supplied via extendParam.
	 * @param stats The stats must correspond in their order to the extensions supplied via extendStats.
	 */
	public void printStats(String param, SummaryStatistics... stats) {
		if (!headerPrinted) {
			headerPrinted = true;
			serverstatswriter.println(paramname + " \"Client rtt\" Rtt Decoding Timeouts Viewchanges STReqsSent STReqsReceived" + headerExtension);
			clientstatswriter.println("\"Client Count\" Decoding StdDev Var \"Total Duration\" StdDev Var");
		}
		NumberFormat nf = NumberFormat.getNumberInstance();
		String serverstats = param 
				+ " " + nf.format(crtt.getMean())
				+ " " + nf.format(rtt.getMean())
				+ " " + nf.format(dec.getMean())
				+ " " + timeouts 
				+ " " + viewchanges
				+ " " + strequestssent
				+ " " + strequestsreceived;
		for (int i = 0; i < stats.length; i++) {
			serverstats += " " + formatStats(stats[i]);
		}
		serverstatswriter.println(serverstats);
		serverstatswriter.flush();

		for (Integer i : clientstatsmap.keySet()) {
			clientstatswriter.println(i + " " + clientstatsmap.get(i).toString());
		}
		reset();
	}

	public void printRunningStatsHeader(String header) {
		runningstatswriter.append("Time(ns) ").append(TOMReceiver.getCurrentServerComQueuesNames()).append(' ').append("\"Pending Requests\" ").append(header).append("\n").flush();
	}

	public void printRunningStats(String timestamp, String output) {
		runningstatswriter.append(timestamp).append(' ').append(TOMReceiver.getCurrentServerComQueues()).append(' ').append(TOMReceiver.getCurrentPendingRequests()).append(' ').append(output).append("\n").flush();
	}

	public void printAndClose() {
		printStats("");
		close();
	}

	public void close() {
		serverstatswriter.close();
		clientstatswriter.close();
		runningstatswriter.close();
	}

	/**
	 * Store the start time of this consensus for measurement
	 *
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
	 * Returns the statistics object for this client. If none is existant a new one is created and returned.
	 *
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

	public static String formatStats(SummaryStatistics stats) {
		NumberFormat nf = NumberFormat.getNumberInstance();
		StringBuilder s = new StringBuilder();
		s.append(nf.format(stats.getMean()))
				.append(" ")
				.append(nf.format(stats.getStandardDeviation()))
				.append(" ")
				.append(nf.format(stats.getVariance()))
				.append(" ")
				.append(nf.format(get95ConfidenceIntervalWidth(stats)))
				.append(" ")
				.append(stats.getN());
		return s.toString();
	}
	
	public static String formatStatsString(String statsname){
		return statsname + " StdDev Var 95% N";
	}
	
	/**
	 * Logs a timeout and prints it to the serverstats file when the testrun is finished.
	 */
	public void timeout(){
		timeouts++;
	}
	
	/**
	 * Logs an actual view change and prints it to the serverstats file when the testrun is finished.
	 */
	public void viewChange(){
		viewchanges++;
	}
	
	/**
	 * A state transfer is requested due to a large gap between this replica and the others
	 */
	public void stateTransferRequested(){
		strequestssent++;
	}
	
	/**
	 * A state transfer is received
	 */
	public void stateTransferReqReceived(){
		strequestsreceived++;
	}
}
