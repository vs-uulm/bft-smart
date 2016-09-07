/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package navigators.smart.communication.server;

/**
 *
 * @author Christian Spann <christian.spann at uni-ulm.de>
 */
public class HMacVerifierFactory implements MessageVerifierFactory<PTPMessageVerifier> {

    public PTPMessageVerifier generateMessageVerifier() {
        return new HMacVerifier();
    }



}
