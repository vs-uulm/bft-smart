/**
 * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
 * 
 * This file is part of SMaRt.
 * 
 * SMaRt is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * SMaRt is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with SMaRt.  If not, see <http://www.gnu.org/licenses/>.
 */

package navigators.smart.paxosatwar.executionmanager;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignedObject;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.paxosatwar.messages.CollectProof;
import navigators.smart.paxosatwar.messages.FreezeProof;
import navigators.smart.tom.core.timer.messages.RTCollect;
import navigators.smart.tom.util.ByteWrapper;
import navigators.smart.tom.util.TOMConfiguration;

/**
 * This class is used to process data relacioned with freezed rounds. Generate
 * proposes - good values, verify the proposed values and so on...
 */
public class ProofVerifier {

	public static final Logger log = Logger.getLogger(ProofVerifier.class
			.getCanonicalName());

	private final int quorumF; // f replicas
	private final int quorumStrong; // (n + f) / 2 replicas
	private final int n;
	private final PublicKey[] publickeys; // public keys of the replicas
	private Signature[] engines;
	private PrivateKey prk; // private key for this replica
	private Signature engine; // Signature engine

	/**
	 * This class comprises all necessary info to compute a good value to
	 * propose when a round was frozen and a new one needs to be started. The
	 * <code>acc</code> list holds all values weakly accepted by at least one
	 * (f+1) correct acceptor. The list <code>poss</code> holds all values (at
	 * most one...) that got possibly accepted by 2f+1 values. <code>val</code>
	 * holds all values proposed in order to use them as a basis if no value got
	 * accepted before by at least f+1 acceptors.
	 * 
	 * @author Christian Spann
	 * 
	 */
	private class RoundInfo {
		final int round;
		final Set<ByteWrapper> acc;
		final Set<ByteWrapper> poss;
		final Set<ByteWrapper> val;
		final Integer proposer;

		public RoundInfo(int round, Set<ByteWrapper> acc,
				Set<ByteWrapper> poss, Set<ByteWrapper> val, Integer prop) {
			this.round = round;
			this.acc = acc;
			this.poss = poss;
			this.val = val;
			this.proposer = prop;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append(round).append(" | Acc:");

			for (ByteWrapper b : acc) {
				sb.append(Arrays.toString(b.value)).append(" | ");
			}
			sb.append("Poss: ");
			for (ByteWrapper b : poss) {
				sb.append(Arrays.toString(b.value)).append(" | ");
			}
			return sb.toString();
		}
	}

	public class GoodInfo {
		public final byte[] val;
		public final Integer proposer;

		public GoodInfo(byte[] val, Integer proposer) {
			super();
			this.val = val;
			this.proposer = proposer;
		}

	}

	/**
	 * Creates a new instance of ProofVerifier
	 * 
	 * @param conf
	 *            TOM configuration
	 */
	public ProofVerifier(TOMConfiguration conf) {
		this.quorumF = conf.getF();
		this.quorumStrong = (conf.getN() + quorumF) / 2;
		this.n = conf.getN();
		// this.numberOfNonces = conf.getNumberOfNonces();

		this.publickeys = TOMConfiguration.getRSAServersPublicKeys();
		try {
			engines = new Signature[publickeys.length];
			int i = 0;
			for (PublicKey key : publickeys) {
				engines[i] = Signature.getInstance("SHA1withRSA");
				engines[i].initVerify(key);
				i++;
			}
			this.prk = TOMConfiguration.getRSAPrivateKey();
			this.engine = Signature.getInstance("SHA1withRSA");
			engine.initSign(prk);
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
		}
	}

	/**
	 * Signs proofs of a freezed consensus
	 * 
	 * @param cp
	 *            Proofs of a freezed consensus
	 * @return Signed proofs
	 */
	public void sign(CollectProof cp) {
		try {
			byte[] serialisedcp = cp.getBytes();
			engine.update(serialisedcp);
			cp.setSignature(engine.sign());
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
		}
	}

