#!/bin/sh

runcompiler() {
  out=$2
  in=$1
  java -jar $orig_pwd/../../dist/Compiler.jar \
    -target codegen $@
}

fail=0

if ! gcc -v 2>&1 |grep -q '^Target: x86_64-linux-gnu'; then
  echo "Refusing to run cross-compilation on non-64-bit architecture."
  exit 0;
fi

  cd `dirname $0`
  orig_pwd=$PWD

  workingdir=`mktemp -d`
  file=derby.dcf
  progname=derby
  orig_input="./input.ppm"
  input="${workingdir}/input.ppm"
  golden="${orig_pwd}/golden.ppm"
  binary="${workingdir}/${progname}"
  asm="${workingdir}/${progname}.s"
  output="${workingdir}/output.ppm"
  timing_gcc="${workingdir}/${progname}_gcc_opt.timing"
  timing_dcf_unopt="${workingdir}/${progname}_dcf_unopt.timing"
  timing_dcf_ra="${workingdir}/${progname}_dcf_ra.timing"
  timing_dcf_cse="${workingdir}/${progname}_dcf_cse.timing"
  timing_dcf_fullopt="${workingdir}/${progname}_dcf_fullopt.timing"

  echo "Unoptimized"
  cp $orig_input $input;
  msg=""
  if runcompiler -o $asm $file; then
    if gcc -o $binary -L. $asm -l6035 -lpthread; then
      cd $workingdir
      if $binary > $timing_dcf_unopt; then
        if ! diff -q $output $golden > /dev/null; then
          msg="File $file output mismatch.";
        fi
      else
        msg="Program failed to run.";
      fi
    else
      msg="Program failed to assemble.";
    fi
  else
    msg="Program failed to generate assembly.";
  fi
  echo $msg

  cd "$orig_pwd";
  echo "RA only"
  cp $orig_input $input;
  msg=""
  if runcompiler -o $asm -opt regalloc $file; then
    if gcc -o $binary -L. $asm -l6035 -lpthread; then
      cd $workingdir
      if $binary > $timing_dcf_ra; then
        if ! diff -q $output $golden > /dev/null; then
          msg="File $file output mismatch.";
        fi
      else
        msg="Program failed to run.";
      fi
    else
      msg="Program failed to assemble.";
    fi
  else
    msg="Program failed to generate assembly.";
  fi
  echo $msg

  cd "$orig_pwd";
  echo "CSE only"
  cp $orig_input $input;
  msg=""
  if runcompiler -o $asm -opt cse $file; then
    if gcc -o $binary -L. $asm -l6035 -lpthread; then
      cd $workingdir
      if $binary > $timing_dcf_cse; then
        if ! diff -q $output $golden > /dev/null; then
          msg="File $file output mismatch.";
        fi
      else
        msg="Program failed to run.";
      fi
    else
      msg="Program failed to assemble.";
    fi
  else
    msg="Program failed to generate assembly.";
  fi
  echo $msg

  cd "$orig_pwd";
  echo "Fullopt"
  cp $orig_input $input;
  msg=""
  if runcompiler -o $asm -opt all $file; then
    if gcc -o $binary -L. $asm -l6035 -lpthread; then
      cd $workingdir
      if $binary > $timing_dcf_fullopt; then
        if ! diff -q $output $golden > /dev/null; then
          msg="File $file output mismatch.";
        fi
      else
        msg="Program failed to run.";
      fi
    else
      msg="Program failed to assemble.";
    fi
  else
    msg="Program failed to generate assembly.";
  fi
  echo $msg

  cd "$orig_pwd";
  ./derby_gcc > $timing_gcc

  #if [ ! -z "$msg" ]; then
    #fail=1
    #echo $msg
  #else
    if [ ! -z "`cat $timing_gcc`" ]; then
      echo -n "GCC: "
      gcc=`cat $timing_gcc|awk '{print($2)}'`
      echo "${gcc} usec"
    fi
    if [ ! -z "`cat $timing_dcf_unopt`" ]; then
      echo -n "Unoptimized: "
      dcf_unopt=`cat $timing_dcf_unopt|awk '{print($2)}'`
      echo "${dcf_unopt} usec"
  int_speedup=$(($dcf_unopt / $gcc))
  dec_speedup=$((($dcf_unopt * 1000) / $gcc - ($int_speedup * 1000)))
  printf "%d.%03dx slower\n" $int_speedup ${dec_speedup}
    fi
    if [ ! -z "`cat $timing_dcf_ra`" ]; then
      echo -n "RA: "
      dcf_ra=`cat $timing_dcf_ra|awk '{print($2)}'`
      echo "${dcf_ra} usec"
  int_speedup=$(($dcf_ra / $gcc))
  dec_speedup=$((($dcf_ra * 1000) / $gcc - ($int_speedup * 1000)))
  printf "%d.%03dx slower\n" $int_speedup ${dec_speedup}
    fi
    if [ ! -z "`cat $timing_dcf_cse`" ]; then
      echo -n "CSE: "
      dcf_cse=`cat $timing_dcf_cse|awk '{print($2)}'`
      echo "${dcf_cse} usec"
  int_speedup=$(($dcf_cse / $gcc))
  dec_speedup=$((($dcf_cse * 1000) / $gcc - ($int_speedup * 1000)))
  printf "%d.%03dx slower\n" $int_speedup ${dec_speedup}
    fi
    if [ ! -z "`cat $timing_dcf_fullopt`" ]; then
      echo -n "Fullopt: "
      dcf_fullopt=`cat $timing_dcf_fullopt|awk '{print($2)}'`
      echo "${dcf_fullopt} usec"
  int_speedup=$(($dcf_fullopt / $gcc))
  dec_speedup=$((($dcf_fullopt * 1000) / $gcc - ($int_speedup * 1000)))
  printf "%d.%03dx slower\n" $int_speedup ${dec_speedup}
    fi
  #fi

  cd "$orig_pwd";
  rm -r -f $workingdir;

exit $fail;
