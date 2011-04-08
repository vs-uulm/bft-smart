#!/bin/bash

# test properties
numthreads=4	#Number of threads
currid=0		#current startid so that every client can use another startid and wont get blocked
nummsgs=10000	#number of messages sent by each client in each epoch
epochs=1		#Number of epochs to run
argsize=750		#Argument size of each request
interval=0		#Interval of waiting between each request
multicast=true 	#Multicast msgs to all replicas or not - for paxos at war always use multicast - ebawa can live without it

# Change to the prober directory in order to not mess around somewhere else
SCRIPT=`readlink -f $0`
# Absolute path this script is in, thus /home/user/bin
SCRIPTPATH=`dirname $SCRIPT`
cd $SCRIPTPATH/..

count=` awk '!/^#/ && NF {a++} END{print a}' config/hosts.config`
clientcount=` awk '!/^#/ && NF {a++} END{print a}' config/clients.config`
dir=`pwd`
dir=${dir#${HOME}/}
seqfile=SEQNR

#Create testcounter file
if [ ! -f ${seqfile} ]; then
	echo "Creating $seqfile"	
	echo "0" > ${seqfile}
fi
seqnr=`cat ${seqfile}`

#Launch replicas
for (( i=0 ; i < count; i++ )) 
do
	host=`awk '$1 == '$i' {print $2}' config/hosts.config`
	cmd="cd $dir; runscripts/throughputtest.sh $i $seqnr $dir"
	if [ $i -eq 0 ] ; then
		tmux new-session -d -s testrun "ssh $host \"$cmd\""
		tmux set-window-option -g -t testrun:0 remain-on-exit on
		tmux set-option -g -t testrun set-remain-on-exit on
	else 
		tmux split-window "ssh $host \"$cmd\""
	fi
done
tmux select-layout -t "testrun:0" even-vertical
#start clients
echo "Found $clientcount clients to start!"
for (( i=0 ; i < clientcount; i++ )) 
do
	host=`awk '$1 == '$i' {print $2}' config/clients.config`
	cmd="cd $dir; runscripts/throughputtest_client.sh $seqnr $numthreads $currid $nummsgs $epochs $argsize $interval $multicast"
	if [ $i -eq 0 ] ; then
		tmux new-window -t testrun:1 "ssh ${host} \"$cmd\""
	else 
		tmux split-window "ssh ${host} \"${cmd}\""
	fi
	((currid += numthreads))
done

tmux attach -t "testrun"

# echo $(( ++seqnr )) > ${seqfile} # Seqno is not autoincreased any more as the log output shall go to one file now

for (( i = 0 ; i < count ; i++))
do
	host=`awk ' $1 == '$i' { print $2} ' config/hosts.config`
	echo "Killing process on $host"
	ssh $host "cd $dir; kill \`cat ${host}_PID\`; rm ${host}_PID"
	
done
for (( i = 0 ; i < clientcount ; i++))
do
	host=`awk ' $1 == '$i' { print $2} ' config/clients.config`
	echo "Killing process on $host"
	ssh $host "cd $dir; kill \`cat ${host}_PID\`; rm ${host}_PID"	
	
done
