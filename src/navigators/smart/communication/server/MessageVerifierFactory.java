/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package navigators.smart.communication.server;

/**
 *
 * @author Christian Spann <christian.spann at uni-ulm.de>
 */
public interface MessageVerifierFactory<F> {

    public enum VerifierType {
        None, //no verification is to be used
        PTPVerifier,
        GlobalVerifier;
    }

    public F generateMessageVerifier();
}
