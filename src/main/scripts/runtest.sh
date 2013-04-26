#!/bin/bash
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
	cmd="cd $dir; runscripts/$1 $i $seqnr"
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
	cmd="cd $dir; runscripts/$2 $seqnr"
	if [ $i -eq 0 ] ; then
		tmux new-window -t "testrun:1" "ssh ${host} \"$cmd\""
	else 
		tmux split-window "ssh ${host} \"${cmd}\""
	fi
	((currid += numthreads))
done

tmux attach -t "testrun"

echo $(( ++seqnr )) > ${seqfile} # auto increase seqfile

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
