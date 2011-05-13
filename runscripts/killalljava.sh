#!/bin/bash
count=` awk '!/^#/ && NF {a++} END{print a}' config/hosts.config`
echo $count

for (( i = 0 ; i < count ; i++))
do
	host=`awk ' $1 == '$i' { print $2} ' config/hosts.config`
	ssh $host "killall java"
done
