Introduction 
============
This ia a spike code carried on 30/08/2017. Actual code is grabbed as it is  from a google lab project : (https://codelabs.developers.google.com/codelabs/speaking-with-a-webpage/index.html#0)
Following are key features for this service:

**Client**
* Java script, HTML, CSS
* Web Audio API to capture audio from microphone
* Web socket handshake
* Transcript writing in a div
* Reopen socket when terminated by server

**Server**
* Jetty servlet container and webserver
* Websocket to handle stream from client and dispatch to google speech API

Setup
=====
1. Install Java 8
2. Install Maven
3. Create a Google project
4. Enable Speech API
5. Download Service account keys (Json file and put it in **resources** folder in the code)

Command to run:
```
mvn clean jetty:run
```
This will run it in active mode and if you want to run it in background then:
```
nohup mvn jetty:run > output.log 2>&1 &
```


Testing
=======
For testing point your Chrome browser to:
https://localhost:8443