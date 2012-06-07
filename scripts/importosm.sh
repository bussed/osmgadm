#!/bin/sh

if [ ! -f "$1" ]
then
    if [ ! -f config.cfg ]
    then
        echo "Could not found config.cfg file!"
        echo "usage: $0 path_to_config"
        exit
    else
        . ./config.cfg
    fi
else
    . $1
fi

#fetch http://planet.openstreetmap.org/pbf/planet-latest.osm.pbf

osmosis --truncate-pgsql user=$DB_USER database=$DB_NAME password=$DB_PW host=$DB_HOST
osmosis --read-pbf $PBF --tf accept-relations "boundary=administrative" --used-way --used-node –-write-pgsql user=$DB_USER database=$DB_NAME password=$DB_PW host=$DB_HOST