	/**
	 * TODO: Nao sei para que serve nem o q e um RTCollect. Mas deve ser da
	 * difusao atomica
	 * 
	 * @param cp
	 * @return Signed
	 */
	public SignedObject sign(RTCollect trc) {
		try {
			return new SignedObject(trc, prk, engine);
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			return null;
		}
	}

	/**
	 * Counts how many proofs are in the given array (how many are different
	 * from null)
	 * 
	 * @param proofs
	 *            Array of proofs, which might have indexes pointing to null
	 * @return Number of proofs in the array
	 */
	public int countProofs(CollectProof[] proofs) {
		int validProofs = 0;
		for (int i = 0; i < proofs.length; i++) {
			if (proofs[i] != null) {
				validProofs++;
			}
		}
		return validProofs;
	}

	// /**
	// * Obtains the value that is considered to be good, as is specified by the
	// PaW algorithm
	// * @param proofs Signed proofs to be evaluated
	// * @param in True if the proofs to be evaluated are from the freezed
	// consensus, false for the proofs from the next consensus
	// * @return The value considered to be good, if any. If such value can't be
	// found, null is returned
	// */
	// public byte[] getGoodValue(SignedObject[] proofs, int r) {
	// try {
	// CollectProof[] cps = new CollectProof[proofs.length];
	// for (int i = 0; i < proofs.length; i++) {
	// if (proofs[i] != null) {
	// cps[i] = (CollectProof) proofs[i].getObject();
	// }
	// }
	// return getGoodValue(cps, r);
	// } catch (Exception e) {
	// log.severe(e.getLocalizedMessage());
	// return null;
	// }
	// }

	// /**
	// * Checks if an specified array of bytes is contained in a given linked
	// list (whose values are arrays of bytes)
	// * @param l Linked list containing arrays of bytes
	// * @param e Array of bytes that is to be search in the linked list
	// * @return True if 'e' is contained in 'l', false otherwise
	// */
	// private boolean containsArray(LinkedList<byte[]> l, byte[] e) {
	// for (Iterator<byte[]> i = l.iterator(); i.hasNext();) {
	// byte[] value = i.next();
	// if (Arrays.equals(value, e)) {
	// return true;
	// }
	// }
	//
	// return false;
	// }

	/**
	 * Obtains the value that is considered to be good, as is specified by the
	 * PaW algorithm
	 * 
	 * @param proofs
	 *            Proofs to be evaluated
	 * @return The value considered to be good, if any, and its proposer. If
	 *         such value can't be found, null is returned
	 */
	public GoodInfo getGoodValue(CollectProof[] proofs, Integer r) {
		List<RoundInfo> infos = buildInfos(proofs);
		/*
		 * condition G2 in Paxos At War Check each round for a possible w
		 */
		for (int i = infos.size() - 1; i >= 0; i--) {
			RoundInfo info = infos.get(i);
			log.log(Level.FINER, "Checking G2 of {0}", info);
			// Check each value in acc
			for (ByteWrapper val : info.acc) {
				if (checkGoodG2(val, info, r, infos)) {
					return new GoodInfo(val.value, info.proposer);
				}
			}
		}

		// No value is in poss -> G1 of the PaW Algorithms can use any value
		for (RoundInfo s : infos) {
			log.log(Level.FINER, "Checking G1 of {0}", s);
			if (!s.poss.isEmpty()) {
				log.severe("No G2 value found, but poss is not empty!");
				return null;
			}
		}
		// No value is in poss -> G1 of the PaW Algorithms can use any value
		for (RoundInfo s : infos) {
			log.log(Level.FINER, "Checking G1 of {0}", s);
			for (ByteWrapper w : s.val) {
				// TODO Check here if w element of I of the PaW
				return new GoodInfo(w.value, null);
			}
		}
		log.severe("No value found to propose - should not happen");
		return null;
	}

