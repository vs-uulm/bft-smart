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
 */package navigators.smart.tom.core.timer.messages;

import java.io.IOException;
import java.nio.ByteBuffer;

import navigators.smart.tom.core.messages.SystemMessage;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.SerialisationHelper;


/**
 * Message used to forward a client request to the current leader when the first
 * timeout for this request is triggered (see RequestTimer).
 *
 */
public final class ForwardedMessage extends SystemMessage {

    private TOMMessage request;

    public ForwardedMessage(ByteBuffer in) throws IOException, ClassNotFoundException {
        super(Type.FORWARDED, in);

        byte[] serReq = SerialisationHelper.readByteArray(in);
        request = new TOMMessage(ByteBuffer.wrap(serReq));
        request.setBytes(serReq);

		if(in.get() == 1){											// Signature present?
			request.serializedMessageSignature = 
					SerialisationHelper.readByteArray(in);
		}

    }

    public ForwardedMessage(Integer senderId, TOMMessage request) {
        super(Type.FORWARDED, senderId);
        this.request = request;
    }

    public TOMMessage getRequest() {
        return request;
    }

    @Override
    public void serialise(ByteBuffer out) {
        super.serialise(out);
        SerialisationHelper.writeByteArray(request.getBytes(), out);
		out.put(request.signed?(byte)1:(byte)0);					// indicator byte for signature
		if(request.signed){
			SerialisationHelper.
					writeByteArray(request.serializedMessageSignature, out);
		}
    }

    @Override
    public int getMsgSize(){
		int size = super.getMsgSize();
		size += 4 + request.getBytes().length;						// Add TOMMessage + length field
		// Handle signature
		size += 1;													// Add byte to indicate signature presence
		if (request.signed) { 
			size += 4 + request.serializedMessageSignature.length;	// Add sig lenth + length field
		}
    	return size;
    }

}
