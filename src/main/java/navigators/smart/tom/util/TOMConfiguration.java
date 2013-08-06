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

import java.security.PrivateKey;import java.security.PublicKey;import navigators.smart.communication.server.MessageVerifierFactory.VerifierType;
public class TOMConfiguration extends Configuration {

    protected int n;
    protected int f;
    protected int requestTimeout;
    protected int freezeInitialTimeout;
    protected int tomPeriod;
    protected int paxosHighMark;
    protected int revivalHighMark;
    protected int replyVerificationTime;
    protected int maxBatchSize;
    protected int numberOfNonces;
    protected int inQueueSize;
    protected int outQueueSize;
    protected boolean decideMessagesEnabled;
    protected boolean verifyTimestamps;
    protected boolean useSenderThread;
    protected static RSAKeyLoader rsaLoader;
    protected int clientServerCommSystem;
    private int maxMessageSize;
    private int numNIOThreads;
    private int commBuffering;
    private VerifierType verifiertype;
    private int useSignatures;
    private boolean stateTransferEnabled;
    private int checkpoint_period;
    private int useControlFlow;
    private int signatureSize;
    private long sendDelay;
	/** Cap for the number of pending messages each client can have */
	private int maxPendingMessages;
	/** Shall we not use the previously implemented unfair 'fair' clienthandling 
	 that always starts batches with client (0)*/
	private boolean fairClientHandling;
	// Shall we send readonly requests to f+1 or to all replicas
	private boolean readonlytoall;
    public TOMConfiguration(TOMConfiguration conf, int processId) {
        super(conf, processId);
        this.n = conf.n;
        this.f = conf.f;
        this.requestTimeout = conf.requestTimeout;
        this.freezeInitialTimeout = conf.freezeInitialTimeout;
        this.tomPeriod = conf.tomPeriod;
        this.paxosHighMark = conf.paxosHighMark;
        this.revivalHighMark = conf.revivalHighMark;
        this.replyVerificationTime = conf.replyVerificationTime;
        this.maxBatchSize = conf.maxBatchSize;
        this.numberOfNonces = conf.numberOfNonces;
        this.decideMessagesEnabled = conf.decideMessagesEnabled;
        this.verifyTimestamps = conf.verifyTimestamps;
        this.useSenderThread = conf.useSenderThread;
        this.clientServerCommSystem = conf.clientServerCommSystem;
        this.numNIOThreads = conf.numNIOThreads;
        this.commBuffering = conf.commBuffering;
        this.verifiertype = conf.verifiertype;
        this.useSignatures = conf.useSignatures;
        this.stateTransferEnabled = conf.stateTransferEnabled;
        this.checkpoint_period = conf.checkpoint_period;
        this.useControlFlow = conf.useControlFlow;
        this.inQueueSize = conf.inQueueSize;
        this.outQueueSize = conf.outQueueSize;
        this.maxPendingMessages = conf.maxPendingMessages;
        this.signatureSize = conf.signatureSize;
        this.sendDelay = conf.sendDelay;
		this.fairClientHandling = conf.fairClientHandling;
		this.readonlytoall = conf.readonlytoall;    }

    /** Creates a new instance of TOMConfiguration
     * @param processId The id of this process
     * @param configHome The location of the necessary config files
     */
    @SuppressWarnings("boxing")
    public TOMConfiguration(Integer processId, String configHome) {
        super(processId, configHome);
    }

