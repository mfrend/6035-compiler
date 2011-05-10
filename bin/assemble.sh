#!/bin/sh

DIR=`dirname $0`
TEMP=`tempfile -s .o`
$DIR/ld-linux-x86-64.so.2 --library-path $DIR $DIR/as -o $TEMP $1
gcc -o $2 $TEMP
