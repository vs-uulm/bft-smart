
package navigators.smart.tom;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.communication.client.CommunicationSystemClientSideFactory;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.BlackList;
import navigators.smart.tom.util.Statistics;
import navigators.smart.tom.util.TOMConfiguration;


/**
 * This class implements a TOMReplyReceiver, and a proxy to be used on the client side of the application.
 * It sends a request to the replicas, receives the reply, and delivers it to the
 * application
 *
 */
public class ServiceProxy extends TOMSender {
	
	public static final Logger log = Logger.getLogger(ServiceProxy.class.getCanonicalName());

	private int n; // Number of total replicas in the system
	private int f; // Number of maximum faulty replicas assumed to occur
	private final Object sync = new Object();
	private int reqId = -1; // request id
	private TOMMessage replies[] = null; // Replies from replicas are stored here
	private byte[] response = null; // Pointer to the reply that is actually delivered to the application
	boolean decided = false;
	private ReplicaHolder r = new ReplicaHolder();
	long timeout = 0; //timeout to wait for the client request
	private BlackList blacklist;
	
	private class ReplicaHolder {
//		private int currentReplica = 0;
		/** Pseudorandom to select a replica to choose */
		private final Random r = new Random();
		
		/**
		 * Returns a random replica that is on the white list.
		 * 
		 * @return A probably correct replica
		 */
		public int getNextRandomReplica() {
			List<Integer> good = blacklist.getCorrect();
			return good.get(r.nextInt(good.size()));
		}
		
		/**
		 * Returns n random replicas from the white list. n must be smaller
		 * than 2f+1 as there might be f bad replicas on the blacklist
		 * @param n
		 * @return 
		 */
		public List<Integer> getNRandomReplicas (int n){
			if(n>f+1){
				throw new IllegalArgumentException(
						"n is "+n+" which is bigger than f+1="+(f+1));
			}
			List<Integer> good = blacklist.getCorrect();
			Collections.shuffle(good);
			return good.subList(0, n);
		}
	}

	/**
	 * Constructor
	 * 
	 * @param id Process id for this client
	 */
	public ServiceProxy(int id) {
		TOMConfiguration conf = new TOMConfiguration(id, "./config");
		init(conf);
	}
	
	/**
	 * Constructor
	 * 
	 * @param id Process id for this client
	 * @param timeout The timeout to wait for the request to finish before printing some logging info.
	 */
	public ServiceProxy(int id, String configdir, long timeout) {
		TOMConfiguration conf = new TOMConfiguration(id, configdir);
		this.timeout = timeout;
		init(conf);
	}

	/**
	 * Constructo
	 * 
	 * @param id Process id for this client
	 */
	public ServiceProxy(int id, String configdir) {
		TOMConfiguration conf = new TOMConfiguration(id, configdir);
		init(conf);
	}

	// This method initializes the object
	private void init(TOMConfiguration conf) {
		init(CommunicationSystemClientSideFactory.getCommunicationSystemClientSide(conf), conf);
		n = conf.getN();
		f = conf.getF();
		blacklist = new BlackList(n,f);
		replies = new TOMMessage[n];
		Statistics.init(conf);
	}

	/**
	 * This method sends a request to the replicas, and returns the related reply. This method is
	 * thread-safe.
	 *
	 * @param request Request to be sent
	 * @return The reply from the replicas related to request
	 */
	public byte[] invoke(byte[] request) {
		return invoke(request, false,false);
	}
	
	public byte[] invoke(byte[] request, boolean readonly) {
		return invoke(request, readonly, false);
	}

