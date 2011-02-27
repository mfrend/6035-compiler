#!/bin/sh

base=`dirname $0`/..

echo 'Checking for indent width of two spaces.'
find $base/src $base/tests/src -name "*.java" |
    xargs $base/lib/indent_finder.py | grep -v -n -E ': space 2$'

echo 'Detecting trailing whitespace.'
find $base/src $base/tests/src -name "*.java" |
    xargs grep -n -E ' $'

echo 'Detecting tabs.'
find $base/src $base/tests/src -name "*.java" |
    xargs grep -n -E '	'; 

