#!/bin/bash
java -server -XX:CompileThreshold=0 -ea -Xmx265m -XX:+HeapDumpOnOutOfMemoryError -Dnavigators.smart.ebawa.configfile=config/ebawa.config -cp dist/modular_SMaRt.jar:lib/netty-3.1.1.GA.jar:lib/ConfigHandler.jar:lib/commons-math-2.2.jar -Djava.util.logging.config.file=config/logging.properties navigators.smart.tom.demo.ThroughputLatencyTestClient $*
