#!/bin/bash
logdir=log

#Create logdir 
if [ ! -e $logdir ]; then
	echo "Creating $logdir"
	mkdir $logdir
fi

seqnr=$1 #Read testrun seqno from stdin"

logfile=${logdir}/${HOSTNAME}.log

javacmd="java -server -XX:CompileThreshold=0 -ea -Xmx265m -XX:+HeapDumpOnOutOfMemoryError -Dnavigators.smart.ebawa.configfile=config/ebawa.config -cp dist/modular_SMaRt.jar:lib/netty-3.1.1.GA.jar:lib/ConfigHandler.jar:lib/commons-math-2.2.jar -Djava.util.logging.config.file=config/logging.properties navigators.smart.tom.demo.ThroughputLatencyTestClient $numthreads $currid $nummsgs $epochs $argsize $interval $multicast"
echo "Test run $seqnr on $HOSTNAME - client" >> $logfile
echo "Executing: $javacmd" >> $logfile

$javacmd >> $logfile 2>&1 &
pid=$!
echo $pid > ${HOSTNAME}_PID #Read pid and write to file
tail --pid=$pid -f $logfile #open log for reading
