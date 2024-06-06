#!/usr/bin/zsh

echo "Prepare native.sh..."

source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk use java 21.0.2-graal 1>&2

if [ ! -f textExperiment_native ]; then
  echo "Recompile..."
#  mvn package

  native-image --gc=epsilon -O3 -march=native --enable-preview -cp target/experiment-1.jar -o experiment org.example.Main
else
  echo "File is there, no need to do anything..."
fi



