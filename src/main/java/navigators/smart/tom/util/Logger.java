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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

@Deprecated
public class Logger {

    

    //public static long startInstant = System.currentTimeMillis();
    public static boolean debug = false;
    

    public static void println(String msg) {
     if (debug){
      //long now = System.currentTimeMillis()%100000000;
      DateFormat dateFormat = new SimpleDateFormat("yy/MM/dd HH:mm:ss");
      Date date = new Date();
      String dataActual = dateFormat.format(date);
      System.out.println("(" + dataActual + " - "+
                    Thread.currentThread().getName()+") " + msg);
     }
    }
    

    public static void print(String msg) {

     if (debug){
      long now = System.currentTimeMillis()%100000000;
      System.out.print("("+now+") "+msg);
     }

    }

    

  

}

