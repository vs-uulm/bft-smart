#!/bin/bash

# Change to the prober directory in order to not mess around somewhere else
SCRIPT=`readlink -f $0`
# Absolute path this script is in, thus /home/user/bin
SCRIPTPATH=`dirname $SCRIPT`
cd $SCRIPTPATH/..

pwd 

count=` awk '!/^#/ && NF {a++} END{print a}' config/hosts.config`
dir=`pwd`
seqfile=SEQNR

#Create testcounter file
if [ ! -f ${seqfile} ]; then
	echo "Creating $seqfile"	
	echo "0" > ${seqfile}
fi
seqnr=`cat ${seqfile}`


for (( i=0 ; i < count; i++ )) 
do
	host=`awk '$1 == '$i' {print $2}' config/hosts.config`
	cmd="cd $dir; runscripts/throughputtest.sh $i $seqnr $dir"
	if [ $i -eq 0 ] ; then
		tmux new-session -d -s testrun "ssh $host \"$cmd\""
		tmux set-window-option -t testrun:0 remain-on-exit on
	else 
		tmux split-window -v "ssh $host \"$cmd\""
	fi
done
tmux select-layout -t "testrun:0" even-vertical
tmux attach -t "testrun"

echo $(( ++seqnr )) > ${seqfile}

for (( i = 0 ; i < count ; i++))
do
	host=`awk ' $1 == '$i' { print $2} ' config/hosts.config`
	ssh $host "kill \`cat $dir/${host}_PID\`"
	rm ${host}_PID
done
