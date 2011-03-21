#!/bin/sh
java -server -XX:CompileThreshold=0 -ea -Xmx65m -XX:+HeapDumpOnOutOfMemoryError -Dnavigators.smart.ebawa.configfile=config/ebawa.config -cp 'dist/modular_SMaRt.jar:dist/lib/netty-3.1.1.GA.jar:dist/lib/ConfigHandler.jar' -Djava.util.logging.config.file=config/logging.properties navigators.smart.tom.demo.ThroughputLatencyTestServer $1 $2 $3
