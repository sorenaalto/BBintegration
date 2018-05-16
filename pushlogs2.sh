#!/bin/sh
echo Pushing logs from $LOGDIR to remote...
cd  $LOGDIR
rsync  -av ./ enrolomat@dut-fileshare.northeurope.cloudapp.azure.com:/var/www/html/enrol-o-mat/$LOGDIR
cd ..