	/**
	 * This method sends a request to the replicas, and returns the related reply. This method is
	 * thread-safe.
	 *
	 * @param request Request to be sent
	 * @param readOnly it is a read only request (will not be ordered)
	 * @return The reply from the replicas related to request
	 */
	public byte[] invoke(byte[] request, boolean readOnly, boolean random) {
		// Ahead lies a critical section.
		// This ensures the thread-safety by means of a semaphore
		synchronized (sync) {
			try {
				// Discard previous replies
				Arrays.fill(replies, null);
				response = null;
				TOMMessage tommsg = createTOMMsg(request, readOnly);
//				if(random){
//					Collections.shuffle(group);
//				}
				reqId = getLastSequenceNumber();
				while (!decided){
					List<Integer> targets = new ArrayList<Integer>();
					// Send the request to the replicas, and get its ID
					if (random && !readOnly){
						targets.add(r.getNextRandomReplica());
						log.fine("Sending request "+tommsg.getId()+" to "+targets.get(0));
						doTOUnicast(targets.get(0) ,tommsg);
					} else if (readOnly){
						targets = r.getNRandomReplicas(f+1);
						log.fine("Sending request "+tommsg.getId()+" to "+targets);
						doTOMulticast(tommsg, targets);
					} else {
						log.fine("Multicasting request "+tommsg.getId());
						doTOMulticast(tommsg);		// send to all
					}
					sync.wait(timeout);
					if(!decided){
						handleTimeout(tommsg, random, targets);
					}
				}
				decided = false; //reset
			} catch (InterruptedException ex) {
				Logger.getLogger(ServiceProxy.class.getName()).log(Level.SEVERE, null, ex);
			}
			return response; // return the response
		}
	}

	

	
	/**
	 * This method sends a request to the replicas, and returns the related reply. This method is
	 * thread-safe.
	 *
	 * @param request Request to be sent
	 * @param readOnly it is a read only request (will not be ordered)
	 * @return The reply from the replicas related to request
	 */
	public byte[] invoke(TOMMessage request) {

		// Ahead lies a critical section.
		// This ensures the thread-safety by means of a semaphore
		synchronized (sync) {
			try {
				// Discard previous replies
				Arrays.fill(replies, null);
				response = null;
				// Send the request to the replicas, and get its ID
				doTOMulticast( request);
				reqId = request.getSequence();
				while (!decided){
					sync.wait(timeout);
					if(!decided){
						handleTimeout(request, false, null);
					}
				}
				decided = false; //reset decided
			} catch (InterruptedException ex) {
				Logger.getLogger(ServiceProxy.class.getName()).log(Level.SEVERE, null, ex);
			}

		}

		return response; // return the response
	}

	/**
	 * This is the method invoked by the client side comunication system.
	 *
	 * @param reply The reply delivered by the client side comunication system
	 */
	public void replyReceived(TOMMessage reply) {
		synchronized (sync) {
			int sender = reply.getSender();
			if (sender >= n) { //ignore messages that don't come from replicas
				return;
			}

			// Ahead lies a critical section.
			// This ensures the thread-safety by means of a semaphore
			if (reply.getSequence() == reqId) { // Is this a reply for the last request sent?
				replies[sender] = reply;
				log.log(Level.FINEST,"Received Reply for {0}: {1}", new Object[]{reqId,reply});
				// Compare the reply just received, to the others
				for (int i = 0; i < replies.length; i++) {
					if (replies[i] != null) {
						int sameContent = 1;
						byte[] content = replies[i].getContent();

						for (int j = i + 1; j < replies.length; j++) {
							if (replies[j] != null && Arrays.equals(replies[j].getContent(), content)) {
								sameContent++;

								// if there are more than f equal replies, this cycle can end
								if (sameContent >= f + 1) {
									break;
								}
							}
						}

						// Is there already more than f equal replies?
						if (sameContent >= f + 1) {
							response = content;
							reqId = -1;
							decided = true;
							sync.notify(); // unblocks the thread in invoke
							break;
						}
					}
				}
			}
		}

		// Critical section ends here. The semaphore can be released
	}

	private void handleTimeout(TOMMessage tommsg, boolean random, List<Integer> group) {
		StringBuilder s = new StringBuilder("Timeout while waiting for replies ")
						.append(", got replies from: \n")
						.append(Arrays.toString(replies))
						.append("Targets: ");
		if (random || tommsg.isReadOnlyRequest()) {
			s.append(group);
		} else {
			s.append("ALL");
		}
		s.append(tommsg);
		log.warning(s.toString());
		/* 
		 * Blacklist the evil non proposer for random mode (like ebawa)
		 * or the non repliant replicas for read only requests
		 */
		log.log(Level.FINE,"Blacklist before timeout handling: {0}", new Object[]{blacklist});
		if(random && !tommsg.isReadOnlyRequest()){
			blacklist.addFirst(group.get(0));
		} else if(tommsg.isReadOnlyRequest()){
			for(Integer target:group){
				if(replies[target] == null){
					blacklist.addFirst(target);
				}
			}
		} 
		log.log(Level.FINE,"Blacklist after timeout handling: {0}", new Object[]{blacklist});
	}
}
