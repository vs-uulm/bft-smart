#!/bin/bash
measurement_int=1000
measurement_count=10 # currently unused
logdir=log
cd $3
javacmd="java -server -XX:CompileThreshold=0 -ea -Xmx65m -XX:+HeapDumpOnOutOfMemoryError -Dnavigators.smart.ebawa.configfile=config/ebawa.config -cp dist/modular_SMaRt.jar:lib/netty-3.1.1.GA.jar:lib/ConfigHandler.jar:lib/commons-math-2.2.jar -Djava.util.logging.config.file=config/logging.properties navigators.smart.tom.demo.ThroughputLatencyTestServer $1 $measurement_int $measurement_count"

seqnr=$2 #read seqnr from call

#Create logdir 
if [ ! -e $logdir ]; then
	echo "Creating $logdir"
	mkdir $logdir
fi

logfile=${logdir}/${HOSTNAME}_${seqnr}.log

echo "Test run $seqnr on $HOSTNAME" > $logfile
echo "Executing: $javacmd" >> $logfile
$javacmd >> $logfile 2>&1 & #Run cmd, redirect output to logfile, redirect stderr to stdout and go to background to be able to read the pid for later killing
echo $! > ${HOSTNAME}_PID #Read pid and write to file
tail -f $logfile #open log for reading
