A place to learn and experiment with java 1brc: https://github.com/gunnarmorling/1brc

Using GraalVM, visualVM, jbang and async profilers.

Following tutorial found at https://questdb.io/blog/billion-row-challenge-step-by-step/

---

Execution times:
First solution - no optimization whatsoever:
51~53s


---

Useful commands:

Measure execution time
`time ./run.sh`

Run visualvm once installed  
`/home/paulograbin/.sdkman/candidates/visualvm/current/bin/visualvm`

Run perf
`perf stat -e branches,branch-misses,cache-references,cache-misses,cycles,instructions,idle-cycles-backend,idle-cycles-frontend,task-clock -- java --enable-preview --class-path target/experiment-1.jar org.example.Main`


Generate flamegraph
`jbang --javaagent=ap-loader@jvm-profiling-tools/ap-loader=start,event=cpu,file=profile.html --enable-preview -m org.example.Main target/experiment-1.jar`


Java Flight Recorder
`java --enable-preview -XX:StartFlightRecording=duration=15s,settings=profile,name=testeGrabs,filename=flight-recorder.jfr,dumponexit=true -cp target/experiment-1.jar org.example.Main`




