#!/bin/sh

if [ ! -f "$1" ]
then
    if [ ! -f config.cfg ]
    then
        echo "Could not found config file!"
        echo "usage: $0 path_to_config"
        exit
    else
        . ./config.cfg
    fi
else
    . $1
fi


psql -d $DB_NAME -h $DB_HOST -U $DB_USER -c "truncate table log;"

echo `date` " Started!"

for i in 2 3 4 5 6 7 8 9 10 11 12 
do
    
echo `date` PROCESSING ADMINLEVEL $i

java -cp $OSMGADM_JAR osmgadm.TreeBuilder --adminlevel=$i --dbname=$DB_NAME --dbhost=$DB_HOST --dbuser=$DB_USER --dbpw=$DB_PW --numthreads=$NUM_THREADS

done

echo `date` " Finished!"
