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
 */package navigators.smart.tom.core;

import java.util.Arrays;
import java.util.concurrent.Semaphore;

import navigators.smart.tom.core.messages.RequestRecoveryMessage;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.TOMConfiguration;


/**
 * TODO: Isto vai desaparecer?
 * 
 */
public class RequestRecover {

    private Semaphore wait = new Semaphore(0);
    private Semaphore mutex = new Semaphore(1);
    private Integer[] group;
    private TOMMessage msg;
    private TOMLayer tomLayer;
    private byte[] hash;
    private TOMConfiguration conf;

    /** Creates a new instance of RequestRecover */
    @SuppressWarnings("boxing")
    public RequestRecover(TOMLayer tomLayer, TOMConfiguration conf) {
        this.tomLayer = tomLayer;
        this.conf = conf;
        group = new Integer[conf.getN() - 1];

        int c = 0;
        for (int i = 0; i < conf.getN(); i++) {
            if (i != conf.getProcessId().intValue()) {
                group[c++] = i;
            }
        }
    }

    public void recover(byte[] hash) {
        this.msg = null;
        this.hash = hash;
        this.tomLayer.getCommunication().send(group,
                new RequestRecoveryMessage(hash, conf.getProcessId()));
    }

    public void receive(TOMMessage tommsg, byte[] msgHash) {
        try {
            this.mutex.acquire();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (this.hash != null) {
            if (Arrays.equals(msgHash, hash)) {
                this.msg = tommsg;
                this.hash = null;
                //br.ufsc.das.util.Logger.println("(RequestRecover) Mensagem recebida coincide com a que estavamos ah espera");
                this.wait.release();
            } else {
                //br.ufsc.das.util.Logger.println("(RequestRecover) Mensagem recebida NAO coincide com a que estavamos ah espera");
            }
        }

        mutex.release();
    }

//    public TOMMessage checkWithoutWait(List<RequestRecoveryMessage> l, byte[] h) {
        /*
        TOMMessage result = null;

        try{
        this.mutex.acquire();
        }catch(Exception e){
        e.printStackTrace();
        }

        if(l != null && l.size() > conf.getF()){        
        List<TOMMessage> candidates = new LinkedList<TOMMessage>();

        for(int i = 0; i < l.size(); i++){
        if(se != null){

        for(UnorderedEntry e: se){

        if(equalsHash(e.hash,h)){

        candidates.add(e.msg);

        break;

        }

        }

        }

        }



        result = getFromCandidates(candidates);

        }



        this.mutex.release();



        return result;
         */
//        return null;
//    }

//    public void check(List<RequestRecoveryMessage> l) {
        /*
        try{

        this.mutex.acquire();

        }catch(Exception e){

        e.printStackTrace();

        }



        if(this.hash != null && l != null && l.size() > conf.getF()){

        List<TOMMessage> candidates = new LinkedList<TOMMessage>();



        for(int i = 0; i < l.size(); i++){

        UnorderedEntry[] se = l.get(i).getMsgsDelivered();



        if(se != null){

        for(UnorderedEntry e:se){

        if(equalsHash(e.hash,this.hash)){

        candidates.add(e.msg);

        break;

        }

        }

        }

        }



        TOMMessage result = getFromCandidates(candidates);

        if(result != null){

        this.msg = result;

        this.hash = null;

        this.wait.release();

        }

        }



        this.mutex.release();
         */
//    }

//    private TOMMessage getFromCandidates(List<TOMMessage> candidates) {
//        TOMMessage comp = null;
//
//        for (int i = 0; i < candidates.size(); i++) {
//            comp = candidates.get(i);
//            int c = 1;
//            for (int j = i + 1; j < candidates.size(); j++) {
//                if (comp.equals(candidates.get(j))) {
//                    c++;
//                }
//            }
//            if (c > conf.getF()) {
//                return comp;
//            }
//        }
//
//        return null;
//    }

    /*
    public void receive(TOMMessage msg) {
        try {
            this.mutex.acquire();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (this.hash != null) {

            byte[] recHash = tomLayer.computeHash(msg);
            if (Arrays.equals(recHash, hash)) {
                //message recovered...
                this.msg = msg;
                this.hash = null;
                this.wait.release();
            }
        } else {
            Logger.println("(RequestRecover) hash do RR_REPLY não é igual ao do RR_REQUEST");
        }

        mutex.release();
    }
    */
    public TOMMessage getMsg() {
        try {
            this.wait.acquire();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return this.msg;
    }
}
