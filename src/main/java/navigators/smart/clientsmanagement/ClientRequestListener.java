/* * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, 
 * and the authors indicated in the @author tags 
 *  
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 *  
 * http://www.apache.org/licenses/LICENSE-2.0 
 *  
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License. 
 */
package navigators.smart.clientsmanagement;

/**
 * This interface is used by components that want to be informed upon starting
 * and final descision of client requests.
 *
 * @author Christian Spann 
 */
public interface ClientRequestListener {

    /**
     * This method is invoked upon a new message beeing received from a client
     * that shall be ordered/decided by the consensus
     */
    public void requestReceived(Object msg);

    /**
     * This method is invoked when the consensus successfully decided upon the
     * message.
     */
    public void requestOrdered(Object msg);

}
