package com.github.coderodde.wikipedia.game.killer;

import com.github.coderodde.graph.pathfinding.delayed.AbstractNodeExpander;
import com.github.coderodde.graph.pathfinding.delayed.impl.ThreadPoolBidirectionalBFSPathFinder;
import com.github.coderodde.graph.pathfinding.delayed.impl.ThreadPoolBidirectionalBFSPathFinderBuilder;
import com.github.coderodde.graph.pathfinding.delayed.impl.ThreadPoolBidirectionalBFSPathFinderSearchBuilder;
import com.github.coderodde.wikipedia.graph.expansion.BackwardWikipediaGraphNodeExpander;
import com.github.coderodde.wikipedia.graph.expansion.ForwardWikipediaGraphNodeExpander;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WikiGameKiller {
    
    /**
     * Specifies the HTML file format.
     */
    private static final String HTML_TEMPLATE = 
            """
            <!DOCTYPE html>
            <html>
                <head>
                    <title>WikiGameKiller.java</title>
                </head>
                <body>
                    <div>%s</div>
                    <div>
                        <h3>Shortest path:</h3>
                        <table>
            %s            </table>
                    </div>
                <body>
            </html>
            """;
    
    /**
     * Custom {@link PrintStream} for dealing with UTF-8 text.
     */
    private static PrintStream OUT;
    
    /**
     * The Wikipedia URL format.
     */
    private static final String WIKIPEDIA_URL_FORMAT =
            "^((http:\\/\\/)|(https:\\/\\/))?..\\.wikipedia\\.org\\/wiki\\/.+$";
    
    /**
     * The Wikipedia URL regular expression pattern object.
     */
    private static final Pattern WIKIPEDIA_URL_FORMAT_PATTERN = 
            Pattern.compile(WIKIPEDIA_URL_FORMAT);
    
    static {
        try {
            OUT = new PrintStream(System.out, true, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            System.out.printf("ERROR: %.\n", ex.getMessage());
            System.exit(2);
        }
    }
            
    /**
     * This class simply holds all the command line arguments.
     */
    private static final class CommandLineArguments {
        String source           = null;
        String target           = null;
        String outFileName      = null;
        int threads             = ThreadPoolBidirectionalBFSPathFinder.DEFAULT_NUMBER_OF_THREADS;
        int trials              = ThreadPoolBidirectionalBFSPathFinder.DEFAULT_NUMBER_OF_MASTER_TRIALS;
        int masterSleepDuration = ThreadPoolBidirectionalBFSPathFinder.DEFAULT_MASTER_THREAD_SLEEP_DURATION_MILLIS;
        int slaveSleepDuration  = ThreadPoolBidirectionalBFSPathFinder.DEFAULT_SLAVE_THREAD_SLEEP_DURATION_MILLIS;
        int expansionTimeout    = ThreadPoolBidirectionalBFSPathFinder.DEFAULT_EXPANSION_JOIN_DURATION_MILLIS;
        int lockWaitDuration    = ThreadPoolBidirectionalBFSPathFinder.DEFAULT_LOCK_WAIT_MILLIS;
        boolean printHelp       = false;
        boolean printStatistics = false;
    }
    
    public static void main(String[] args) {
        
        try {
            CommandLineArguments commandLineArguments = 
                    parseCommandLineArguments(args);
            
            if (commandLineArguments.printHelp) {
                printHelp();
                return;
            }
            
            String source = commandLineArguments.source;
            String target = commandLineArguments.target;
            
            checkWikipediaArticleFormat(source);
            checkWikipediaArticleFormat(target);
            
            String languageCodeSource = getLanguageCode(source);
            String languageCodeTarget = getLanguageCode(target);
            
            if (!languageCodeSource.equals(languageCodeTarget)) {
                throw new CommandLineException(
                        String.format(
                                "Language code mismatch: \"%s\" vs \"%s\".", 
                                languageCodeSource, 
                                languageCodeTarget));
            }
            
            // Get the article names:
            source = source.substring(source.lastIndexOf("/") + 1);
            target = target.substring(target.lastIndexOf("/") + 1);
            
            source = URLDecoder.decode(source, Charset.forName("UTF-8"));
            target = URLDecoder.decode(target, Charset.forName("UTF-8"));
            
            ForwardLinkExpander forwardLinkExpander = 
                    new ForwardLinkExpander(languageCodeSource);
            
            BackwardLinkExpander backwardLinkExpander = 
                    new BackwardLinkExpander(languageCodeTarget);
            
            validateTerminalNodes(forwardLinkExpander,
                                  backwardLinkExpander, 
                                  source,
                                  target);
            
            ThreadPoolBidirectionalBFSPathFinder<String> finder = 
                    ThreadPoolBidirectionalBFSPathFinderBuilder.<String>begin()
                    .withJoinDurationMillis(commandLineArguments.expansionTimeout)
                    .withLockWaitMillis(commandLineArguments.lockWaitDuration)
                    .withMasterThreadSleepDurationMillis(commandLineArguments.masterSleepDuration)
                    .withSlaveThreadSleepDurationMillis(commandLineArguments.slaveSleepDuration)
                    .withNumberOfMasterTrials(commandLineArguments.trials)
                    .withNumberOfRequestedThreads(commandLineArguments.threads)
                    .end();
            
            List<String> path = 
                    ThreadPoolBidirectionalBFSPathFinderSearchBuilder
                            .<String>withPathFinder(finder)
                            .withSourceNode(source)
                            .withTargetNode(target)
                            .withForwardNodeExpander(forwardLinkExpander)
                            .withBackwardNodeExpander(backwardLinkExpander)
                            .search();
            
            if (commandLineArguments.printStatistics) {
                OUT.printf(
                        "[STATISTICS] Duration: %d milliseconds, " + 
                        "expanded nodes: %d nodes.\n",
                        finder.getDurationMillis(),
                        finder.getNumberOfExpandedNodes());
            }
            
            final List<LinkPathNode> linkPathNodeList = 
                    new ArrayList<>(path.size());
            
            for (int i = 0; i < path.size(); i++) {
                String title = path.get(i);
                title = URLDecoder.decode(title, Charset.forName("UTF-8"));
                title = title.replace("_", " ");
                
                String url = wrapToUrl(title, languageCodeTarget);
                linkPathNodeList.add(new LinkPathNode(url, title));
            }
            
            final int maximumUrlLength = getMaximumUrlLength(linkPathNodeList);
            
            for (final LinkPathNode linkPathNode : linkPathNodeList) {
                OUT.println(
                        linkPathNode.toCommandLineRow(maximumUrlLength));
            }
            
            if (commandLineArguments.outFileName != null) {
                saveFile(commandLineArguments.outFileName,
                         linkPathNodeList,
                         commandLineArguments.printStatistics,
                         finder.getDuration(),
                         finder.getNumberOfExpandedNodes());
            }
            
        } catch (final CommandLineException ex) {
            OUT.printf("ERROR: %s\n", ex.getMessage());
            System.exit(1);
        }
    }

    /**
     * Checks that the argument array has space for the argument at index 
     * {@code index}. If not, an instance of {@link CommandLineException} is
     * thrown.
     * 
     * @param args  the argument array.
     * @param index the index of an argument.
     * 
     * @throws CommandLineException if {@code index} does not fit in the input
     *                              argument array.
     */
    private static void checkValueFitsInCommandLine(String[] args, int index) {
        if (index >= args.length) {
            throw new CommandLineException(
                    String.format(
                            "The argument \"%s\" has no value.", 
                            args[index]));
        }
    }

    /**
     * Checks that the input URL {@code url} is a valid Wikipedia URL.
     * 
     * @param url the URL to check.
     * 
     * @throws an instance of {@link CommandLineException} if the input URL is 
     *         not a valid Wikipedia URL.
     */
    static void checkWikipediaArticleFormat(final String url) {
        Matcher matcher = WIKIPEDIA_URL_FORMAT_PATTERN.matcher(url);
        
        if (!matcher.find()) {
            throw new CommandLineException(
                    String.format(
                            "URL \"%s\" is not a valid Wikipedia URL.",
                            url));
        }
    }

    /**
     * Computes a map mapping each command line argument to its appearance 
     * index.
     * 
     * @param args the argument array.
     * 
     * @return a map mapping each command line argument to its appearnce index.
     */
    private static Map<String, Integer> computeArgumentMap(String[] args) {
        Map<String, Integer> map = new HashMap<>(args.length);
        
        for (int i = 0; i < args.length; i++) {
            map.put(args[i], i);
        }
        
        return map;
    }
    
    /**
     * Attemtps to read an integer value of the {@code index}th argument.
     * 
     * @param args  the argument array.
     * @param index the argument array index.
     * 
     * @return the integer value of the {@code index}th argument.
     * 
     * @throws an instance of {@link CommandLineException} if either the index
     *         is outside of the argument array, or the {@code index}th argument
     *         is not an integer.
     */
    private static int getArgumentIntValue(final String[] args,
                                           final int index) {
        
        checkValueFitsInCommandLine(args, index);
        
        try {
            return Integer.parseInt(args[index]);
        } catch (final NumberFormatException ex) {
            throw new CommandLineException(
                    String.format(
                            "\"%s\" is not an integer.",
                            args[index + 1]));
        }
    }
    
    /**
     * Attempts to read a string value of the {@code index}th argument.
     * 
     * @param args  the argument array.
     * @param index the argument array index.
     * 
     * @return the string value of the {@code index}th argument.
     * 
     * @throws an instance of {@link CommandLineException} if the index is 
     *         outside of the argument array.
     */
    private static String getArgumentStringValue(String[] args, int index) {
        checkValueFitsInCommandLine(args, index);
        return args[index];
    }
    
    /**
     * Returns the ISO language code used in the input URL {@code url}.
     * 
     * @param url the URL to extract the country code from.
     * 
     * @return the language code.
     * 
     * @throws an instance of {@link CommandLineException} if the resulting 
     *         language code does not conform to ISO.
     */
    private static String getLanguageCode(String url) {
        final String secureProtocol = "https://";
        final String insecureProtocol = "http://";
        
        if (url.startsWith(secureProtocol)) {
            url = url.substring(secureProtocol.length());
        } else if (url.startsWith(insecureProtocol)) {
            url = url.substring(insecureProtocol.length());
        }
        
        final String languageCode = url.substring(0, 2);
        
        if (!Arrays.asList(Locale.getISOLanguages()).contains(languageCode)) {
            throw new CommandLineException(
                    String.format(
                            "Unknown language code: %s",
                            languageCode));
        }
        
        return languageCode;
    }
    
    /**
     * Returns the maximum URL length extracted from {@code linkPathNodeList}.
     * 
     * @param linkPathNodeList the list of {@link LinkPathNode} objects.
     * 
     * @return the maximum URL length in {@code linkPathNodeList}. 
     */
    private static int getMaximumUrlLength(
            final List<LinkPathNode> linkPathNodeList) {
        
        int maximumUrlLength = 1;
        
        for (final LinkPathNode linkPathNode : linkPathNodeList) {
            maximumUrlLength = Math.max(maximumUrlLength,
                                        linkPathNode.url.length());
        }
        
        return maximumUrlLength;
    }
    
    /**
     * Returns the current name of this .jar file. Used in the help message.
     * 
     * @return the current name of this .jar file.
     */
    private static String getPath() {
        return new java.io.File(WikiGameKiller.class.getProtectionDomain()
          .getCodeSource()
          .getLocation()
          .getPath())
          .getName();
    }
    
    /**
     * Returns the HTML code for the link path table.
     * 
     * @param linkPathNodeList the list of link path nodes.
     * 
     * @return the HTML code for the link path table.
     */
    private static String getPathTableHtml(
            final List<LinkPathNode> linkPathNodeList) {
        
        StringBuilder stringBuilder = new StringBuilder();
        
        int lineNumber = 1;
        
        for (final LinkPathNode linkPathNode : linkPathNodeList) {
            stringBuilder.append(linkPathNode.toTableRowHtml(lineNumber++));
        }
        
        return stringBuilder.toString();
    }
    
    /**
     * Parses the entire command line excluding the Java VM call 
     * ({@code java -jar FILE.jar}).
     * 
     * @param args the array of arguments.
     * 
     * @return the {@code CommandLineArguments} object describing the command 
     *         line invocation.
     * 
     * @throws CommandLineException if there are problems with the command line.
     */
    private static CommandLineArguments
         parseCommandLineArguments(String[] args) {
             
        Map<String, Integer> map = computeArgumentMap(args);
        
        if (map.containsKey("--help")) {
            if (map.size() > 1) {
                throw new CommandLineException(
                        "--help must be the only argument.");
            }
            
            CommandLineArguments commandLineSettings = new CommandLineArguments();
            commandLineSettings.printHelp = true;
            return commandLineSettings;
        }
        
        if (!map.containsKey("--source")) {
            throw new CommandLineException("--source option is missing.");
        }
        
        if (!map.containsKey("--target")) {
            throw new CommandLineException("--target option is missing.");
        }
        
        CommandLineArguments commandLineArguments = new CommandLineArguments();
        
        commandLineArguments.source = 
                getArgumentStringValue(args, map.get("--source") + 1);
        
        commandLineArguments.target = 
                getArgumentStringValue(args, map.get("--target") + 1);
        
        if (map.containsKey("--out")) {
            commandLineArguments.outFileName = 
                getArgumentStringValue(args, map.get("--out") + 1);
        }
        
        if (map.containsKey("--stats")) {
            commandLineArguments.printStatistics = true;
        }
        
        if (map.containsKey("--threads")) {
            int index = map.get("--threads");
            commandLineArguments.threads = getArgumentIntValue(args, index + 1);
        }
        
        if (map.containsKey("--master-trials")) {
            int index = map.get("--master-trials");
            commandLineArguments.trials = getArgumentIntValue(args, index + 1);
        }
        
        if (map.containsKey("--master-sleep-duration")) {
            int index = map.get("--master-sleep-duration");
            commandLineArguments.masterSleepDuration = 
                    getArgumentIntValue(args, index + 1);
        }
        
        if (map.containsKey("--slave-sleep-duration")) {
            int index = map.get("--slave-sleep-duration");
            commandLineArguments.slaveSleepDuration = 
                    getArgumentIntValue(args, index + 1);
        }
        
        if (map.containsKey("--expansion-timeout")) {
            int index = map.get("--expansion-timeout");
            commandLineArguments.expansionTimeout = 
                    getArgumentIntValue(args, index + 1);
        }
        
        if (map.containsKey("--lock-wait-timeout")) {
            int index = map.get("--lock-wait-timeout");
            commandLineArguments.lockWaitDuration = 
                    getArgumentIntValue(args, index + 1);
        }
        
        return commandLineArguments;
    }
         
    /**
     * Prints the help message.
     */
    private static void printHelp() {
        OUT.printf(
        """
        usage: %s
            --source SOURCE_ARTICLE_URL
            --target TARGET_ARTICLE_URL
           [--threads NUMBER_OF_THREADS]
           [--master-trials TRIALS]
           [--master-sleep-duration MASTER_SLEEP_MILLIS]
           [--slave-sleep-duration SLAVE_SLEEP_MILLIS]
           [--expansion-timeout EXPANSION_TIMEOUT_MILLIS]
           [--lock-wait-timeout LOCK_WAIT_MILLIS]
           [--help]
           [--stats]
           [--out OUTPUT_HTML_FILE_NAME]
        
            where:
                NUMBER_OF_THREADS        - the total number of threads.        Default is %d.
                TRIALS                   - the number of master thread trials. Default is %d.
                MASTER_SLEEP_MILLIS      - the number of milliseconds.         Default is %d.
                SLAVE_SLEEP_MILLIS       - the number of milliseconds.         Default is %d.
                EXPANSION_TIMEOUT_MILLIS - the number of milliseconds.         Default is %d.
                LOCK_WAIT_MILLIS         - the number of milliseconds.         Default is %d.
        
                --help  - Print this help message.
                --stats - Print the search statistics after the search.
        """,
        getPath(),
        ThreadPoolBidirectionalBFSPathFinder.DEFAULT_NUMBER_OF_THREADS,
        ThreadPoolBidirectionalBFSPathFinder.DEFAULT_NUMBER_OF_MASTER_TRIALS,
        ThreadPoolBidirectionalBFSPathFinder.DEFAULT_MASTER_THREAD_SLEEP_DURATION_MILLIS,
        ThreadPoolBidirectionalBFSPathFinder.DEFAULT_SLAVE_THREAD_SLEEP_DURATION_MILLIS,
        ThreadPoolBidirectionalBFSPathFinder.DEFAULT_EXPANSION_JOIN_DURATION_MILLIS,
        ThreadPoolBidirectionalBFSPathFinder.DEFAULT_LOCK_WAIT_MILLIS
        );
    }
    
    /**
     * Attempts to save the results to an HTML file.
     * 
     * @param fileName              the name of the saved file.
     * @param linkPathNodeList      the link path node list.
     * @param showStats             if {@code true}, statistics will be saved.
     * @param duration              the duration of the search.
     * @param numberOfExpandedNodes the number of expanded nodes.
     * 
     * @throws CommandLineException if something goes wrong.
     */
    private static void saveFile(final String fileName,
                                 final List<LinkPathNode> linkPathNodeList,
                                 final boolean showStats,
                                 final long duration,
                                 final int numberOfExpandedNodes) {
        File file = new File(fileName);
        
        if (file.exists()) {
            if (!file.delete()) {
                throw new CommandLineException(
                        String.format(
                                "Could not delete the file \"%s\".", 
                                fileName));
            }
        }
        
        String html;
        
        if (showStats) {
            html = String.format(
                    HTML_TEMPLATE,
                    String.format(
                            "Duration: %d milliseconds, expanded %d nodes.", 
                            duration, 
                            numberOfExpandedNodes),
                    getPathTableHtml(linkPathNodeList));
        } else {
            html = String.format(
                    HTML_TEMPLATE, 
                    "",
                    getPathTableHtml(linkPathNodeList));
        }
        
        try (BufferedWriter bufferedWriter =
                new BufferedWriter(new FileWriter(file))) {
            
            bufferedWriter.write(html);
            bufferedWriter.close();
        } catch (IOException ex) {
            throw new CommandLineException(
                    "Could not create a buffered writer.");
        }
    }
    
    /**
     * Strinps the protocol, host name and {@code wiki} path from each URL in
     * the {@code urlList}. For example, 
     * {@code https://en.wikipedia.org/en/Hiisi} becomes simply {@code Hiisi}.
     * 
     * @param urlList the list of URLs.
     * @return the list of stripped URLs.
     */
    private static List<String> stripHostAddress(final List<String> urlList) {
        List<String> result = new ArrayList<>(urlList.size());
        
        for (final String url : urlList) {
            result.add(url.substring(url.lastIndexOf("/") + 1));
        }
        
        return result;
    }
    
    /**
     * Makes sure that the two terminal nodes are valid Wikipedia article nodes.
     * 
     * @param forwardExpander  the forward link expander.
     * @param backwardExpander the backward link expander.
     * @param source           the source node.
     * @param target           the target node.
     * 
     * @throws CommandLineException if could not validate both terminal nodes.
     */
    private static void validateTerminalNodes(
            final AbstractNodeExpander<String> forwardExpander,
            final AbstractNodeExpander<String> backwardExpander,
            String source, 
            String target) {
        
        if (!forwardExpander.isValidNode(source)) {
            throw new CommandLineException(
                    String.format(
                            "The source node \"%s\" is not a valid node.",
                            source));
        }
        
        if (!backwardExpander.isValidNode(target)) {
            throw new CommandLineException(
                    String.format(
                            "The target node \"%s\" is not a valid node.",
                            target));
        }
    }
    
    /**
     * Wraps {@code articleTitle} and {@code languageCode} into a valid 
     * Wikipedia URL.
     * 
     * @param articleTitle the title of the article.
     * @param languageCode the language code of the article.
     * 
     * @return a full Wikipedia URL.
     */
    private static String wrapToUrl(final String articleTitle, 
                                    final String languageCode) {
        
        return String.format("https://%s.wikipedia.org/wiki/%s", 
                             languageCode, 
                             URLEncoder.encode(
                                     articleTitle, 
                                     Charset.forName("UTF-8")))
                .replace("+", "_");
    }
    
    public static final class CommandLineException extends RuntimeException {
        
        CommandLineException(final String exceptionMessage) {
            super(exceptionMessage);
        }
    }
    
    /**
     * This class implements the forward link expander.
     */
    private static final class ForwardLinkExpander 
            extends AbstractNodeExpander<String> {

        private final ForwardWikipediaGraphNodeExpander expander;
        
        public ForwardLinkExpander(final String languageCode) {
            this.expander = new ForwardWikipediaGraphNodeExpander(languageCode);
        }
        
        /**
         * Generate all the links that this article links to.
         * 
         * @param article the source article of each link.
         * 
         * @return all the article titles that {@code article} links to.
         */
        @Override
        public List<String> generateSuccessors(final String article) {
            List<String> urlList = expander.generateSuccessors(article);
            return stripHostAddress(urlList);
        }

        @Override
        public boolean isValidNode(final String article) {
            return expander.isValidNode(article);
        }
    }
    
    /**
     * This class implements the backward link expander. 
     */
    private static final class BackwardLinkExpander 
            extends AbstractNodeExpander<String> {

        private final BackwardWikipediaGraphNodeExpander expander;
        
        public BackwardLinkExpander(final String languageCode) {
            this.expander = 
                    new BackwardWikipediaGraphNodeExpander(languageCode);
        }
        
        /**
         * Generate all the links pointing to the article {@code article}.
         * 
         * @param article the target article of each link.
         * 
         * @return all the article titles linking to {@code article}.
         */
        @Override
        public List<String> generateSuccessors(final String article) {
            List<String> urlList = expander.generateSuccessors(article);
            return stripHostAddress(urlList);
        }

        @Override
        public boolean isValidNode(final String article) {
            return expander.isValidNode(article);
        }
    }
    
    private static final class LinkPathNode {
        private final String url;
        private final String title;
        
        LinkPathNode(final String url, final String title) {
            this.url = url;
            this.title = title;
        }
        
        String toTableRowHtml(final int lineNumber) {
            return new StringBuilder().append("                ") // Align.
                                      .append("<tr><td>")
                                      .append(lineNumber)
                                      .append(".</td><td><a href=\"")
                                      .append(url)
                                      .append("\">")
                                      .append(url)
                                      .append("</a></td><td>")
                                      .append(title)
                                      .append("</td></tr>\n")   
                                      .toString();
        }
        
        String toCommandLineRow(int urlLength) {
            return String.format(
                    "%-" + urlLength + "s  [%s]", 
                    url.replace("+", "_"),
                    title);
        }
    }
}