    @Override
    protected void init() {
        super.init();
        try {
            n = Integer.parseInt(configs.remove("system.servers.num").toString());
            String s = configs.remove("system.servers.f");
            if (s == null) {
                f = (int) Math.ceil((n - 1) / 3);
            } else {
                f = Integer.parseInt(s);
            }

            s = configs.remove("system.paxos.freeze.timeout");
            if (s == null) {
                freezeInitialTimeout = n * 10;
            } else {
                freezeInitialTimeout = Integer.parseInt(s);
            }

            s = configs.remove("system.paxos.decideMessages");
            decideMessagesEnabled = Boolean.parseBoolean(s);

            s = configs.remove("system.totalordermulticast.timeout");
            if (s == null) {
                requestTimeout = freezeInitialTimeout / 2;
            } else {
                requestTimeout = Integer.parseInt(s);
            }

            s = configs.remove("system.totalordermulticast.period");
            if (s == null) {
                tomPeriod = n * 5;
            } else {
                tomPeriod = Integer.parseInt(s);
            }

            s = configs.remove("system.totalordermulticast.highMark");
            if (s == null) {
                paxosHighMark = 10000;
            } else {
                paxosHighMark = Integer.parseInt(s);
                if (paxosHighMark < 10) {
                    paxosHighMark = 10;
                }
            }

            s = configs.remove("system.totalordermulticast.revival_highMark");
            if (s == null) {
                revivalHighMark = 10;
            } else {
                revivalHighMark = Integer.parseInt(s);
                if (revivalHighMark < 1) {
                    revivalHighMark = 1;
                }
            }

            s = configs.remove("system.totalordermulticast.maxbatchsize");
            if (s == null) {
                maxBatchSize = 100;
            } else {
                maxBatchSize = Integer.parseInt(s);
            }

            s = configs.remove("system.totalordermulticast.maxMessageSize");
            if (s == null) {
                maxMessageSize = 200; //the same as used in upright
            } else {
                maxMessageSize = Integer.parseInt(s);
            }

//            s = configs.remove("system.debug");
//            if (s == null) {
//                Logger.debug = false;
//            } else {
//                debug = Integer.parseInt(s);
//                if (debug == 0) {
//                    Logger.debug = false;
//                } else {
//                    Logger.debug = true;
//                }
//            }

            s = configs.remove("system.totalordermulticast.replayVerificationTime");
            if (s == null) {
                replyVerificationTime = 0;
            } else {
                replyVerificationTime = Integer.parseInt(s);
            }

            s = configs.remove("system.totalordermulticast.nonces");
            if (s == null) {
                numberOfNonces = 0;
            } else {
                numberOfNonces = Integer.parseInt(s);
            }

            s = configs.remove("system.totalordermulticast.verifyTimestamps");
            verifyTimestamps = Boolean.parseBoolean(s);

            s = configs.remove("system.communication.useSenderThread");
            useSenderThread = Boolean.parseBoolean(s);

            s = configs.remove("system.communication.clientServerCommSystem");
            if (s == null) {
                clientServerCommSystem = 1;
            } else {
                clientServerCommSystem = Integer.parseInt(s);
            }


            s = configs.remove("system.communication.numNIOThreads");
            if (s == null) {
                numNIOThreads = 2;
            } else {
                numNIOThreads = Integer.parseInt(s);
            }

            s = configs.remove("system.communication.commBuffering");
            if (s == null) {
                commBuffering = 0;
            } else {
                commBuffering = Integer.parseInt(s);
            }

            s = configs.remove("system.communication.verifiertype");
            if (s == null) {
                verifiertype = VerifierType.None;
            } else {
                verifiertype = VerifierType.valueOf(s);
            }

            s = configs.remove("system.communication.useSignatures");
            if (s == null) {
                useSignatures = 0;
            } else {
                useSignatures = Integer.parseInt(s);
            }

            s = configs.remove("system.totalordermulticast.state_transfer");
            stateTransferEnabled = Boolean.parseBoolean(s);

            s = configs.remove("system.totalordermulticast.checkpoint_period");
            if (s == null) {
                checkpoint_period = 1;
            } else {
                checkpoint_period = Integer.parseInt(s);
            }

            s = configs.remove("system.communication.useControlFlow");
            if (s == null) {
                useControlFlow = 0;
            } else {
                useControlFlow = Integer.parseInt(s);
            }

            s = configs.remove("system.communication.inQueueSize");
            if (s == null) {
                inQueueSize = 200;
            } else {

                inQueueSize = Integer.parseInt(s);
                if (inQueueSize < 1) {
                    inQueueSize = 200;
                }

            }

            s = configs.remove("system.communication.outQueueSize");
            if (s == null) {
                outQueueSize = 200;
            } else {
                outQueueSize = Integer.parseInt(s);
                if (outQueueSize < 1) {
                    outQueueSize = 200;
                }
            }
            s = configs.remove("system.client.maxPendingMessages");
            if (s == null) {
                maxPendingMessages = 5;
            } else {
                maxPendingMessages = Integer.parseInt(s);
                if (maxPendingMessages < 0) {
                    maxPendingMessages = 5;
                }
            }
            s = configs.remove("system.testing.sendDelay");
            if (s == null) {
                sendDelay = 0;
            } else {
                sendDelay = Integer.parseInt(s);
                if (sendDelay < 0) {
                    sendDelay = 0;
                }
            }
			
			s = configs.remove("system.client.fairhandling");
			if(s == null) {
				fairClientHandling = true;
			} else {
				fairClientHandling = Boolean.parseBoolean(s);
			}
			
			s = configs.remove("system.client.readonlytoall");
			if(s == null) {
				readonlytoall = false;
			} else {
				readonlytoall = Boolean.parseBoolean(s);
			}

            rsaLoader = new RSAKeyLoader(this, configHome);

            signatureSize = new TOMUtil().getSignatureSize();

        } catch (Exception e) {
            System.err.println("Wrong system.config file format.");
            e.printStackTrace();
        }

    }

