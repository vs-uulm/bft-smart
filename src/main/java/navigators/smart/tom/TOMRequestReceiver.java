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
 */package navigators.smart.tom;

import navigators.smart.tom.core.messages.TOMMessage;

/**
 * Interface meant for objects that receive requests from clients
 */
public interface TOMRequestReceiver {

    /**
     * This is the method invoked by the DeliveryThread to deliver a totally
     * ordered request, and where the code to handle the request is to be written
     *
     * @param msg the delivered request
     */
    public void receiveOrderedMessage(TOMMessage msg);

    /**
     * This is the method invoked by the TOMLayer to deliver a read only request
     * that was not totally ordered
     *
     * @param msg The request delivered by the TOM layer
     */
    public void receiveUnorderedMessage(TOMMessage msg);

    /**
     * This method is used by the TOM Layer to retrieve the state of the application. This must be
     * implemented by the programmer, and it should retrieve an array of bytes that contains the application
     * state.
     * @return An rray of bytes that can be diserialized into the application state
     */
    public byte[] getState();

    /**
     * This method is invoked by the TOM Layer in order to set a state upon the application. This is done when
     * a replica is delayed compared to the rest of the group, and when it recovers after a failure.
     */
    public void setState(byte[] state);
}

