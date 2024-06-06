#!/usr/bin/zsh

if [ ! -f target/experiment-1.jar ]; then
  echo "Compiled program not found, cannot run anything..."
else
#  java -Xlog:gc --class-path target/experiment-1.jar org.example.Main
  java -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC --enable-preview --class-path target/experiment-1.jar org.example.Main
#  java --enable-preview --class-path target/experiment-1.jar org.example.Main




time for i in {1..10}; do java -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC --enable-preview --class-path target/experiment-1.jar org.example.Main; done > native_no_ep.txt
time for i in {1..10}; do java -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC --enable-preview --class-path target/experiment-1.jar org.example.Main; done > native_no_ep.txt