    public int getMaxMessageSize() {
        return maxMessageSize;
    }

    public int getRequestTimeout() {
        return requestTimeout;
    }

    public int getReplyVerificationTime() {
        return replyVerificationTime;
    }

    /**
     * Returns the number of servers that are configured.
     * @return The number of servers.
     */
    public int getN() {
        return n;
    }

    public int getF() {
        return f;
    }

    public int getTOMPeriod() {
        return tomPeriod;
    }

    public int getFreezeInitialTimeout() {
        return freezeInitialTimeout;
    }

    public int getPaxosHighMark() {
        return paxosHighMark;
    }

    public int getRevivalHighMark() {
        return revivalHighMark;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public boolean isDecideMessagesEnabled() {
        return decideMessagesEnabled;
    }

    public boolean isStateTransferEnabled() {
        return stateTransferEnabled;
    }

    public boolean canVerifyTimestamps() {
        return verifyTimestamps;
    }

    public int getInQueueSize() {
        return inQueueSize;
    }

    public int getOutQueueSize() {
        return outQueueSize;
    }

    public boolean isUseSenderThread() {
        return useSenderThread;
    }

    /**
     *
     * @return 0 (Netty), 1 (MINA)
     */
    public int clientServerCommSystem() {
        return clientServerCommSystem;
    }

    /**
     *     
     */
    public int getNumberOfNIOThreads() {
        return numNIOThreads;
    }

    /** 
     * @return the numberOfNonces
     */
    public int getNumberOfNonces() {
        return numberOfNonces;
    }

    /**
     * Number of requests from clients buffered by the client communication system before delivering to the TOM Layer
     */
    public int getCommBuffering() {
        return commBuffering;
    }

    /**
     * Indicates if signatures should be used (1) or not (0) to authenticate client requests
     */
    public int getUseSignatures() {
        return useSignatures;
    }

    /**
     * Indicates if MACs should be used (1) or not (0) to authenticate client-server and server-server messages
     */
    public VerifierType getVerifierType() {
        return verifiertype;
    }

    /**
     * Indicates the checkpoint period used when fetching the state from the application
     */
    public int getCheckpoint_period() {
        return checkpoint_period;
    }

    /**
     * Indicates if a simple control flow mechanism should be used to avoid an overflow of client requests
     */
    public int getUseControlFlow() {
        return useControlFlow;
    }

    public static PublicKey[] getRSAServersPublicKeys() {
        try {
            return rsaLoader.loadServersPublicKeys();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    public static PublicKey getRSAPublicKey(int id) {
        try {
            return rsaLoader.loadPublicKey(id);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    public void increasePortNumber() {
        for (int i = 0; i < getN(); i++) {
            hosts.setPort(i, hosts.getPort(i) + 1);
        }

    }

    public static PrivateKey getRSAPrivateKey() {
        try {
            return rsaLoader.loadPrivateKey();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public int getSignatureSize() {
        return signatureSize;
    }

    public long getSendDelay() {
        return sendDelay;
    }

	public int getMaxPending() {
		return maxPendingMessages;
	}

	public boolean isFairClientHandling() {
		return fairClientHandling;
	}}
