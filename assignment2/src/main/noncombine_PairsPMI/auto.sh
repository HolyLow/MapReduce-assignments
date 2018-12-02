#!/bin/bash

class="PairsPMI"
javafile="${class}.java"
version="noncombine_PairsPMI"
jarfile="${version}.jar"

inputRoot="/data/tokens"
inputScale="medium"
inputDir="${inputRoot}/${inputScale}"

outputRoot="/output/assignment2"
outputDir="${outputRoot}/${version}/${inputScale}"

javac -classpath `hadoop classpath` $javafile
jar -cvf $jarfile ./${class}*.class
time hadoop jar $jarfile $class -input $inputDir -output $outputDir


