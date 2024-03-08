#!/usr/bin/zsh

echo "Prepare.sh..."

source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk use java 21.0.2-graal 1>&2

if [ ! -f target/experiment-1.jar ]; then
  echo "Recompile..."
  mvn package
else
  echo "File is there, no need to do anything..."
fi