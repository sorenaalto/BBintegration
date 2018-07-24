#!/bin/sh
echo logging to $LOGDIR
mkdir -p $LOGDIR

export dname=`basename $1 xls`
echo === generate newenrollments for $dname ===
>BBSTUDENTS.TXT
>BBENROL.TXT

echo GENERATE SNAPSHOTS...
time python ./BBCIncrementalEnrollerX2.py $1 2>&1 | tee $LOGDIR/"$dname"Enrolomat.txt

echo POSTING SNAPSHOTS to SIS endpoint
echo `/bin/date` Posting student and course enrollment snapshots for $dname >$LOGDIR/"$dname"Postlog.txt
echo  BBSTUDENTS.TXT ----------- >>$LOGDIR/"$dname"Postlog.txt
#echo TEST RUN, NOT POSTING BBSTUDENTS
#echo TEST RUN, NOT POSTING BBSTUDENTS >>$LOGDIR/"$dname"Postlog.txt
./poststudents.sh BBSTUDENTS.TXT >>$LOGDIR/"$dname"Postlog.txt
# there may be a race condition here?
sleep 10s
echo   BBENROL.TXT ------------- >>$LOGDIR/"$dname"Postlog.txt
#echo TEST RUN, NOT POSTIN BBENROL
#echo TEST RUN, NOT POSTIN BBENROL >>$LOGDIR/"$dname"Postlog.txt
./postenrol.sh BBENROL.TXT       >>$LOGDIR/"$dname"Postlog.txt
echo copy snapshot files to saved output
for x in BBSTUDENTS BBENROL ; do
	cp $x.TXT $LOGDIR/"$dname"$x.txt
done
echo === department done ===


