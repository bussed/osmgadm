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

if [ ! -d "$SHAPE_PATH_UNCOMPRESSED" ]
then
    echo "Shape path uncompressed path: $SHAPE_PATH_UNCOMPRESSED not found!"
    exit
fi

if [ ! -d "$SHAPE_PATH_COMPRESSED" ]
then
    echo "Shape path compressed path: $SHAPE_PATH_COMPRESSED not found!"
    exit
fi

rm $SHAPE_PATH_UNCOMPRESSED*
rm $SHAPE_PATH_COMPRESSED*


for i in `psql -t $DB_NAME -h $DB_HOST -U $DB_USER -c "select relation_id from tree_world where level=2 and poly is not null ;"`
do
ogr2ogr --config SHAPE_ENCODING UTF-8 -f "ESRI Shapefile" $SHAPE_PATH_UNCOMPRESSED$i.shp PG:"host=$DB_HOST user=$DB_USER dbname=$DB_NAME password=$DB_PW" -sql "select level as adminlevel, relation_id as relationid, parent_id as parentid, name, poly from tree_world where relation_id in (select relation_id from children_of($i))"
zip -9 -j $SHAPE_PATH_COMPRESSED$i.zip $SHAPE_PATH_UNCOMPRESSED$i.*
done

