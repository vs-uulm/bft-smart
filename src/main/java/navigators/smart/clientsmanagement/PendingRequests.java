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
