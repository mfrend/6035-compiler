#!/bin/sh

runcompiler() {
  java -jar `dirname $0`/../../dist/Compiler.jar \
    -target codegen -o $2 $1
}

fail=0

for file in `dirname $0`/input/*.dcf; do
  asm=`tempfile --suffix=.s`
  echo $file
  if runcompiler $file $asm; then
    binary=`tempfile`
    if gcc -o $binary -L `dirname $0`/lib -l6035 $asm; then
      output=`tempfile`
      if $binary > $output; then
        if ! diff -u $output `dirname $0`/output/`basename $file`.out; then
          echo "File $file output mismatch.";
          fail=1
        else
          echo "SUCCESS!";
        fi
      else
        cat $output
        echo "Program failed to run.";
        fail=1
      fi
      rm $output;
    else
      echo "Program failed to assemble.";
      fail=1
    fi
    rm $binary;
  else
    echo "Program failed to generate assembly.";
    fail=1
  fi
  rm $asm;
done

exit $fail;
