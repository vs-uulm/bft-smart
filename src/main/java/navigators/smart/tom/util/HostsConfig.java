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
import java.io.BufferedReader;
import java.io.FileReader;
import java.net.InetSocketAddress;
import java.util.Hashtable;
import java.util.StringTokenizer;


public class HostsConfig {
    
    private Hashtable<Integer,Config> servers = new Hashtable<Integer,Config>();
    
    
    /** Creates a new instance of HostsConfig */
    public HostsConfig(String configHome, String fileName) {
        loadConfig(configHome, fileName);
    }    
    public HostsConfig(HostsConfig h) {
    	for(Integer i : h.servers.keySet()){    		servers.put(i,new Config(h.servers.get(i)));    	}
    }
    
    private void loadConfig(String configHome, String fileName){
        try{
            String path =  "";
            String sep = System.getProperty("file.separator");
            if(configHome.equals("")){
                   if (fileName.equals(""))
                        path = "config"+sep+"hosts.config";
                   else
                        path = "config"+sep+fileName;
            }else{
                   if (fileName.equals(""))
                        path = configHome+sep+"hosts.config";
                   else
                       path = configHome+sep+fileName;
            }
            FileReader fr = new FileReader(path);
            BufferedReader rd = new BufferedReader(fr);
            String line = null;
            while((line = rd.readLine()) != null){
                if(!line.startsWith("#")){
                    StringTokenizer str = new StringTokenizer(line," ");
                    if(str.countTokens() > 2){
                        int id = Integer.valueOf(str.nextToken());
                        String host = str.nextToken();
                        int port = Integer.valueOf(str.nextToken());
                        this.servers.put(id, new Config(id,host,port));
                    }
                }
            }
            fr.close();
            rd.close();
        }catch(Exception e){
            e.printStackTrace(System.out);
        }
    }
    
    public int getNum(){
        return servers.size();
    }
    
    public InetSocketAddress getRemoteAddress(int id){
        Config c = this.servers.get(id);
        if(c != null){
            return new InetSocketAddress(c.host,c.port);
        }
        return null;
    }
    
    public int getPort(int id){
        Config c = this.servers.get(id);
        if(c != null){
            return c.port;
        }
        return -1;
    }

    public void setPort(int id, int port){
        Config c = this.servers.get(id);
        if(c != null){
            c.port = port;
        }
    }
    
    public String getHost(int id){
        Config c = this.servers.get(id);
        if(c != null){
            return c.host;
        }
        return null;
    }
    
    
    public InetSocketAddress getLocalAddress(int id){
        Config c = this.servers.get(id);
        if(c != null){
            return new InetSocketAddress(c.port);
        }
        return null;
    }
    
    public class Config{
        public int id;
        public String host;
        public int port;
        
        public Config(int id, String host, int port){
            this.id = id;
            this.host = host;
            this.port = port;
        }		public Config(Config config) {			this.id = config.id;			this.host = config.host;			this.port = config.port;		}
    }
}
