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
package navigators.smart.paxosatwar.executionmanager;

import navigators.smart.paxosatwar.roles.Acceptor;


/**
 * This class implements a timeout for consensus's rounds
 */
public class TimeoutTask implements Runnable {

    private Acceptor acceptor; // The acceptor role of the PaW algorithm
    private Round round; // The round to which this timeout is related to

    /**
     * Creates a new instance of TimeoutTask
     * @param acceptor The acceptor role of the PaW algorithm
     * @param round The round to which this timeout is related to
     */
    public TimeoutTask(Acceptor acceptor, Round round) {

        this.acceptor = acceptor;
        this.round = round;
    }

    /**
     * Runs this timer
     */
    public void run() {
        acceptor.timeout(round);
    }
}

