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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import navigators.smart.paxosatwar.messages.CollectProof;
import navigators.smart.paxosatwar.messages.FreezeProof;
import navigators.smart.tom.core.timer.messages.RTCollect;
import navigators.smart.tom.util.TOMConfiguration;


/**
 * This class is used to process data relacioned with freezed rounds. 
 * Generate proposes - good values, verify the proposed values and so on...
 */
public class ProofVerifier {

    private int quorumF; // f replicas
    private int quorumStrong; // (n + f) / 2 replicas
//    private int numberOfNonces; // Ammount of nonces that have to be delivered to the application
    private PublicKey[] publickeys; // public keys of the replicas
    private Signature[] engines;
    private PrivateKey prk = null; // private key for this replica
    private Signature engine; // Signature engine

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
            e.printStackTrace();
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
            e.printStackTrace();
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
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Counts how many proofs are in the given array (how many are diferent from null)
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
    public byte[] getGoodValue(SignedObject[] proofs, boolean in) {
        try {
            CollectProof[] cps = new CollectProof[proofs.length];
            for (int i = 0; i < proofs.length; i++) {
                if (proofs[i] != null) {
                    cps[i] = (CollectProof) proofs[i].getObject();
                }
            }
            return getGoodValue(cps, in);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Checks if an specified array of bytes is contained in a given linked list (whose values are arrays of bytes)
     * @param l Linked list containing arrays of bytes
     * @param e Array of bytes that is to be search in the linked list
     * @return True if 'e' is contained in 'l', false otherwise
     */
    private boolean containsArray(LinkedList<byte[]> l, byte[] e) {
        for (Iterator<byte[]> i = l.iterator(); i.hasNext();) {
            byte[] value = i.next();
            if (Arrays.equals(value, e)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Obtains the value that is considered to be good, as is specified by the PaW algorithm
     * @param proofs Proofs to be evaluated
     * @param in True if the proofs to be evaluated are from the freezed consensus, false for the proofs from the next consensus
     * @return The value considered to be good, if any. If such value can't be found, null is returned
     */
    public byte[] getGoodValue(CollectProof[] proofs, boolean in) {

        LinkedList<byte[]> poss = buildPoss(proofs, in);
        LinkedList<byte[]> acc = buildAcc(proofs, in);
        for (Iterator<byte[]> i = acc.iterator(); i.hasNext();) {
            byte[] value = i.next();
            if (containsArray(poss, value)) {
                return value;
            }
        }

        return null;
    }

    /**
     * Called by acceptors to verify if some proposal is good, as specified by the PaW algorithm
     * @param value The proposed value
     * @param proofs The proofs to check the value agaisnt
     * @param in True if the proofs to be evaluated are from the freezed consensus, false for the proofs from the next consensus
     * @return True if the value is good, false otherwise
     */
    public boolean good(byte[] value, CollectProof[] proofs, boolean in) {

        LinkedList<byte[]> poss = buildPoss(proofs, in);
        LinkedList<byte[]> acc = buildAcc(proofs, in);

        //condition G2
        if (containsArray(acc, value) && (containsArray(poss, value) || poss.isEmpty())) {
            return true;
        }

        //condition G1
        if (poss.isEmpty()) {
            //alysson: ainda nao estou bem certo q isso esta certo
            return true;
        }

        return false;
    }

    /**
     * Returns the round number of the next consensus's execution from an array of proofs
     * @param proof Array of proofs which gives out the round number of next consensus's execution
     * @return The number of the round, or -1 if there is not one executing
     */
    public Integer getNextExecRound(CollectProof[] proof) {
        for (int i = 0; i < proof.length; i++) {
            if (proof[i].getProofs(false) != null) {
                Integer r = proof[i].getProofs(false).getRound();
                int c = 1;
                for (int j = i + 1; j < proof.length; j++) {
                    if (proof[j].getProofs(false) != null) {
                        if (r.equals(proof[j].getProofs(false).getRound())) {
                            c++;
                        }
                    }
                }
                if (c > quorumF) {
                    return r;
                }
            }
        }
        return null;
    }

    /**
     * Checks if this is a valid proof
     * @param eid Execution ID to match against the proof
     * @param round round number to match against the proof
     * @param proof Proof to be verified
     * @return True if valid, false otherwise
     */
    public boolean validProof(Long eid, Integer round, FreezeProof proof) {
        // TODO: nao devia ser 'proof.getRound() <= round'?
        return (proof != null) && (proof.getEid().equals(eid)) && (proof.getRound().equals(round));
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
                        && validProof(eid, round, proofs[i].getProofs(true))) {
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
            e.printStackTrace();
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
     * Builds a Poss set as defined in the PaW algorithm
     * @param proofs Proofs to be used to create the set
     * @param in True if the proofs to be used are from the freezed consensus, false for the proofs from the next consensus
     * @return A linked list which stands for the Poss set
     */
    private LinkedList<byte[]> buildPoss(CollectProof[] proofs, boolean in) {
        LinkedList<byte[]> poss = new LinkedList<byte[]>();

        for (int i = 0; i < proofs.length; i++) {
            byte[] w = null;

            if (proofs[i] != null && proofs[i].getProofs(in) != null) {
                w = proofs[i].getProofs(in).getValue();
            }

            if (w != null) {
                int countW = 0;
                int countS = 0;

                for (int j = 0; j < proofs.length; j++) {
					
                    if (proofs[j] != null) {
						FreezeProof current = proofs[j].getProofs(in);
						if(current != null){
							if (Arrays.equals(w, current.getValue())) {
								if(current.isWeak()){
									countW++;
								}
								if(current.isStrong()){
									countS++;
								}
							}
						}
                    }
                }

                if ((countW > quorumStrong || countS > quorumF) && !poss.contains(w)) {
                    poss.add(w);
                }
            }
        }

        return poss;
    }


    /**
     * Builds a Acc set as defined in the PaW algorithm
     * @param proofs Proofs to be used to create the set
     * @param in True if the proofs to be used are from the freezed consensus, false for the proofs from the next consensus
     * @return A linked list which stands for the Acc set
     */
    private LinkedList<byte[]> buildAcc(CollectProof[] proofs, boolean in) {
        LinkedList<byte[]> acc = new LinkedList<byte[]>();

        for (int i = 0; i < proofs.length; i++) {
            byte[] w = null;
            if (proofs[i] != null && proofs[i].getProofs(in) != null && proofs[i].getProofs(in).isWeak()) {
                w = proofs[i].getProofs(in).getValue();
            }

            if (w != null) {
                int count = 0;

                for (int j = 0; j < proofs.length; j++) {

                    if (proofs[j] != null){
						FreezeProof current = proofs[j].getProofs(in);
						//The other proof is also weakly accepted and the values are equal
						if (current != null && current.isWeak()
								&& Arrays.equals(w, current.getValue())) {
							count++;
						}
					}
                }

                if (count > quorumF && !acc.contains(w)) {
                    acc.add(w);
                }
            }
        }

        return acc;
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
            e.printStackTrace();
        }
        return false;
    }
}
