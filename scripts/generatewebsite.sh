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

if [ ! -d "$WEBSITE_PATH" ]
then
    echo "Web site path: $WEBSITE_PATH not found!"
    exit
fi

rm -rf $WEBSITE_PATH*
cp -r $WEBBASE_PATH* $WEBSITE_PATH

java -Xmx1024m -cp $OSMGADM_JAR osmgadm.WebsiteBuilder --templatepath=$TEMPLATE_PATH --websitepath=$WEBSITE_PATH --shapepath=$SHAPE_PATH_COMPRESSED --dbname=$DB_NAME --dbhost=$DB_HOST --dbuser=$DB_USER --dbpw=$DB_PW


