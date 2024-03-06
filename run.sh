#!/usr/bin/zsh

echo "Run.sh..."

if [ ! -f target/experiment-1.jar ]; then
  echo "Compiled program not found, cannot run anything..."
else
#  java -Xlog:gc --class-path target/experiment-1.jar org.example.Main
  java --enable-preview --class-path target/experiment-1.jar org.example.Main
fi