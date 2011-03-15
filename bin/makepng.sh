#!/bin/bash

filename=$1
nopath=$(basename $1)
filebase=${nopath%.*}

dot -Tpng -o $filebase.png $filename
