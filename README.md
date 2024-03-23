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

### Example search

```
java -jar WikiGameKiller.java-1.0.0.jar --source https://en.wikipedia.org/wiki/Korie --target https://en.wikipedia.org/wiki/Bugatti --threads 300 --stats --out ..\index.html --expansion-timeout 4000
```
A sample output is:
```
[STATISTICS] Duration: 5284 milliseconds, expanded nodes: 1539 nodes.
https://en.wikipedia.org/wiki/Korie                  [Korie]
https://en.wikipedia.org/wiki/Korie_Homan            [Korie Homan]
https://en.wikipedia.org/wiki/Australian_Open        [Australian Open]
https://en.wikipedia.org/wiki/Australian_Grand_Prix  [Australian Grand Prix]
https://en.wikipedia.org/wiki/Bugatti                [Bugatti]
```
The above will also generate a convenient HTML file listing the path:
![image](https://github.com/coderodde/WikiGameKiller.java/assets/1770505/8423a00d-b03a-4ccd-b874-4487d6181346)

## Software limitations
Sometimes it may happen that the link is in a section that must be made visible by pressing the `Show` link. For example:
![wiki](https://github.com/coderodde/WikiGameKiller.java/assets/1770505/ccf97b1f-498d-46ed-aea6-6d7273b652ec)

Note above that we need to press the `Show` link in [Australian Open](https://en.wikipedia.org/wiki/Australian_Open) in order to expose the link to [Australian Grand Prix](https://en.wikipedia.org/wiki/Australian_Grand_Prix	).

Also, the search may return an empty path even if one non-empty path exists. If something like that happens, it is advised to tweek the search parameters and retry until successful.

Note that while searching with too many threads, the Wikipedia API may start to respond with HTTP 429 (Too Many Requests). If that happens, try to reduce the number of threads in use (the `--threads` argument).

Finally, the program cannot switch between Wikipedia languages.
