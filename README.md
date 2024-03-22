## Installing the WikiGameKiller.java

Run these from your terminal:
```
git clone https://github.com/coderodde/WikiGameKiller.java.git
cd WikiGameKiller.java
mvn clean compile assembly:single
```

Note that this project relies on the next two Maven projects:
- [ThreadPoolBidirectionalBFSPathFinder.java](https://github.com/coderodde/ThreadPoolBidirectionalBFSPathFinder.java)
- [WikipediaGraphNodeExpanders.java](https://github.com/coderodde/WikipediaGraphNodeExpanders.java)

## Running the WikiGameKiller.java

Starting from the same directory in which you installed the program, run these:
```
cd target
java -jar WikiGameKiller.java-1.0.0.jar --help
```
A nice help message will appear telling you how to use it! :-)
