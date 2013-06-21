/**
 * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the
 *
 * @author tags
 *
 * This file is part of SMaRt.
 *
 * SMaRt is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * SMaRt is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with SMaRt. If not, see <http://www.gnu.org/licenses/>.
 */

package navigators.smart.tom.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;


/**
 *
 * @author Christian Spann 
 */
public class SerialisationHelper {

    public static void writeByteArray(byte[] array, DataOutput out) throws IOException{
        out.writeInt(array.length);
        out.write(array);
    }
    
    public static void writeByteArray(byte[] array, ByteBuffer out) {
    	if(array == null){
    		out.putInt(-1);
    	} else {
    		out.putInt(array.length);
    		out.put(array);
    	}
    }

    public static byte[] readByteArray (DataInput in) throws IOException{
        int len = in.readInt();
        if(len >= 0){
        	byte[] ret = new byte[len];
        	in.readFully(ret);
        	return ret;
        } else {
        	return null;
        }
        	
    }
    
    public static byte[] readByteArray (ByteBuffer in) {
    	int len = in.getInt();
    	if(len >= 0){
    		byte[] ret = new byte[len];
    		in.get(ret);
    		return ret;
    	} else {
    		return null;
    	}
    	
    }

    public static void writeObject(Object content, DataOutput out) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(content);
        oos.flush();
        writeByteArray(baos.toByteArray(), out);
    }
    
    public static byte[] writeObject(Object content)  {
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
    	ObjectOutputStream oos;
		try {
			oos = new ObjectOutputStream(baos);
			oos.writeObject(content);
			oos.flush();
		} catch (IOException e) {
			e.printStackTrace();	//wont happen with this jvm
		}
    	return baos.toByteArray();
    }

    public static Object readObject(DataInput in) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(readByteArray(in));
        ObjectInputStream ois = new ObjectInputStream(bais);
        return ois.readObject();
    }
    
    public static Object readObject(ByteBuffer in) throws IOException, ClassNotFoundException {
    	ByteArrayInputStream bais = new ByteArrayInputStream(readByteArray(in));
    	ObjectInputStream ois = new ObjectInputStream(bais);
    	return ois.readObject();
    }

    public static void writeString(String op, DataOutput out) throws IOException {
        out.writeInt(op.length());
        out.writeChars(op);
    }

    public static String readString(DataInput in) throws IOException{
        int len = in.readInt();
        char[] strchar = new char[len];
        for(int i = 0;i < len; i++){
            strchar[i] = in.readChar();
        }
        return new String(strchar);
    }
}
