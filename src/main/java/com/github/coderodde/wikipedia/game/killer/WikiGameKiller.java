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
    
    private static final String WIKIPEDIA_URL_FORMAT =
            "^((http:\\/\\/)|(https:\\/\\/))?..\\.wikipedia\\.org\\/wiki\\/.+$";
    
    private static final Pattern WIKIPEDIA_URL_FORMAT_PATTERN = 
            Pattern.compile(WIKIPEDIA_URL_FORMAT);

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
                System.out.printf(
                        "[STATISTICS] Duration: %d milliseconds, " + 
                        "expanded nodes: %d nodes.\n",
                        finder.getDurationMillis(),
                        finder.getNumberOfExpandedNodes());
            }
            
            for (int i = 0; i < path.size(); i++) {
                final String title = path.get(i);
                final String url = wrapToUrl(title, languageCodeTarget);
                path.set(i, url);
            }
            
            for (final String articleTitle : path) {
                System.out.println(articleTitle);
            }
            
            if (commandLineArguments.outFileName != null) {
                saveFile(commandLineArguments.outFileName,
                         path,
                         commandLineArguments.printStatistics,
                         finder.getDuration(),
                         finder.getNumberOfExpandedNodes());
            }
            
        } catch (final CommandLineException ex) {
            System.out.printf("ERROR: %s\n", ex.getMessage());
            System.exit(1);
        }
    }
    
    private static void saveFile(final String fileName,
                                 final List<String> path,
                                 final boolean showStats,
                                 final long duration,
                                 final int numberOfExpandedNodes) {
        File file = new File(fileName);
        
        if (!file.exists()) {
            try {
                if (!file.createNewFile()) {
                    throw new CommandLineException(
                            String.format(
                                    "Could not create file \"%s\".", 
                                    fileName));
                }
            } catch (IOException ex) {
                throw new CommandLineException(
                        String.format(
                                "Could not create file \"%s\".", 
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
                    getPathListHtml(path));
        } else {
            html = String.format(HTML_TEMPLATE, "", getPathListHtml(path));
        }
        
        try {
            final BufferedWriter bufferedWriter = 
                    new BufferedWriter(new FileWriter(fileName));
            
            bufferedWriter.write(html);
            bufferedWriter.close();
        } catch (IOException ex) {
            throw new CommandLineException(
                    "Could not create a buffered writer.");
        }
    }
    
    private static String getPathListHtml(final List<String> articleUrlPath) {
        StringBuilder stringBuilder = new StringBuilder();
        
        stringBuilder.append("<ol>\n");

        for (final String articleUrl : articleUrlPath) {
            stringBuilder.append("                <li><a href=\"")
                         .append(articleUrl)
                         .append("\">")
                         .append(articleUrl)
                         .append("</a></li>\n");
        }
        
        stringBuilder.append("            </ol>\n");
        return stringBuilder.toString();
    }
    
    private static String wrapToUrl(final String articleTitle, 
                                    final String languageCode) {
        
        return String.format("https://%s.wikipedia.org/wiki/%s", 
                             languageCode, 
                             URLEncoder.encode(
                                     articleTitle, 
                                     Charset.forName("UTF-8")));
    }
    
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
                        %s
                    </div>
                <body>
            </html>
            """;
    
    private static void printHelp() {
        System.out.printf(
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
    
    private static CommandLineArguments parseCommandLineArguments(String[] args) {
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
    
    private static Map<String, Integer> computeArgumentMap(String[] args) {
        Map<String, Integer> map = new HashMap<>(args.length);
        
        for (int i = 0; i < args.length; i++) {
            map.put(args[i], i);
        }
        
        return map;
    }
    
    private static String getArgumentStringValue(String[] args, int index) {
        checkValueFitsInCommandLine(args, index);
        return args[index];
    }
    
    private static int getArgumentIntValue(String[] args, int index) {
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
    
    private static void checkValueFitsInCommandLine(String[] args, int index) {
        if (index >= args.length) {
            throw new CommandLineException(
                    String.format(
                            "The argument \"%s\" has no value.", 
                            args[index]));
        }
    }
    
    public static final class CommandLineException extends RuntimeException {
        
        CommandLineException(final String exceptionMessage) {
            super(exceptionMessage);
        }
    }
    
    private static String getPath() {
        return new java.io.File(WikiGameKiller.class.getProtectionDomain()
          .getCodeSource()
          .getLocation()
          .getPath())
          .getName();
    }
    
    static void checkWikipediaArticleFormat(final String url) {
        Matcher matcher = WIKIPEDIA_URL_FORMAT_PATTERN.matcher(url);
        
        if (!matcher.find()) {
            throw new CommandLineException(
                    String.format(
                            "URL \"%s\" is not a valid Wikipedia URL.",
                            url));
        }
    }
    
    private static List<String> stripHostAddress(final List<String> urlList) {
        List<String> result = new ArrayList<>(urlList.size());
        
        for (final String url : urlList) {
            result.add(url.substring(url.lastIndexOf("/") + 1));
        }
        
        return result;
    }
    
    private static final class ForwardLinkExpander 
            extends AbstractNodeExpander<String> {

        private final ForwardWikipediaGraphNodeExpander expander;
        
        public ForwardLinkExpander(final String languageCode) {
            this.expander = new ForwardWikipediaGraphNodeExpander(languageCode);
        }
        
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
    
    private static final class BackwardLinkExpander 
            extends AbstractNodeExpander<String> {

        private final BackwardWikipediaGraphNodeExpander expander;
        
        public BackwardLinkExpander(final String languageCode) {
            this.expander = 
                    new BackwardWikipediaGraphNodeExpander(languageCode);
        }
        
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
}
