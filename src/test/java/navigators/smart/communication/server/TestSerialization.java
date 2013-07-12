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
 */package navigators.smart.communication.server;

import static org.junit.Assert.assertEquals;
import java.nio.ByteBuffer;

import navigators.smart.tom.core.messages.TOMMessage;

public class TestSerialization {

    
	@org.junit.Test
    public void TomSerialisationTest() {
     
        TOMMessage tm = new TOMMessage(0,0,new String("abc").getBytes());

        byte[] message = tm.getBytes();
        System.out.println(message.length);
        System.out.println(message);

        //TOMMessage tm2 = (TOMMessage) ois.readObject();
        ByteBuffer buf = ByteBuffer.wrap(message);
        
        TOMMessage tm2 = new TOMMessage(buf);
        
        assertEquals(tm,tm2);
//        tm2.readExternal(ois);
        
//        System.out.println(new String(tm2.getContent()));
    }

}
