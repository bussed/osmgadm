#!/bin/sh

#ATTENTION!!!! THIS SCRIPT WILL DELETE YOUR PGSQL DB FOREVER!!!!
#READ CAREFULLY!

#this script is intended to be run by the pgsql user, NOT ROOT!!!!

# this script will (re-) create a complete new postgrestql DB
# it will create the db, create a user, init postgis and
# init all osmosis schema creation scripts (snapshot variant)
# todo: automatically add escape_string_warning = off to postgresql.conf

DBLOCATION=/home/dieter/db/oos
OSMOSISHOME=/home/dieter/tools/osmosis-0.40.1
DBNAME=osm
DBUSERNAME=osm
DBUSERPASSWORD=osm


/usr/local/etc/rc.d/postgresql stop

rm -rf $DBLOCATION/*
mkdir -p $DBLOCATION


initdb -E UTF8 -D $DBLOCATION
/usr/local/etc/rc.d/postgresql start
createdb -E UTF8 $DBNAME
createuser -s $DBUSERNAME
psql -d $DBNAME -c "create extension hstore;"
psql -d $DBNAME -f /usr/local/share/postgis/contrib/postgis-1.5/postgis.sql
psql -d $DBNAME -f /usr/local/share/postgis/contrib/postgis-1.5/spatial_ref_sys.sql
psql -d $DBNAME -f $OSMOSISHOME/script/pgsnapshot_schema_0.6.sql
psql -d $DBNAME -f $OSMOSISHOME/script/pgsnapshot_schema_0.6_action.sql
psql -d $DBNAME -f $OSMOSISHOME/script/pgsnapshot_schema_0.6_bbox.sql
psql -d $DBNAME -f $OSMOSISHOME/script/pgsnapshot_schema_0.6_linestring.sql

echo "alter role $DBUSERNAME password '$DBUSERPASSWORD';" | psql -d $DBNAME
echo "ALTER TABLE geometry_columns OWNER TO $DBUSERNAME; ALTER TABLE spatial_ref_sys OWNER TO $DBUSERNAME;" | psql -d $DBNAME
