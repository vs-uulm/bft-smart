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
 */package navigators.smart.tom.demo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Random;

import navigators.smart.tom.ServiceProxy;

/**
 *
 * @author Joao Sousa
 */
public class RandomClient {

    public static void main(String[] args) throws IOException {
        if(args.length < 2) {
            System.out.println("Usage: java RandomClient <process id> <seed>");
            System.exit(-1);
        }

        ServiceProxy counterProxy = new ServiceProxy(Integer.parseInt(args[0]));
        Random generator = new Random(Long.parseLong(args[1]));

        int i=0;
        //sends 1000 requests to replicas and then terminates
        while(i<1500){

            int argument = generator.nextInt(10000) + 1;
            int operator = generator.nextInt(4);

            ByteArrayOutputStream out = new ByteArrayOutputStream(4);
            new DataOutputStream(out).writeInt(argument);
            new DataOutputStream(out).writeInt(operator);

	        byte[] reply = counterProxy.invoke(out.toByteArray(),false);
	        int newValue = new DataInputStream(new ByteArrayInputStream(reply)).readInt();
	        System.out.println("Current value: "+newValue);
	        i++;
        }
        //System.exit(0);
    }
}
