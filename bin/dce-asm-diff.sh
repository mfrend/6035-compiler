#!/bin/sh

runcompiler() {
  java -jar `dirname $0`/../dist/Compiler.jar \
    -target codegen -o $2 $@ $1
}

fail=0

asm_opt=`tempfile --suffix=.s`
asm_unopt=`tempfile --suffix=.s`
msg=""
if runcompiler $1 $asm_unopt -opt cse -opt cp; then
  if ! runcompiler $1 $asm_opt -opt all; then
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
  diff -u -s $asm_unopt $asm_opt
fi
rm -f $asm_opt $asm_unopt;

exit $fail;
