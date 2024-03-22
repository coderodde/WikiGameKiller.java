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

## Software limitations
Sometimes it may happen that a link `A -> B` is returned from the Wikipedia API, yet the web page of `A` does not contain the link to `B`. Also, the search may return an empty path even if one non-empty path exists. If something like that happens, it is advised to tweek the search parameters until successful.
