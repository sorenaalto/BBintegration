#!/bin/bash
cd $LOGDIR
echo Enrol-o-mat: all departments run `/bin/date` >000RUN_SUMMARY.txt
for x in BBC*Enrolomat* ; do
	DPFX=`echo $x | cut -b1-18`
	cat $x | grep "^main:\|^ERROR:" | grep -v "maps to multiple classrooms" | sed -e"s/^/$DPFX: /" >>000RUN_SUMMARY.txt
done
