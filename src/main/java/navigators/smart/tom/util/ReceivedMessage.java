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
 */package navigators.smart.tom.util;

public class ReceivedMessage{
    
    
    private int from;
    private byte[] data;
    
    /** Creates a new instance of Message */
    public ReceivedMessage(byte[] data, int from) {
        this.from = from;
        this.data = data;
    }
        
    public int getFrom(){
        return this.from;
    }
    
    public byte[] getData(){
        return this.data;
    }
}

