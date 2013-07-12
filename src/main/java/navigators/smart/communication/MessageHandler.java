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
package navigators.smart.communication;

import java.io.IOException;
import java.nio.ByteBuffer;

import navigators.smart.tom.core.messages.SystemMessage;

/**
 *
 * @param <U> Resulttype of the verification of this message
 * @param <M> Messages that will be sent and received via this MessageHandler
 * @author Christian Spann 
 */
public interface MessageHandler<M extends SystemMessage, U> {

    /**
     * Processes the SystemMessage sm if it is adressed to this handler
     * @param sm The message to process
     */
    public void processData(SystemMessage sm);

    /**
     * Deserialises the SystemMessage of the given type
     * @param type The type to indicate the Object to create
     * @param in The ByteBuffer containing the serialised object
     * @param result The result of the verification of the message
     * @return The created Object if the type is valid for this handler, null otherwhise
     *
     * @throws ClassNotFoundException If there are Objects seralised within and the class of these objects is not found
     * @throws IOException If something else goes wrong upon deserialisation
     */
    public M deserialise(SystemMessage.Type type, ByteBuffer in, U result) throws ClassNotFoundException,IOException;

}
