#!/bin/bash
cd /home/soren/stuff/BBintegration/2018

# set datestamp and log directory for this run
. ./startRun.sh

./updateWorkingSheets.sh

for dsheet in workingSheets/data/BBC*.xls ; do
	./enrolForDeptX.sh $dsheet
	# push logs after every department...
	echo syncing logs...
	./pushlogs2.sh
done
./addSummaryLog.sh
./pushlogs2.sh

