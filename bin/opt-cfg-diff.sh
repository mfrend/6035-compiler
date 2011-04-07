#!/bin/sh

runcompiler() {
  java -jar `dirname $0`/../dist/Compiler.jar \
    -target cfg -o $2 $@ $1
}

fail=0

cfg_opt=`tempfile --suffix=.cfg`
cfg_unopt=`tempfile --suffix=.cfg`
msg=""
if runcompiler $1 $cfg_unopt; then
  if ! runcompiler $1 $cfg_opt -opt all; then
    msg="Failed to generate optimized assembly.";
  fi
else
  msg="Failed to generate unoptimized assembly.";
fi
echo $1
if [ ! -z "$msg" ]; then
  fail=1
  echo $msg
else
  diff -u -s $cfg_unopt $cfg_opt
fi
rm -f $cfg_opt $cfg_unopt;

exit $fail;
