/**
 * Copyright (c) 2007-2009 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
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
import java.util.logging.Logger;

import navigators.smart.paxosatwar.messages.CollectProof;
import navigators.smart.paxosatwar.messages.FreezeProof;
import navigators.smart.tom.core.timer.messages.RTCollect;
import navigators.smart.tom.util.ByteWrapper;
import navigators.smart.tom.util.TOMConfiguration;


/**
 * This class is used to process data relacioned with freezed rounds. 
 * Generate proposes - good values, verify the proposed values and so on...
 */
public class ProofVerifier {
	
	public static final Logger log = Logger.getLogger(ProofVerifier.class.getCanonicalName());

    private int quorumF; // f replicas
    private int quorumStrong; // (n + f) / 2 replicas
    private PublicKey[] publickeys; // public keys of the replicas
    private Signature[] engines;
    private PrivateKey prk = null; // private key for this replica
    private Signature engine; // Signature engine
	
	private class RoundInfo {
		final int round;
		final Set<ByteWrapper> acc;
		final Set<ByteWrapper> poss;

		public RoundInfo(int round, Set<ByteWrapper> acc, Set<ByteWrapper> poss) {
			this.round = round;
			this.acc = acc;
			this.poss = poss;
		}
	}

	/**
     * Creates a new instance of ProofVerifier
     * @param conf TOM configuration
     */
    public ProofVerifier(TOMConfiguration conf) {
        this.quorumF = conf.getF();
        this.quorumStrong = (conf.getN() + quorumF) / 2;
//        this.numberOfNonces = conf.getNumberOfNonces();

        this.publickeys = TOMConfiguration.getRSAServersPublicKeys();
        try {
            engines = new Signature[publickeys.length];
            int i = 0;
            for(PublicKey key : publickeys){
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
     * @param cp Proofs of a freezed consensus
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
     * TODO: Nao sei para que serve nem o q e um RTCollect. Mas deve ser da difusao atomica
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
     * Counts how many proofs are in the given array (how many are different from null)
     * @param proofs Array of proofs, which might have indexes pointing to null
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

    /**
     * Obtains the value that is considered to be good, as is specified by the PaW algorithm
     * @param proofs Signed proofs to be evaluated
     * @param in True if the proofs to be evaluated are from the freezed consensus, false for the proofs from the next consensus
     * @return The value considered to be good, if any. If such value can't be found, null is returned
     */
    public byte[] getGoodValue(SignedObject[] proofs, int r) {
        try {
            CollectProof[] cps = new CollectProof[proofs.length];
            for (int i = 0; i < proofs.length; i++) {
                if (proofs[i] != null) {
                    cps[i] = (CollectProof) proofs[i].getObject();
                }
            }
            return getGoodValue(cps, r);
        } catch (Exception e) {
            log.severe(e.getLocalizedMessage());
            return null;
        }
    }

//    /**
//     * Checks if an specified array of bytes is contained in a given linked list (whose values are arrays of bytes)
//     * @param l Linked list containing arrays of bytes
//     * @param e Array of bytes that is to be search in the linked list
//     * @return True if 'e' is contained in 'l', false otherwise
//     */
//    private boolean containsArray(LinkedList<byte[]> l, byte[] e) {
//        for (Iterator<byte[]> i = l.iterator(); i.hasNext();) {
//            byte[] value = i.next();
//            if (Arrays.equals(value, e)) {
//                return true;
//            }
//        }
//
//        return false;
//    }

    /**
     * Obtains the value that is considered to be good, as is specified by the PaW algorithm
     * @param proofs Proofs to be evaluated
     * @param in True if the proofs to be evaluated are from the freezed consensus, false for the proofs from the next consensus
     * @return The value considered to be good, if any. If such value can't be found, null is returned
     */
    public byte[] getGoodValue(CollectProof[] proofs, Integer r) {
        List<RoundInfo> infos = buildInfos(proofs);

        /* condition G2 in Paxos At War 
		   Check each round for a possible w */
		for (RoundInfo s : infos) {
			
			// Check each value in acc
			for(ByteWrapper w:s.acc){
				if (checkGood(w, s, r, infos)) {
					return w.value;
				}
			}
		}

        return null;
    }

    /**
     * Called by acceptors to verify if some proposal is good, as specified by the PaW algorithm
     * @param value The proposed value
     * @param proofs The proofs to check the value agaisnt
     * @param r The current round where the value shall be proposed
	 * 
	 * @return True if the value is good, false otherwise
	 * 
     */
    public boolean good(byte[] value, CollectProof[] proofs, Integer r) {
		ByteWrapper w  = new ByteWrapper(value);
        List<RoundInfo> infos = buildInfos(proofs);

        //condition G2 in Paxos At War 
		for (RoundInfo s : infos) {
			if (checkGood(w, s, r, infos)) {
				return true;
			}
		}
		
        //condition G1 in Paxos At War 
		for(RoundInfo i : infos){
			if (!i.poss.isEmpty() && i.round < r.intValue()) {
				return false;
			}
		}
        return true;
    }
	
	private boolean checkGood( ByteWrapper w, RoundInfo s, Integer r, List<RoundInfo> infos){
		if (s.acc.contains(w) && s.round < r) {
			boolean good = true;

			// Check if val is in poss beginning with the round where it was in acc 
			for (int i = s.round; i < infos.size(); i++) {
				if (!infos.get(i).poss.contains(w)) {
					good = false;
				}
			}
			if (good) {
				return true;	// Value was in acc(s) for s < r and in poss(t) with s <= t < r
			}
		}
		return false;
	}

    /**
     * Returns the round number of the next consensus's execution from an array of proofs
     * @param proof Array of proofs which gives out the round number of next consensus's execution
     * @return The number of the round, or -1 if there is not one executing
     */
//    public Integer getNextExecRound(CollectProof[] proof) {
//        for (int i = 0; i < proof.length; i++) {
//            if (proof[i].getProofs(false) != null) {
//                Integer r = proof[i].getProofs(false).getRound();
//                int c = 1;
//                for (int j = i + 1; j < proof.length; j++) {
//                    if (proof[j].getProofs(false) != null) {
//                        if (r.equals(proof[j].getProofs(false).getRound())) {
//                            c++;
//                        }
//                    }
//                }
//                if (c > quorumF) {
//                    return r;
//                }
//            }
//        }
//        return null;
//    }

    /**
     * Checks if this is a valid proof
     * @param eid Execution ID to match against the proof
     * @param round round number to match against the proof
     * @param proofs Proof to be verified
     * @return True if valid, false otherwise
     */
    public boolean validCollectProof(Long eid, Integer round,
			LinkedList<FreezeProof> proofs) {
		if (proofs == null){
			return false;
		}
		for(FreezeProof p: proofs){
			if (!p.getEid().equals(eid)){
				return false;
			}
		}
		if(!proofs.getLast().getRound().equals(round-1)){
			return false;
		}
        return true;
    }

    /**
     * Returns the valid proofs
     * @param eid Execution ID to match against the proofs
     * @param round round number to match against the proofs
     * @param proofs Proofs to be verified
     * @return Array the the valid proofs
     */
    public CollectProof[] checkValid(Long eid, Integer round, CollectProof[] proofs) {
        if (proofs == null) {
            return null;
        }
        Collection<CollectProof> valid = new HashSet<CollectProof>();
            for (int i = 0; i < proofs.length; i++) {
                if (proofs[i] != null
                        && validSignature(proofs[i], i)
                        && validCollectProof(eid, round, proofs[i].getProofs())) {
                        valid.add(proofs[i]);
                    }
                }
        return valid.toArray(new CollectProof[valid.size()]);
    }

    /**
     * Checks if a signature is valid
     * @param so Signed object
     * @param sender Replica that sent the signed object
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
     * @param l Replica to be checked
     * @param proof Proofs to verify the leader against
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
	 * Builds the acc list for the given round and this set of proofs.
	 * @param proofs
	 * @param round
	 * @return 
	 */
	private RoundInfo buildRoundInfo(CollectProof[] proofsin, int round) {

		// Copy collectproofs to be able to null values
		CollectProof[] proofs = Arrays.copyOf(proofsin, proofsin.length);
		Set<ByteWrapper> acc = new HashSet<ByteWrapper>();
		Set<ByteWrapper> poss = new HashSet<ByteWrapper>();
		

		//Check each Proof for its weak value;
		for (int i = 0; i < proofs.length; i++) {
			ByteWrapper w = getWeakProof(proofs[i], round);
			ByteWrapper s = getStrongProof(proofs[i], round);

			if (w != null && !acc.contains(w) && !poss.contains(w)) {
				int weakcount = 0;
				int strongcount = 0;
				for (int j = 0; j < proofs.length; j++) {

					//Check rounds of other proofs for the same w
					if (j != i) {
						ByteWrapper cw = getWeakProof(proofs[j], round);		// current weak
						ByteWrapper cs = getStrongProof(proofs[j], round);		// current strong

						//The other proof is also weakly accepted and the values are equal
						if (cw != null && w.equals(cw)) {
							weakcount++;
							proofs[j] = null;	// Eliminate this Proof as it was already counted
						}
						if (cs != null && s.equals(cs)) {
							strongcount++;
						}
					}
				}
				if (weakcount > quorumF) {
					acc.add(w);
				}
				if ((weakcount > quorumStrong || strongcount > quorumF)) {
					poss.add(w);
				}
			}
		}
		return new RoundInfo(round, acc, poss);
	}
	
	/**
	 * Returns the weakly accepted value for the given round or null if non exists.
	 * @param p The CollectProof to check
	 * @param round The round to check
	 * @return The weakly accepted value or null
	 */
	private ByteWrapper getWeakProof(CollectProof p, int round){
		if (p != null && p.getProofs().size() > round && p.getProofs().get(round).isWeak()) {
			return new ByteWrapper(p.getProofs().get(round).getValue());
		}
		return null;
	}
	/**
	 * Returns the weakly accepted value for the given round or null if non exists.
	 * @param p The CollectProof to check
	 * @param round The round to check
	 * @return The weakly accepted value or null
	 */
	private ByteWrapper getStrongProof(CollectProof p, int round){
		if (p != null && p.getProofs().size() > round && p.getProofs().get(round).isStrong()) {
			return new ByteWrapper(p.getProofs().get(round).getValue());
		}
		return null;
	}
	
	/**
	 * Get highest round number for this set of Collects
	 */
	private int getNumberOfRounds(CollectProof[] proofs){
		int max = 0;
		for (int i = 0; i < proofs.length; i++) {
			int curr_max = proofs[i].getProofs().getLast().getRound();
			max = curr_max > max ? curr_max : max;
		}
		return max;
	}


    /**
     * Builds the Acc and Poss Information for all Rounds in this set of collects
     * @param proofs Proofs to be used to create the set
     * @return A linked list which stands for the Acc set
     */
    private List<RoundInfo> buildInfos(CollectProof[] proofs) {
		List<RoundInfo> infos = new LinkedList<RoundInfo>();
		int rounds = getNumberOfRounds(proofs);
		
		// Check all proofs for the current round until no further rounds are found
		for (int i = 0; i < rounds+1; i++) {
			infos.add(buildRoundInfo(proofs, i));
		}
        return infos;
    }

    /**
     * Checks if a signature is valid
     * @param so Signed object
     * @param sender Replica that sent the signed object
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
