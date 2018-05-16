#!/bin/bash
export HOST=10.0.8.29 # TLZ APP1
#10.0.28.30 – TLZ APP2
#export HOST=10.0.8.30
export URLCOURSE=https://$HOST/webapps/bb-data-integration-flatfile-BBLEARN/endpoint/course/store
export URLPERSON=https://$HOST/webapps/bb-data-integration-flatfile-BBLEARN/endpoint/person/store
export URLENROL=https://$HOST/webapps/bb-data-integration-flatfile-BBLEARN/endpoint/membership/store
#export CREDS=1776b10c-4cc3-4022-b0f2-86c5f9c31ca0:s3cr3t
#export CREDS=96730d3e-34a0-48eb-9856-11782645ae9d:s3cr3t
#export CREDS=ec8ebcb0-b906-4611-a997-a31c4a1985cf:s3cr3t
export CREDS=955f6f08-00d2-4457-bca6-d7439031b45d:s3cr3t


export FILE=$1
head -5 $FILE
echo ...
tail -3 $FILE
echo curl  -k --data-binary @$FILE -H "Content-type: text/plain"  -u $CREDS $URLPERSON
out1=`curl  -k --data-binary @$FILE -H "Content-type: text/plain"  -u $CREDS $URLPERSON`
echo "Server reponse ref: $out1"
echo done.




