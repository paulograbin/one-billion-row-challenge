#!/usr/bin/zsh

echo "Prepare.sh..."

if [ ! -f target/experiment-1.jar ]; then
  echo "Recompile..."
  mvn package
else
  echo "File is there, no need to do anything..."
fi