# OscPullPush
Pulls a list of OSC addresses from OSCQuery server and pushes values to another OSC server.

# Running
This is a command line tool that will read a list of OSC addresses from a text file (one per line) and
then use ZeroConf to autodetect a local OSCQuery server to pull OSC address values from.  For each OSC address
found in the response from the OSCQuery server that is also listed in the text file, the tool will convert 
the value to a float and forward the value to OSC server specified on the command line.  This can be used to
initialize the values for a client using Open Stage Control.  The destination OSC host and port should be the
address for OSC inputs for Open Stage Control.

# Example
```
java -jar OSCPullPush-0.0.2.jar oscaddresses.txt localhost 3737
```



