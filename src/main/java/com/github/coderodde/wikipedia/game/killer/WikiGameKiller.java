package com.github.coderodde.wikipedia.game.killer;

import com.github.coderodde.graph.pathfinding.delayed.impl.ThreadPoolBidirectionalBFSPathFinder;
import java.util.HashMap;
import java.util.Map;

public final class WikiGameKiller {

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

            System.out.println(commandLineSettings);

            if (commandLineSettings.printHelp) {
                printHelp();
                return;
            }
            
        } catch (final CommandLineException ex) {
            System.out.printf("ERROR: %s\n", ex.getMessage());
            System.exit(1);
        }
    }
    
    private static void printHelp() {
        System.out.printf(
        """
        usage: mvn exec:java --source SOURCE_ARTICLE_URL
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
        
        if (!map.containsKey("--source")) {
            throw new CommandLineException("--source option is missing.");
        }
        
        if (!map.containsKey("--target")) {
            throw new CommandLineException("--target option is missing.");
        }
        
        CommandLineSettings commandLineSettings = new CommandLineSettings();
        
        commandLineSettings.source = 
                getArgumentStringValue(args, map.get("--source"));
        
        commandLineSettings.target = 
                getArgumentStringValue(args, map.get("--target"));
        
        if (map.containsKey("--help")) {
            commandLineSettings.printHelp = true;
            return commandLineSettings;
        }
        
        if (map.containsKey("--stats")) {
            commandLineSettings.printStatistics = true;
        }
        
        if (map.containsKey("--threads")) {
            int index = map.get("--threads");
            commandLineSettings.threads = getArgumentIntValue(args, index + 1);
        }
        
        if (map.containsKey("--master-trials")) {
            int index = map.get("--master-trials");
            commandLineSettings.threads = getArgumentIntValue(args, index + 1);
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
        
        if (map.containsKey("")) {
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
        return args[index + 1];
    }
    
    private static int getArgumentIntValue(String[] args, int index) {
        checkValueFitsInCommandLine(args, index);
        
        try {
            return Integer.parseInt(args[index + 1]);
        } catch (final NumberFormatException ex) {
            throw new CommandLineException(
                    String.format(
                            "\"%s\" is not an integer.",
                            args[index + 1]));
        }
    }
    
    private static void checkValueFitsInCommandLine(String[] args, int index) {
        if (index + 1 >= args.length) {
            throw new CommandLineException(
                    String.format(
                            "The argument \"%s\" has no value.", 
                            args[index]));
        }
    }
    
    private static final class CommandLineException extends RuntimeException {
        
        CommandLineException(final String exceptionMessage) {
            super(exceptionMessage);
        }
    }
}
