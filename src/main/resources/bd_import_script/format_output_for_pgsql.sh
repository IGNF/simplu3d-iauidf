#!/bin/bash

while getopts i:r:o: option
do
 case "${option}"
 in
	i) outputRaw=${OPTARG};;
	r) run=${OPTARG};;
	\?) echo "$OPTARG : option invalide"
	    echo "USAGE : format_sdp_output -i /path/to/output.csv -r yymmdd"
	    exit 1;;
 esac
done
outputCleaned="simuls_sdp.csv"
errorsCleaned="simuls_errors.csv"

# first we remove empty lines and comment lines, then we trim spaces and we add the column for 'run' 
echo "writing ${outputCleaned}..."
cat $outputRaw | awk NF | sed '/^#/ d' | tr -d ' ' | awk '{ if(NR==1){print $0";run"} else{print $0";'"$run"'"} }' > $outputCleaned
echo "...done"

echo "writing ${errorsCleaned}..."
# we get the comments lines starting with #, extract the directory number and add the 'run' id
echo "directory;run" > $errorsCleaned 
cat $outputRaw | grep \# | grep -o [0-9]\* | awk '{ print $0";'"$run"'" }' >> $errorsCleaned 
echo "...done"
