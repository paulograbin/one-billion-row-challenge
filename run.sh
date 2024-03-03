#!/usr/bin/zsh

echo "Run.sh..."

if [ ! -f target/experiment-1.jar ]; then
  echo "11111"
else
  echo "222"
  java --class-path target/experiment-1.jar org.example.Main                                                                                                        130 ↵
fi