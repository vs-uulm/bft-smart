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

import java.util.Arrays;
import java.util.LinkedList;
import java.util.ListIterator;

import navigators.smart.tom.core.messages.TOMMessage;



/**
 * Extended LinkedList used to store pending requests issued by a client.
 *
 * @author alysson
 */
public class PendingRequests extends LinkedList<TOMMessage> {

	private static final long serialVersionUID = -7796606820273416224L;

    public TOMMessage remove(byte[] serializedMessage) {
        for(ListIterator<TOMMessage> li = listIterator(); li.hasNext(); ) {
            TOMMessage msg = li.next();
            if(Arrays.equals(serializedMessage,msg.getBytes())) {
                li.remove();
                return msg;
            }
        }
        return null;
    }

    public TOMMessage removeById(Integer id){
        for(ListIterator<TOMMessage> li = listIterator(); li.hasNext(); ) {
            TOMMessage msg = li.next();
            if(msg.getId().equals(id)) {
                li.remove();
                return msg;
            }
        }
        return null;
    }


    public TOMMessage get(byte[] serializedMessage){
        for(ListIterator<TOMMessage> li = listIterator(); li.hasNext(); ) {
            TOMMessage msg = li.next();
            if(Arrays.equals(serializedMessage,msg.getBytes())) {
                return msg;
            }
        }
        return null;
    }


    public TOMMessage getById(Integer id){
        for(ListIterator<TOMMessage> li = listIterator(); li.hasNext(); ) {
            TOMMessage msg = li.next();
            if(msg.getId().equals(id)) {
                return msg;
            }
        }
        return null;
    }

    public boolean contains(Integer id){
        for(ListIterator<TOMMessage> li = listIterator(); li.hasNext(); ) {
            TOMMessage msg = li.next();
            if(msg.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }
}
