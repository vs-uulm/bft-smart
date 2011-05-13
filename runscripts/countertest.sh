#!/bin/sh
java -server -XX:CompileThreshold=0 -ea -Xmx65m -XX:+HeapDumpOnOutOfMemoryError -cp 'dist/modular_SMaRt.jar:lib/netty-3.1.1.GA.jar:lib/ConfigHandler.jar' -Djava.util.logging.config.file=config/logging.properties navigators.smart.tom.demo.CounterServer $1
