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
package navigators.smart.tom.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * Used to load JCA public and private keys from conf/keys/publickey<id> and
 * conf/keys/privatekey<id>
 */

public class RSAKeyLoader {

    private String path;
    private PublicKey[] pubKeys;
    private PrivateKey priKey;
    private TOMConfiguration conf;

    /** Creates a new instance of RSAKeyLoader */
    public RSAKeyLoader(TOMConfiguration conf, String configHome) {
        this.conf = conf;
        if(configHome.equals("")){
            path = "config"+System.getProperty("file.separator")+"keys"+
                      System.getProperty("file.separator");
        }else{
            path = configHome+System.getProperty("file.separator")+"keys"+
                      System.getProperty("file.separator");
        }
    }

    /**
     * Load the public keys from processes 0..conf.getN()-1 (all servers).
     *
     * @return the array of public keys loaded
     * @throws Exception problems reading or parsing the keys
     */

    public PublicKey[] loadServersPublicKeys() throws Exception {
        if(pubKeys == null){
            pubKeys = new PublicKey[conf.getN()];
            for(int i = 0; i < pubKeys.length; i++){
                pubKeys[i] = loadPublicKey(i);
            }
        }
        return pubKeys;
    }

    /**
     * Loads the public key of some processes from configuration files
     * TODO Load this clientspecificly definded in a config file
     * @param id the id of the process that we want to load the public key
     * @return the PublicKey loaded from config/keys/publickey<id>
     * @throws Exception problems reading or parsing the key
     */
    public PublicKey loadPublicKey(int id) throws Exception {
        BufferedReader r = new BufferedReader(new FileReader(path+"publickey0"/*+id*/));
        String tmp = "";
        String key = "";
        while((tmp = r.readLine()) != null){
            key=key+tmp;
        }
        r.close();
        return getPublicKeyFromString(key);
    }

    /**
     * Loads the private key of this process
     * TODO select private key from property in config file ...
     * @return the PrivateKey loaded from config/keys/publickey<conf.getProcessId()>
     * @throws Exception problems reading or parsing the key
     */
    public PrivateKey loadPrivateKey() throws Exception {
        if (priKey == null) {
            BufferedReader r = new BufferedReader(
                    new FileReader(path + "privatekey0"/* + conf.getProcessId()*/));
            String tmp = "";
            String key = "";
            while ((tmp = r.readLine()) != null) {
                key = key + tmp;
            }
            r.close();
            priKey = getPrivateKeyFromString(key);
        }
        return priKey;
    }

    //utility methods for going from string to public/private key
    @SuppressWarnings("restriction")
    private PrivateKey getPrivateKeyFromString(String key) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");		sun.misc.BASE64Decoder b64 = new sun.misc.BASE64Decoder();
        EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(b64.decodeBuffer(key));
        PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);
        return privateKey;
    }

    @SuppressWarnings("restriction")
    private PublicKey getPublicKeyFromString(String key) throws Exception {		sun.misc.BASE64Decoder b64 = new sun.misc.BASE64Decoder();
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(b64.decodeBuffer(key));
        PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);
        return publicKey;
    }
}