	/**
	 * Called by acceptors to verify if some proposal is good, as specified by
	 * the PaW algorithm
	 * 
	 * @param value
	 *            The proposed value
	 * @param proofs
	 *            The proofs to check the value agaisnt
	 * @param r
	 *            The current round where the value shall be proposed
	 * @param leader
	 *            The leader that initially succeeded to propose
	 * 
	 * @return True if the value is good, false otherwise
	 * 
	 */
	public boolean good(byte[] value, CollectProof[] proofs, Integer r,
			Integer leader) {
		ByteWrapper w = new ByteWrapper(value);
		List<RoundInfo> infos = buildInfos(proofs);

		// condition G2 in Paxos At War
		for (RoundInfo s : infos) {
			if (checkGoodG2(w, s, r, infos)) {
				if(s.proposer != leader){
					log.log(Level.WARNING,"Proposed leader {0} for round {1}"
							+ "does not match with CollectInfos leader {2}", 
							new Object[]{leader,r,s.proposer});
					return false;
				}
				return true;
			}
		}

		// condition G1 in Paxos At War
		for (RoundInfo i : infos) {
			if (!i.poss.isEmpty() && i.round < r.intValue()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Check Condition G2 for this value starting from round s
	 * 
	 * @param val
	 *            The value to check
	 * @param info
	 *            The round to start from (w E acc)
	 * @param r
	 *            The current round
	 * @param infos
	 *            The infos of all rounds in this execution
	 * @param leader
	 *            The leader that shall initially have succeeded
	 * @return
	 */
	private boolean checkGoodG2(ByteWrapper val, RoundInfo info, Integer r,
			List<RoundInfo> infos) {
		if (info.acc.contains(val) && info.round < r) {
			boolean good = true;

			// Check if val is in poss beginning with the round where it was in
			// acc
			for (int i = info.round; i < infos.size(); i++) {
				Set<ByteWrapper> currposs = infos.get(i).poss;
				// Check if w only val in poss or poss empty for a t < r
				if (currposs.size() > 2 || currposs.size() == 1
						&& !currposs.contains(val)) {
					good = false;
					log.log(Level.INFO,
							"ACC w of {1} not in poss of {0} and poss not empty",
							new Object[] { i, info.round });
				}
			}
			if (good) {
				return true; // Value was in acc(s) for s < r and in poss(t)
								// with s <= t < r
			}
		}
		log.log(Level.INFO, "w not in acc of {0} or s({0}) >= r({1})",
				new Object[] { info.round, r });
		return false;
	}

	/**
	 * Returns the round number of the next consensus's execution from an array
	 * of proofs
	 * 
	 * @param proof
	 *            Array of proofs which gives out the round number of next
	 *            consensus's execution
	 * @return The number of the round, or -1 if there is not one executing
	 */
	// public Integer getNextExecRound(CollectProof[] proof) {
	// for (int i = 0; i < proof.length; i++) {
	// if (proof[i].getProofs(false) != null) {
	// Integer r = proof[i].getProofs(false).getRound();
	// int c = 1;
	// for (int j = i + 1; j < proof.length; j++) {
	// if (proof[j].getProofs(false) != null) {
	// if (r.equals(proof[j].getProofs(false).getRound())) {
	// c++;
	// }
	// }
	// }
	// if (c > quorumF) {
	// return r;
	// }
	// }
	// }
	// return null;
	// }

	/**
	 * Checks if this is a valid proof
	 * 
	 * @param eid
	 *            Execution ID to match against the proof
	 * @param round
	 *            round number to match against the proof
	 * @param proofs
	 *            Proof to be verified
	 * @return True if valid, false otherwise
	 */
	public boolean validCollectProof(Long eid, Integer round,
			LinkedList<FreezeProof> proofs) {
		if (proofs == null) {
			return false;
		}
		log.log(Level.FINER, "Checking Proofs: {0}", proofs);
		for (FreezeProof p : proofs) {
			if (!p.getEid().equals(eid)) {
				return false;
			}
		}
		if (!proofs.getLast().getRound().equals(round)) {
			return false;
		}
		return true;
	}

	/**
	 * Returns the valid proofs
	 * 
	 * @param eid
	 *            Execution ID to match against the proofs
	 * @param round
	 *            round number to match against the proofs
	 * @param proofs
	 *            Proofs to be verified
	 * @return Array the the valid proofs
	 */
	public CollectProof[] checkValid(Long eid, Integer round,
			CollectProof[] proofs) {
		if (proofs == null) {
			return null;
		}
		Collection<CollectProof> valid = new HashSet<CollectProof>();
		for (int i = 0; i < proofs.length; i++) {
			if (proofs[i] != null && validSignature(proofs[i], i)
					&& validCollectProof(eid, round, proofs[i].getProofs())) {
				valid.add(proofs[i]);
			}
		}
		return valid.toArray(new CollectProof[valid.size()]);
	}

	/**
	 * Checks if a signature is valid
	 * 
	 * @param so
	 *            Signed object
	 * @param sender
	 *            Replica that sent the signed object
	 * @return True if the signature is valid, false otherwise
	 */
	public boolean validSignature(CollectProof so, int sender) {
		try {
			engines[sender].update(so.getBytes());
			return engines[sender].verify(so.getSignature());
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
		}
		return false;
	}

	/**
	 * Checks if a replica is the leader, given an array of proofs.
	 * 
	 * @param l
	 *            Replica to be checked
	 * @param proof
	 *            Proofs to verify the leader against
	 * @return True if 'l' is the leader, false otherwise
	 */
	public boolean isTheLeader(Integer l, CollectProof[] proof) {
		int c = 0;

		// A replica is considered to really be the leader, if more than F
		// proofs have 'getLeader()' set to be 'l'
		for (int i = 0; i < proof.length; i++) {
			if (proof[i] != null && proof[i].getLeader().equals(l)) {
				c++;
			}
		}
		return c > quorumF;
	}

	/**
	 * Builds the roundinfo list for all available collectproofs for the given
	 * round
	 * 
	 * @param proofs
	 *            The available proofs
	 * @param round
	 *            The round to check
	 * @return The roundinfo containing all necessary information to compute a
	 *         good value.
	 */
	private RoundInfo buildRoundInfo(CollectProof[] proofsin, int round) {

		// Copy collectproofs to be able to null values
		CollectProof[] proofs = Arrays.copyOf(proofsin, proofsin.length);
		Set<ByteWrapper> acc = new HashSet<ByteWrapper>();
		Set<ByteWrapper> poss = new HashSet<ByteWrapper>();
		Set<ByteWrapper> vals = getProposedValues(proofsin, round);
		int[] proposers = new int[n];
		Integer proposer = null;

		// Check each Proof for its weak value;
		for (int i = 0; i < proofs.length; i++) {
			// Get weakly and strongly accepted values for this collectproof
			ByteWrapper w = getWeakProof(proofs[i], round);
			ByteWrapper s = getStrongProof(proofs[i], round);
			/*
			 * Check the value if it exists for ACC and POSS, this whole process
			 * is run exactly once in non byzantine conditions, as the proofs
			 * containing the value w will be removed from proof. So it will be
			 * empty from the first match on.
			 */
			if (w != null && !acc.contains(w) && !poss.contains(w)) {
				log.log(Level.FINER, "Checking Proof: {0} w: {1} s:{2}",
						new Object[] { proofs[i], w, s });
				int weakcount = w != null ? 1 : 0;
				int strongcount = s != null ? 1 : 0;
				// Check all later rounds
				for (int j = i + 1; j < proofs.length; j++) {

					// Check other proofs for the same w
					if (proofs[j] != null) {
						ByteWrapper cw = getWeakProof(proofs[j], round); // current
																			// weak
						ByteWrapper cs = getStrongProof(proofs[j], round); // current
																			// strong

						// The other proof is also weakly accepted and the
						// values are equal
						if (cw != null && w.equals(cw)) {
							weakcount++;
							proposers[proofs[i].proposer]++;
							proofs[j] = null; // Eliminate this Proof as it was
												// already counted
						}
						if (cs != null && cs.equals(s)) {
							strongcount++;
						}
					}
				}
				// We found a weakly accepted value > add it to acc.
				if (weakcount > quorumF) {
					acc.add(w);
				}
				/*
				 * We found a possibly strongly accepted value, add it to poss
				 * and honor out the leader that proposed it.
				 */

				if ((weakcount > quorumStrong || strongcount > quorumF)) {
					poss.add(w);
					for (int j = 0; j < n; j++) {
						if (proposers[j] > quorumF) {
							proposer = j;
						}
					}
				}
			}
		}
		RoundInfo ret = new RoundInfo(round, acc, poss, vals, proposer);
		log.log(Level.FINE, "Info for {0}", ret);
		return ret;
	}

	/**
	 * Returns the all proposed Values of this round;.
	 * 
	 * @param p
	 *            The CollectProof to check
	 * @param round
	 *            The round to check
	 * @return The weakly accepted value or null
	 */
	private Set<ByteWrapper> getProposedValues(CollectProof[] p, int round) {
		Set<ByteWrapper> vals = new HashSet<ByteWrapper>();
		for (int i = 0; i < p.length; i++) {
			if (p[i] != null && p[i].getProofs().size() > round
					&& p[i].getProofs().get(round).getValue() != null) {
				vals.add(new ByteWrapper(p[i].getProofs().get(round).getValue()));
			}

		}
		return vals;
	}

	/**
	 * Returns the weakly accepted value for the given round or null if non
	 * exists.
	 * 
	 * @param p
	 *            The CollectProof to check
	 * @param round
	 *            The round to check
	 * @return The weakly accepted value or null
	 */
	private ByteWrapper getWeakProof(CollectProof p, int round) {
		if (p != null && p.getProofs().size() > round
				&& p.getProofs().get(round).isWeak()) {
			return new ByteWrapper(p.getProofs().get(round).getValue());
		}
		return null;
	}

	/**
	 * Returns the weakly accepted value for the given round or null if non
	 * exists.
	 * 
	 * @param p
	 *            The CollectProof to check
	 * @param round
	 *            The round to check
	 * @return The weakly accepted value or null
	 */
	private ByteWrapper getStrongProof(CollectProof p, int round) {
		if (p != null && p.getProofs().size() > round
				&& p.getProofs().get(round).isStrong()) {
			return new ByteWrapper(p.getProofs().get(round).getValue());
		}
		return null;
	}

	/**
	 * Get highest round number for this set of Collects
	 */
	private int getNumberOfRounds(CollectProof[] proofs) {
		int max = 0;
		for (int i = 0; i < proofs.length; i++) {
			if (proofs[i] != null && proofs[i].getProofs().getLast() != null) {
				int curr_max = proofs[i].getProofs().getLast().getRound();
				max = curr_max > max ? curr_max : max;
			}
		}
		return max;
	}

	/**
	 * Builds the Acc and Poss Information for all Rounds in this set of
	 * collects
	 * 
	 * @param proofs
	 *            Proofs to be used to create the set
	 * @return A linked list which stands for the Acc set
	 */
	private List<RoundInfo> buildInfos(CollectProof[] proofs) {
		List<RoundInfo> infos = new LinkedList<RoundInfo>();
		int rounds = getNumberOfRounds(proofs);

		// Check all proofs for the current round until no further rounds are
		// found
		for (int i = 0; i < rounds + 1; i++) {
			infos.add(buildRoundInfo(proofs, i));
		}
		return infos;
	}

	/**
	 * Checks if a signature is valid
	 * 
	 * @param so
	 *            Signed object
	 * @param sender
	 *            Replica that sent the signed object
	 * @return True if the signature is valid, false otherwise
	 */
	public boolean verifySignature(SignedObject so, int sender) {
		try {
			return so.verify(this.publickeys[sender], engine);
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
		}
		return false;
	}
}
