/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package navigators.smart.tom.util;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import navigators.smart.tom.core.messages.SystemMessage;
import navigators.smart.tom.core.messages.TOMMessage;
import org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics;

/**
 *
 * @author Chritian Spann <christian.spann at uni-ulm.de>
 */
public class Statistics {

    private static final Logger log = Logger.getLogger("navigators.smart.timestamplogger");
    private boolean isfine;
    private Long[] sent;
    private Long[] recv;
    private boolean isLeader;
    private long start;
    //Map holding the client statistic objects
    private Map<Integer, ClientStats> clientstatsmap = Collections.synchronizedMap(new HashMap<Integer, ClientStats>());
    private final SynchronizedSummaryStatistics rtt;
    private final SynchronizedSummaryStatistics crtt;
    private final SynchronizedSummaryStatistics dec;
    public static Statistics stats;

    {
        log.setUseParentHandlers(false);
        isfine = log.isLoggable(Level.FINE);
        FileHandler fh;
        try {
            fh = new FileHandler("%t/Timings.log", false);
            fh.setFormatter(new SimpleFormatter());
            fh.setLevel(Level.ALL);
            log.addHandler(fh);
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
        sent = new Long[conf.getN()];
        recv = new Long[conf.getN()];
        Arrays.fill(sent, 0l);
        Arrays.fill(recv, 0l);
        isLeader = conf.getProcessId() == 0;
        crtt = new SynchronizedSummaryStatistics();
        rtt = new SynchronizedSummaryStatistics();
        dec = new SynchronizedSummaryStatistics();
    }

    public void printStats() {
        System.out.println("Client rtt, Rtt, Decoding");
        NumberFormat nf = NumberFormat.getNumberInstance();
        System.out.println(crtt.getMean()
                + ", " + rtt.getMean()
                + ", " + dec.getMean());
        System.out.println("Client stats");
        System.out.println("Decoding,StdDev,Var,Total Duration,StdDev,Var");
        for (ClientStats clientStats : clientstatsmap.values()) {
            System.out.println(clientStats.toString());
        }
    }

    public void sentMsgToClient(int i, TOMMessage sm) {
        getClientStats(i).sentMsg(sm);
    }

    public void decodedMsg(int remoteId, SystemMessage sm) {
        if (isfine) {
            log.fine("[" + remoteId + "]Decoded msg " + sm);
        }
    }

    public void verifiedMac(int sender, TOMMessage sm) {
        if (isfine) {
            log.fine("[" + sender + "]Verified mac: " + System.nanoTime() + " : " + sm);
        }
    }

    public void receivedMsg(int remoteId) {
        recv[remoteId] = System.nanoTime();
        if (isfine) {
            log.fine("[" + remoteId + "]Recv raw: " + System.nanoTime());
        }
    }

    public void newRound() {
        if (isLeader) {
            if (start != 0) {
                long time = System.nanoTime();
                //calculate client round trip time
                crtt.addValue((time - start) / 1000000);
                //calculate decoding time: current time - max of server repliesa
                dec.addValue((time - getMax(recv)));
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
            log.fine("[" + remoteId + "]Sent msg: " + System.nanoTime());
        }
        if (sent[remoteId] == 0L) {
            sent[remoteId] = System.nanoTime();
        }
    }

    public void receivedMsgFromClient(int sender) {
        newRound();
        getClientStats(sender).receivedMsg();
        if (isfine) {
            log.fine("[" + sender + "]Recv raw: " + System.nanoTime());
        }
    }

    public void decodedMsgFromClient(int sender, TOMMessage sm) {
        getClientStats(sender).decodedMsg(sm);
        if (isfine) {
            log.fine("[" + sender + "]Dec raw: " + System.nanoTime() + " : " + sm);
        }
    }

    public void sendingMsgToServer(Integer[] targets, SystemMessage sm) {
        if (isfine) {
            log.fine("Sending " + sm + " to " + Arrays.toString(targets) + ": " + System.nanoTime());
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
}
