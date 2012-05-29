#!/bin/sh
OUTPATH=$1

UUID=`uuidgen`
INPATH="/tmp/$UUID"

mkdir -p $INPATH
echo "Nobody here but us chickens" > $INPATH/README
jar cf $OUTPATH -C $INPATH README
rm -rf $INPATH
