package com.github.coderodde.wikipedia.game.killer;

import com.github.coderodde.graph.pathfinding.delayed.AbstractNodeExpander;
import com.github.coderodde.graph.pathfinding.delayed.impl.ThreadPoolBidirectionalBFSPathFinder;
import com.github.coderodde.graph.pathfinding.delayed.impl.ThreadPoolBidirectionalBFSPathFinderBuilder;
import com.github.coderodde.graph.pathfinding.delayed.impl.ThreadPoolBidirectionalBFSPathFinderSearchBuilder;
import com.github.coderodde.wikipedia.graph.expansion.BackwardWikipediaGraphNodeExpander;
import com.github.coderodde.wikipedia.graph.expansion.ForwardWikipediaGraphNodeExpander;
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

    private static final class CommandLineSettings {
        String source           = null;
        String target           = null;
        int threads             = ThreadPoolBidirectionalBFSPathFinder.DEFAULT_NUMBER_OF_THREADS;
        int trials              = ThreadPoolBidirectionalBFSPathFinder.DEFAULT_NUMBER_OF_MASTER_TRIALS;
        int masterSleepDuration = ThreadPoolBidirectionalBFSPathFinder.DEFAULT_MASTER_THREAD_SLEEP_DURATION_MILLIS;
        int slaveSleepDuration  = ThreadPoolBidirectionalBFSPathFinder.DEFAULT_SLAVE_THREAD_SLEEP_DURATION_MILLIS;
        int expansionTimeout    = ThreadPoolBidirectionalBFSPathFinder.DEFAULT_EXPANSION_JOIN_DURATION_MILLIS;
        int lockWaitDuration    = ThreadPoolBidirectionalBFSPathFinder.DEFAULT_LOCK_WAIT_MILLIS;
        boolean printHelp       = false;
        boolean printStatistics = false;
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            final String NL = "\n";
            
            sb.append(source).append(NL);
            sb.append(target).append(NL);
            sb.append(threads).append(NL);
            sb.append(trials).append(NL);
            sb.append(masterSleepDuration).append(NL);
            sb.append(slaveSleepDuration).append(NL);
            sb.append(expansionTimeout).append(NL);
            sb.append(lockWaitDuration).append(NL);
            sb.append(printHelp).append(NL);
            sb.append(printStatistics).append(NL);
            
            return sb.toString();
        }
    }
    
    public static void main(String[] args) {
        try {
            CommandLineSettings commandLineSettings = 
                    parseCommandLineSettings(args);
            
            if (commandLineSettings.printHelp) {
                printHelp();
                return;
            }
            
            String source = commandLineSettings.source;
            String target = commandLineSettings.target;
            
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
                    .withJoinDurationMillis(commandLineSettings.expansionTimeout)
                    .withLockWaitMillis(commandLineSettings.lockWaitDuration)
                    .withMasterThreadSleepDurationMillis(commandLineSettings.masterSleepDuration)
                    .withSlaveThreadSleepDurationMillis(commandLineSettings.slaveSleepDuration)
                    .withNumberOfMasterTrials(commandLineSettings.trials)
                    .withNumberOfRequestedThreads(commandLineSettings.threads)
                    .end();
            
            List<String> path = 
                    ThreadPoolBidirectionalBFSPathFinderSearchBuilder
                            .<String>withPathFinder(finder)
                            .withSourceNode(source)
                            .withTargetNode(target)
                            .withForwardNodeExpander(forwardLinkExpander)
                            .withBackwardNodeExpander(backwardLinkExpander)
                            .search();
            
            if (commandLineSettings.printStatistics) {
                System.out.printf(
                        "[STATISTICS] Duration: %d milliseconds, " + 
                        "expanded nodes: %d nodes.\n",
                        finder.getDurationMillis(),
                        finder.getNumberOfExpandedNodes());
            }
            
            path.forEach(System.out::println);
            
        } catch (final CommandLineException ex) {
            System.out.printf("ERROR: %s\n", ex.getMessage());
            System.exit(1);
        }
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
    
    private static CommandLineSettings parseCommandLineSettings(String[] args) {
        Map<String, Integer> map = computeArgumentMap(args);
        
        if (map.containsKey("--help")) {
            if (map.size() > 1) {
                throw new CommandLineException(
                        "--help must be the only argument.");
            }
            
            CommandLineSettings commandLineSettings = new CommandLineSettings();
            commandLineSettings.printHelp = true;
            return commandLineSettings;
        }
        
        if (!map.containsKey("--source")) {
            throw new CommandLineException("--source option is missing.");
        }
        
        if (!map.containsKey("--target")) {
            throw new CommandLineException("--target option is missing.");
        }
        
        CommandLineSettings commandLineSettings = new CommandLineSettings();
        
        commandLineSettings.source = 
                getArgumentStringValue(args, map.get("--source") + 1);
        
        commandLineSettings.target = 
                getArgumentStringValue(args, map.get("--target") + 1);
        
        if (map.containsKey("--stats")) {
            commandLineSettings.printStatistics = true;
        }
        
        if (map.containsKey("--threads")) {
            int index = map.get("--threads");
            commandLineSettings.threads = getArgumentIntValue(args, index + 1);
        }
        
        if (map.containsKey("--master-trials")) {
            int index = map.get("--master-trials");
            commandLineSettings.trials = getArgumentIntValue(args, index + 1);
        }
        
        if (map.containsKey("--master-sleep-duration")) {
            int index = map.get("--master-sleep-duration");
            commandLineSettings.masterSleepDuration = 
                    getArgumentIntValue(args, index + 1);
        }
        
        if (map.containsKey("--slave-sleep-duration")) {
            int index = map.get("--slave-sleep-duration");
            commandLineSettings.slaveSleepDuration = 
                    getArgumentIntValue(args, index + 1);
        }
        
        if (map.containsKey("--expansion-timeout")) {
            int index = map.get("--expansion-timeout");
            commandLineSettings.expansionTimeout = 
                    getArgumentIntValue(args, index + 1);
        }
        
        if (map.containsKey("--lock-wait-timeout")) {
            int index = map.get("--lock-wait-timeout");
            commandLineSettings.lockWaitDuration = 
                    getArgumentIntValue(args, index + 1);
        }
        
        return commandLineSettings;
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
