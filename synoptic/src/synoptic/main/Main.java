package synoptic.main;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.junit.runner.JUnitCore;

import plume.Option;
import plume.OptionGroup;
import plume.Options;

import synoptic.algorithms.bisim.Bisimulation;
import synoptic.invariants.ITemporalInvariant;
import synoptic.invariants.NeverConcurrentInvariant;
import synoptic.invariants.TemporalInvariantSet;
import synoptic.invariants.miners.ChainWalkingTOInvMiner;
import synoptic.invariants.miners.DAGWalkingPOInvMiner;
import synoptic.invariants.miners.POInvariantMiner;
import synoptic.invariants.miners.TOInvariantMiner;
import synoptic.invariants.miners.TransitiveClosureInvMiner;
import synoptic.model.ChainsTraceGraph;
import synoptic.model.DAGsTraceGraph;
import synoptic.model.EventNode;
import synoptic.model.PartitionGraph;
import synoptic.model.export.DotExportFormatter;
import synoptic.model.export.GmlExportFormatter;
import synoptic.model.export.GraphExportFormatter;
import synoptic.model.export.GraphExporter;
import synoptic.model.interfaces.IGraph;
import synoptic.model.interfaces.INode;
import synoptic.util.BriefLogFormatter;
import synoptic.util.InternalSynopticException;

public class Main implements Callable<Integer> {
    public static Logger logger = null;

    /**
     * The current Synoptic version.
     */
    public static final String versionString = "0.0.5";

    /**
     * Global source of pseudo-random numbers.
     */
    public static Random random;

    // //////////////////////////////////////////////////
    /**
     * Print the short usage message. This does not include verbosity or
     * debugging options.
     */
    @OptionGroup("General Options")
    @Option(value = "-h Print short usage message", aliases = { "-help" })
    public static boolean help = false;

    /**
     * Print the extended usage message. This includes verbosity and debugging
     * options but not internal options.
     */
    @Option("-H Print extended usage message (includes debugging options)")
    public static boolean allHelp = false;

    /**
     * Print the current Synoptic version.
     */
    @Option(value = "-V Print program version", aliases = { "-version" })
    public static boolean version = false;
    // end option group "General Options"

    // //////////////////////////////////////////////////
    /**
     * Be quiet, do not print much information. Sets the log level to WARNING.
     */
    @OptionGroup("Execution Options")
    @Option(value = "-q Be quiet, do not print much information",
            aliases = { "-quiet" })
    public static boolean logLvlQuiet = false;

    /**
     * Be verbose, print extra detailed information. Sets the log level to FINE.
     */
    @Option(value = "-v Print detailed information during execution",
            aliases = { "-verbose" })
    public static boolean logLvlVerbose = false;

    /**
     * Use the new FSM checker instead of the LTL checker.
     */
    @Option(
            value = "-f Use FSM checker instead of the default NASA LTL-based checker",
            aliases = { "-use-fsm-checker" })
    public static boolean useFSMChecker = true;

    /**
     * Sets the random seed for Synoptic's source of pseudo-random numbers.
     */
    @Option(
            value = "Use a specific random seed for pseudo-random number generator")
    public static Long randomSeed = null;

    /**
     * Use vector time indexes to partition the output graph into a set of
     * graphs, one per distributed system node type.
     */
    @Option(
            value = "Vector time index sets for partitioning the graph by system node type, e.g. '1,2;3,4'")
    public static String separateVTimeIndexSets = null;
    // end option group "Execution Options"

    // //////////////////////////////////////////////////
    /**
     * Regular expression separator string. When lines are found which match
     * this expression, the lines before and after are considered to be in
     * different 'traces', each to be considered an individual sample of the
     * behavior of the system. This is implemented by augmenting the separator
     * expression with an incrementor, (?<SEPCOUNT++>), and adding \k<SEPCOUNT>
     * to the partitioner.
     */
    @OptionGroup("Parser Options")
    @Option(
            value = "-s Partitions separator reg-exp: log lines below and above the matching line are placed into different partitions",
            aliases = { "-partition-separator" })
    public static String separatorRegExp = null;

    /**
     * Regular expressions used for parsing the trace file. This parameter may,
     * and is often repeated, in order to express the different formats of log
     * lines which should be parsed. The ordering is significant, and matching
     * is attempted in the order in which the expressions are given. These
     * 'regular' expressions are a bit specialized, in that they have named
     * group matches of the form (?<name>regex), in order to extract the
     * significant components of the log line. There are a few more variants on
     * this, detailed in the online documentation.
     */
    @Option(
            value = "-r Parser reg-exp: extracts event type and event time from a log line",
            aliases = { "-regexp" })
    public static List<String> regExps = null;

    /**
     * A substitution expression, used to express how to map the trace lines
     * into partition traces, to be considered as an individual sample of the
     * behavior of the system.
     */
    public static final String partitionRegExpDefault = "\\k<FILE>";
    @Option(
            value = "-m Partitions mapping reg-exp: maps a log line to a partition",
            aliases = { "-partition-mapping" })
    public static String partitionRegExp = partitionRegExpDefault;

    /**
     * This flag indicates whether Synoptic should partition traces by file
     */
    public static boolean partitionViaFile = true;

    /**
     * This option relieves the user from writing regular expressions to parse
     * lines that they are not interested in. This also help to avoid parsing of
     * lines that are corrupted.
     */
    @Option(
            value = "-i Ignore lines that do not match any of the passed regular expressions")
    public static boolean ignoreNonMatchingLines = false;

    /**
     * This allows users to get away with sloppy\incorrect regular expressions
     * that might not fully cover the range of log lines appearing in the log
     * files.
     */
    @Option(
            value = "Ignore parser warnings and attempt to recover from parse errors if possible",
            aliases = { "-ignore-parse-errors" })
    public static boolean recoverFromParseErrors = false;

    /**
     * Output the fields extracted from each log line and terminate.
     */
    @Option(
            value = "Debug the parser by printing field values extracted from the log and then terminate.",
            aliases = { "-debugParse" })
    public static boolean debugParse = false;
    // end option group "Parser Options"

    // //////////////////////////////////////////////////
    /**
     * Command line arguments input filename to use.
     */
    @OptionGroup("Input Options")
    @Option(value = "-c Command line arguments input filename",
            aliases = { "-argsfile" })
    public static String argsFilename = null;
    // end option group "Input Options"

    // //////////////////////////////////////////////////
    /**
     * Specifies the prefix of where to store the final Synoptic representation
     * output. This prefix is also used to determine filenames of intermediary
     * files as well, like corresponding dot file and intermediate stage
     * representations and dot files (if specified, e.g. with
     * --dumpIntermediateStages).
     */
    @OptionGroup("Output Options")
    @Option(
            value = "-o Output path prefix for generating Graphviz dot files graphics",
            aliases = { "-output-prefix" })
    public static String outputPathPrefix = null;

    /**
     * Whether or not to output the list of invariants to a file, with one
     * invariant per line.
     */
    @Option(value = "Output invariants to a file")
    public static boolean outputInvariantsToFile = false;

    /**
     * Whether or not models should be exported as GML (graph modeling language)
     * files (the default format is DOT file format).
     */
    @Option(value = "Export models as GML and not DOT files",
            aliases = { "-export-as-gml" })
    public static boolean exportAsGML = false;

    /**
     * The absolute path to the dot command executable to use for outputting
     * graphical representations of Synoptic models
     */
    @Option(value = "-d Path to the Graphviz dot command executable to use",
            aliases = { "-dot-executable" })
    public static String dotExecutablePath = null;

    /**
     * This sets the output edge labels on graphs that are exported.
     */
    @Option(
            value = "Output edge labels on graphs to indicate transition probabilities",
            aliases = { "-outputEdgeLabels" })
    public static boolean outputEdgeLabels = true;

    /**
     * Whether or not the output graphs include the common TERMINAL state, to
     * which all final trace nodes have an edge.
     */
    @Option(value = "Show TERMINAL node in generated graphs.")
    public static boolean showTerminalNode = true;

    /**
     * Whether or not the output graphs include the common INITIAL state, which
     * has an edge to all the start trace nodes.
     */
    @Option(value = "Show INITIAL node in generated graphs.")
    public static boolean showInitialNode = true;

    // end option group "Output Options"

    // //////////////////////////////////////////////////
    /**
     * Dump the complete list of mined synoptic.invariants for the set of input
     * files to stdout. This option is <i>unpublicized</i>; it will not appear
     * in the default usage message
     */
    @OptionGroup(value = "Verbosity Options", unpublicized = true)
    @Option("Dump complete list of mined invariant to stdout")
    public static boolean dumpInvariants = false;

    /**
     * Dump the DOT representation of the initial graph to file. The file will
     * have the name <outputPathPrefix>.initial.dot, where 'outputPathPrefix' is
     * the filename of the final Synoptic output. This option is
     * <i>unpublicized</i>; it will not appear in the default usage message
     */
    @Option("Dump the DOT file for the initial graph to file <outputPathPrefix>.initial.dot")
    public static boolean dumpInitialGraphDotFile = true;

    /**
     * Dump PNG of graph to file. The file will have the name
     * <outputPathPrefix>.initial.dot.png, where 'outputPathPrefix' is the
     * filename of the final Synoptic output. This option is
     * <i>unpublicized</i>; it will not appear in the default usage message
     */
    @Option("Dump the PNG for the initial graph to file <outputPathPrefix>.initial.dot.png")
    public static boolean dumpInitialGraphPngFile = true;

    /**
     * Dump the dot representations for intermediate Synoptic steps to file.
     * Each of these files will have a name like:
     * outputPathPrefix.stage-S.round-R.dot where 'outputPathPrefix' is the
     * filename of the final Synoptic output, 'S' is the name of the stage (e.g.
     * r for refinement, and c for coarsening), and 'R' is the round number
     * within the stage. This option requires that the outputPathPrefix is set
     * with the -o option (see above). This option is <i>unpublicized</i>; it
     * will not appear in the default usage message
     */
    @Option("Dump dot files from intermediate Synoptic stages to files of form outputPathPrefix.stage-S.round-R.dot")
    public static boolean dumpIntermediateStages = false;
    // end option group "Verbosity Options"

    // //////////////////////////////////////////////////
    @OptionGroup(value = "Debugging Options", unpublicized = true)
    /**
     * Be extra verbose, print extra detailed information. Sets the log level to
     * FINEST.
     */
    @Option(value = "Print extra detailed information during execution")
    public static boolean logLvlExtraVerbose = false;

    /**
     * Used to select the algorithm for mining invariants.
     */
    @Option("Use the transitive closure invariant mining algorithm (usually slower)")
    public static boolean useTransitiveClosureMining = false;

    /**
     * Tell Synoptic to mine/not mine the NeverConcurrentWith invariant. When
     * false, this option changes mining behavior when
     * useTransitiveClosureMining = false (i.e., it only works for the DAG
     * walking invariant miner, not the TC-based miner).
     */
    @Option("Mine the NeverConcurrentWith invariant (only changes behavior for PO traces with useTransitiveClosureMining=false)")
    public static boolean mineNeverConcurrentWithInv = true;

    /**
     * Used to tell Synoptic to not go past mining invariants.
     */
    @Option("Mine invariants and then quit.")
    public static boolean onlyMineInvariants = false;

    /**
     * Do not perform the coarsening stage in Synoptic, and as final output use
     * the most refined representation. This option is <i>unpublicized</i>; it
     * will not appear in the default usage message
     */
    @Option("Do not perform the coarsening stage")
    public static boolean noCoarsening = false;

    /**
     * Perform benchmarking and output benchmark information. This option is
     * <i>unpublicized</i>; it will not appear in the default usage message
     */
    @Option("Perform benchmarking and output benchmark information")
    public static boolean doBenchmarking = false;

    /**
     * Intern commonly occurring strings, such as event types, as a memory-usage
     * optimization. This option is <i>unpublicized</i>; it will not appear in
     * the default usage message
     */
    @Option("Intern commonly occurring strings, such as event types, as a memory-usage optimization")
    public static boolean internCommonStrings = true;

    /**
     * Run all tests in synoptic.tests.units -- all the unit tests, and then
     * terminate. This option is <i>unpublicized</i>; it will not appear in the
     * default usage message
     */
    @Option("Run all tests in synoptic.tests.units, and then terminate.")
    public static boolean runTests = false;

    /**
     * Run all tests in synoptic.tests -- unit and integration tests, and then
     * terminate. This option is <i>unpublicized</i>; it will not appear in the
     * default usage message
     */
    @Option("Run all tests in synoptic.tests, and then terminate.")
    public static boolean runAllTests = false;

    /**
     * Turns on correctness checks that are disabled by default due to their
     * expensive cpu\memory usage profiles.
     */
    @Option("Perform extra correctness checks at the expense of cpu and memory usage.")
    public static boolean performExtraChecks = false;

    /**
     * Do not perform the refinement (and therefore do not perform coarsening)
     * and do not produce any representation as output. This is useful for just
     * printing the list of mined synoptic.invariants (using the option
     * 'dumpInvariants' above). This option is <i>unpublicized</i>; it will not
     * appear in the default usage message
     */
    @Option("Do not perform refinement")
    public static boolean noRefinement = false;
    // end option group "Debugging Options"

    /**
     * Input log files to run Synoptic on. These should appear without any
     * options as the final elements in the command line.
     */
    public static List<String> logFilenames = null;

    /**
     * Formatter to use for exporting graphs (DOT/GML formatter).
     */
    public static GraphExportFormatter graphExportFormatter = null;

    /** One line synopsis of usage */
    private static String usage_string = "synoptic [options] <logfiles-to-analyze>";

    /**
     * The synoptic.main method to perform the inference algorithm. See user
     * documentation for an explanation of the options.
     * 
     * @param args
     *            Command-line options
     */
    public static void main(String[] args) throws Exception {
        Main mainInstance = processArgs(args);
        if (mainInstance == null) {
            return;
        }

        Integer ret;
        try {
            ret = mainInstance.call();
        } catch (ParseException e) {
            throw e;
        } catch (Exception e) {
            throw InternalSynopticException.wrap(e);
        }

        logger.fine("Main.call() returned " + ret.toString());

    }

    /**
     * Parses the set of arguments (args) to the program, to set up static state
     * in Main. This state includes everything necessary to run Synoptic --
     * input log files, regular expressions, etc.
     * 
     * @param args
     *            Command line arguments that specify how Synoptic should
     *            behave.
     * @return
     * @throws IOException
     * @throws URISyntaxException
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     * @throws ParseException
     */
    public static Main processArgs(String[] args) throws IOException,
            URISyntaxException, IllegalArgumentException,
            IllegalAccessException, ParseException {
        // this directly sets the static member options of the Main class
        Options options = new Options(usage_string, Main.class);
        String[] cmdLineArgs = options.parse_or_usage(args);

        if (argsFilename != null) {
            // read program arguments from a file
            InputStream argsStream = new FileInputStream(argsFilename);
            ListedProperties props = new ListedProperties();
            props.load(argsStream);
            String[] cmdLineFileArgs = props.getCmdArgsLine();
            // the file-based args become the default args
            options.parse_or_usage(cmdLineFileArgs);
        }

        // Parse the command line args to override any of the above config file
        // args
        options.parse_or_usage(args);

        // The remainder of the command line is treated as a list of log
        // filenames to process
        logFilenames = new LinkedList<String>(Arrays.asList(cmdLineArgs));

        setUpLogging();

        // Display help for all option groups, including unpublicized ones
        if (allHelp) {
            System.out.println("Usage: " + usage_string);
            System.out
                    .println(options.usage("General Options",
                            "Execution Options", "Parser Options",
                            "Input Options", "Output Options",
                            "Verbosity Options", "Debugging Options"));
            return null;
        }

        // Display help just for the 'publicized' option groups
        if (help) {
            options.print_usage();
            return null;
        }

        if (version) {
            System.out.println("Synoptic version " + Main.versionString);
            return null;
        }

        // Setup the appropriate graph export formatter object.
        if (exportAsGML) {
            graphExportFormatter = new GmlExportFormatter();
        } else {
            graphExportFormatter = new DotExportFormatter();
        }

        if (runAllTests) {
            List<String> testClasses = getTestsInPackage("synoptic.tests.units.");
            testClasses
                    .addAll(getTestsInPackage("synoptic.tests.integration."));
            runTests(testClasses);
        } else if (runTests) {
            List<String> testClassesUnits = getTestsInPackage("synoptic.tests.units.");
            runTests(testClassesUnits);
        }

        // Remove any empty string filenames in the logFilenames list.
        while (logFilenames.contains("")) {
            logFilenames.remove("");
        }

        if (logFilenames.size() == 0 || logFilenames.get(0).equals("")) {
            logger.severe("No log filenames specified, exiting. Try cmd line option:\n\t"
                    + Main.getCmdLineOptDesc("help"));
            return null;
        }

        if (dumpIntermediateStages && outputPathPrefix == null) {
            logger.severe("Cannot dump intermediate stages without an output path prefix. Set this prefix with:\n\t"
                    + Main.getCmdLineOptDesc("outputPathPrefix"));
            return null;
        }

        Main mainInstance = new Main();

        if (logLvlVerbose || logLvlExtraVerbose) {
            mainInstance.printOptions();
        }

        if (randomSeed == null) {
            Main.randomSeed = System.currentTimeMillis();
        }
        Main.random = new Random(randomSeed);
        logger.info("Using random seed: " + randomSeed);

        return mainInstance;
    }

    /**
     * Returns a command line option description for an option name
     * 
     * @param optName
     *            The option variable name
     * @return a string description of the option
     * @throws InternalSynopticException
     *             if optName cannot be accessed
     */
    public static String getCmdLineOptDesc(String optName)
            throws InternalSynopticException {
        Field field;
        try {
            field = Main.class.getField(optName);
        } catch (SecurityException e) {
            throw InternalSynopticException.wrap(e);
        } catch (NoSuchFieldException e) {
            throw InternalSynopticException.wrap(e);
        }
        Option opt = field.getAnnotation(Option.class);
        String desc = opt.value();
        if (desc.length() > 0 && desc.charAt(0) != '-') {
            // For options that do not have a short option form,
            // include the long option trigger in the description.
            desc = "--" + optName + " " + desc;
        }
        return desc;
    }

    /**
     * Runs all the synoptic unit tests
     * 
     * @throws URISyntaxException
     *             if Main.class can't be located
     * @throws IOException
     */
    public static List<String> getTestsInPackage(String packageName)
            throws URISyntaxException, IOException {
        // If we are running from within a jar then jarName contains the path to
        // the jar
        // otherwise, it contains the path to where Main.class is located on the
        // filesystem
        String jarName = Main.class.getProtectionDomain().getCodeSource()
                .getLocation().toURI().getPath();
        System.out.println("Looking for tests in: " + jarName);

        // We assume that the tests we want to run are classes within
        // packageName, which can be found with the corresponding packagePath
        // filesystem offset
        String packagePath = packageName.replaceAll("\\.", File.separator);

        ArrayList<String> testClasses = new ArrayList<String>();

        JarInputStream jarFile = null;
        try {
            // Case1: running from within a jar
            // Open the jar file and locate the tests by their path
            jarFile = new JarInputStream(new FileInputStream(jarName));
            JarEntry jarEntry;
            while (true) {
                jarEntry = jarFile.getNextJarEntry();
                if (jarEntry == null) {
                    break;
                }
                String className = jarEntry.getName();
                if (className.startsWith(packagePath)
                        && className.endsWith(".class")) {
                    int endIndex = className.lastIndexOf(".class");
                    className = className.substring(0, endIndex);
                    testClasses.add(className.replaceAll("/", "\\."));
                }
            }
        } catch (java.io.FileNotFoundException e) {
            // Case2: not running from within a jar
            // Find the tests by walking through the directory structure
            File folder = new File(jarName + packagePath);
            File[] listOfFiles = folder.listFiles();
            for (int i = 0; i < listOfFiles.length; i++) {
                String className = listOfFiles[i].getName();
                if (listOfFiles[i].isFile() && className.endsWith(".class")) {
                    int endIndex = className.lastIndexOf(".class");
                    className = className.substring(0, endIndex);
                    testClasses.add(packageName + className);
                }
            }
        } catch (Exception e) {
            throw InternalSynopticException.wrap(e);
        } finally {
            if (jarFile != null) {
                jarFile.close();
            }
        }

        // Remove anonymous inner classes from the list, these look
        // 'TraceParserTests$1.class'
        ArrayList<String> anonClasses = new ArrayList<String>();
        for (String testClass : testClasses) {
            if (testClass.contains("$")) {
                anonClasses.add(testClass);
            }
        }
        testClasses.removeAll(anonClasses);

        return testClasses;
    }

    /**
     * Takes a list of paths that point to JUnit test classes and executes them
     * using JUnitCore runner.
     * 
     * @param testClasses
     */
    public static void runTests(List<String> testClasses) {
        System.out.println("Running tests: " + testClasses);
        String[] testClassesAr = new String[testClasses.size()];
        testClassesAr = testClasses.toArray(testClassesAr);
        JUnitCore.main(testClassesAr);
    }

    /**
     * Sets up and configures the Main.logger object based on command line
     * arguments
     */
    public static void setUpLogging() {
        // Get the top Logger instance
        logger = Logger.getLogger("");

        // Handler for console (reuse it if it already exists)
        Handler consoleHandler = null;

        // See if there is already a console handler
        for (Handler handler : logger.getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                consoleHandler = handler;
                break;
            }
        }

        if (consoleHandler == null) {
            // No console handler found, create a new one
            consoleHandler = new ConsoleHandler();
            logger.addHandler(consoleHandler);
        }

        // The consoleHandler will write out anything the logger gives it
        consoleHandler.setLevel(Level.ALL);

        // consoleHandler.setFormatter(new CustomFormatter());

        // Set the logger's log level based on command line arguments
        if (logLvlQuiet) {
            logger.setLevel(Level.WARNING);
        } else if (logLvlVerbose) {
            logger.setLevel(Level.FINE);
        } else if (logLvlExtraVerbose) {
            logger.setLevel(Level.FINEST);
        } else {
            logger.setLevel(Level.INFO);
        }

        consoleHandler.setFormatter(new BriefLogFormatter());
        return;
    }

    /**
     * Given a potentially wild-carded file path, finds all those which match.
     * TODO: make sure that the same file doesn't appear twice in the returned
     * list
     * 
     * @param fileArg
     *            The file path which may potentially contain wildcards.
     * @return An array of File handles which match.
     * @throws Exception
     */
    public static File[] getFiles(String fileArg) throws Exception {
        int wildix = fileArg.indexOf("*");
        if (wildix == -1) {
            return new File[] { new File(fileArg) };
        }
        String uptoWild = fileArg.substring(0, wildix);
        String path = FilenameUtils.getFullPath(uptoWild);
        String filter = FilenameUtils.getName(uptoWild)
                + fileArg.substring(wildix);
        File dir = new File(path).getAbsoluteFile();
        // TODO: check that listFiles is working properly recursively here.
        File[] results = dir.listFiles((FileFilter) new WildcardFileFilter(
                filter));
        if (results == null) {
            throw new Exception("Wildcard match failed: "
                    + (dir.isDirectory() ? dir.toString() + " not a directory"
                            : " for unknown reason"));
        }
        return results;
    }

    /**
     * Returns the filename for an intermediate dot file based on the given
     * stage name and round number. Adheres to the convention specified above in
     * usage, namely that the filename is of the format:
     * outputPathPrefix.stage-S.round-R.dot
     * 
     * @param stageName
     *            Stage name string, e.g. "r" for refinement
     * @param roundNum
     *            Round number within the stage
     * @return string filename for an intermediate dot file
     */
    public static String getIntermediateDumpFilename(String stageName,
            int roundNum) {
        return outputPathPrefix + ".stage-" + stageName + ".round-" + roundNum;
    }

    /**
     * Serializes g using a dot/gml format and optionally outputs a png file
     * corresponding to the serialized format (dot format export only).
     * 
     * @throws IOException
     */
    private static <T extends INode<T>> void exportGraph(String baseFilename,
            IGraph<T> g, boolean outputEdgeLabelsCondition,
            boolean imageGenCondition) {

        if (Main.outputPathPrefix == null) {
            logger.warning("Cannot output initial graph. Specify output path prefix using:\n\t"
                    + Main.getCmdLineOptDesc("outputPathPrefix"));
            return;
        }

        String filename = null;
        if (exportAsGML) {
            filename = baseFilename + ".gml";
        } else {
            filename = baseFilename + ".dot";
        }
        try {
            GraphExporter.exportGraph(filename, g, outputEdgeLabelsCondition);
        } catch (IOException e) {
            logger.fine("Unable to export graph to " + filename);
        }

        if (imageGenCondition) {
            // Currently we support only .dot -> .png generation
            GraphExporter.generatePngFileFromDotFile(filename);
        }
    }

    /**
     * Export g as an initial graph.
     */
    public static <T extends INode<T>> void exportInitialGraph(
            String baseFilename, IGraph<T> g) {
        // false below : never include edge labels on exported initial graphs

        // Main.dumpInitialGraphPngFile && !exportAsGML below : whether to
        // convert exported graph to a png file -- the user must have explicitly
        // requested this and the export must be in non-GML format (i.e., dot
        // format).
        exportGraph(baseFilename, g, false, Main.dumpInitialGraphPngFile
                && !exportAsGML);
    }

    /**
     * Export g as a non-initial graph.
     */
    public static <T extends INode<T>> void exportNonInitialGraph(
            String baseFilename, IGraph<T> g) {
        // Main.outputEdgeLabels below : the condition for including edge labels
        // on exported graphs.

        // !exportAsGML below : the condition for exporting an image to png file
        // is that it is not in GML format (i.e., it is in dot format so we can
        // use the 'dot' command).
        exportGraph(baseFilename, g, Main.outputEdgeLabels, !exportAsGML);
    }

    /***********************************************************/

    public Main() {
        //
    }

    /**
     * Prints the values of all the options for this instance of Main class
     * 
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     */
    public void printOptions() throws IllegalArgumentException,
            IllegalAccessException {
        StringBuffer optsString = new StringBuffer();
        optsString.append("Synoptic options:\n");
        for (Field field : this.getClass().getDeclaredFields()) {
            if (field.getAnnotation(Option.class) != null) {
                optsString.append("\t");
                optsString.append(field.getName());
                optsString.append(": ");
                if (field.get(this) != null) {
                    optsString.append(field.get(this).toString());
                    optsString.append("\n");
                } else {
                    optsString.append("null\n");
                }
            }
        }
        System.out.println(optsString.toString());
    }

    public static TraceParser newTraceParser(List<String> rExps,
            String partitioningRegExp, String sepRegExp) throws ParseException {
        TraceParser parser = new TraceParser();

        logger.fine("Setting up the log file parser.");
        if (partitioningRegExp == Main.partitionRegExpDefault) {
            logger.info("Using the default partitions mapping regex: "
                    + Main.partitionRegExpDefault);
        }

        if (!rExps.isEmpty()) {
            // The user provided custom regular expressions.
            for (String exp : rExps) {
                logger.fine("\taddRegex with exp:" + exp);
                parser.addRegex(exp);
            }

            parser.setPartitionsMap(partitioningRegExp);
        } else {
            // No custom regular expressions provided - warn and use defaults.
            logger.warning("Using a default regular expression to parse log-lines: "
                    + "will map the entire log line to an event type."
                    + "\nTo use a custom regular expressions use the option:\n\t"
                    + Main.getCmdLineOptDesc("regExps") + "\n\t");
            // TODO: is this next statement necessary?
            // parser.addRegex("^\\s*$(?<SEPCOUNT++>)");
            parser.addRegex("(?<TYPE>.*)");
            parser.setPartitionsMap(partitioningRegExp);
        }

        if (sepRegExp != null) {
            parser.addPartitionsSeparator(sepRegExp);
            if (!partitioningRegExp.equals(Main.partitionRegExpDefault)) {
                logger.warning("Partition separator and partition mapping regex are both specified. This may result in difficult to understand parsing behavior.");
            }
        }
        return parser;
    }

    public List<EventNode> parseFiles(TraceParser parser, List<String> filenames)
            throws Exception {
        List<EventNode> parsedEvents = new ArrayList<EventNode>();
        for (String fileArg : filenames) {
            logger.fine("\tprocessing fileArg: " + fileArg);
            File[] files = getFiles(fileArg);
            for (File file : files) {
                logger.fine("\tcalling parseTraceFile with file: "
                        + file.getAbsolutePath());
                parsedEvents.addAll(parser.parseTraceFile(file, -1));
            }
        }
        return parsedEvents;
    }

    private long loggerInfoStart(String msg) {
        logger.info(msg);
        return System.currentTimeMillis();
    }

    private void loggerInfoEnd(String msg, long startTime) {
        logger.info(msg + (System.currentTimeMillis() - startTime) + "ms");
    }

    private void processPOLog(TraceParser parser, List<EventNode> parsedEvents)
            throws ParseException, FileNotFoundException {
        // //////////////////
        long startTime = loggerInfoStart("Generating inter-event temporal relation...");
        DAGsTraceGraph inputGraph = parser
                .generateDirectPORelation(parsedEvents);
        loggerInfoEnd("Generating temporal relation took ", startTime);
        // //////////////////

        // Parser can be garbage-collected.
        parser = null;

        // TODO: vector time index sets aren't used yet.
        if (separateVTimeIndexSets != null) {
            // separateVTimeIndexSets is assumed to be in a format like:
            // "1,2;3;4,5,6" where the sets are {1,2}, {3}, {4,5,6}.
            LinkedList<LinkedHashSet<Integer>> indexSets = new LinkedList<LinkedHashSet<Integer>>();
            for (String strSet : separateVTimeIndexSets.split(";")) {
                LinkedHashSet<Integer> iSet = new LinkedHashSet<Integer>();
                indexSets.add(iSet);
                for (String index : strSet.split(",")) {
                    iSet.add(Integer.parseInt(index));
                }
            }
        }

        POInvariantMiner miner;
        if (useTransitiveClosureMining) {
            miner = new TransitiveClosureInvMiner();
        } else {
            miner = new DAGWalkingPOInvMiner(mineNeverConcurrentWithInv);
        }

        // //////////////////
        startTime = loggerInfoStart("Mining invariants ["
                + miner.getClass().getName() + "]..");
        TemporalInvariantSet minedInvs = miner.computeInvariants(inputGraph);
        loggerInfoEnd("Mining took ", startTime);
        // //////////////////

        // Miner can be garbage-collected.
        miner = null;

        logger.info("Mined " + minedInvs.numInvariants() + " invariants");

        int totalNCwith = 0;
        for (ITemporalInvariant inv : minedInvs.getSet()) {
            if (inv instanceof NeverConcurrentInvariant) {
                totalNCwith++;
            }
        }
        logger.info("\tMined " + totalNCwith
                + " NeverConcurrentWith invariants");

        if (dumpInvariants) {
            logger.info("Mined invariants: " + minedInvs);
        }

        if (outputInvariantsToFile) {
            String invariantsFilename = outputPathPrefix + ".invariants.txt";
            logger.info("Outputting invarians to file: " + invariantsFilename);
            minedInvs.outputToFile(invariantsFilename);
        }
    }

    /**
     * The top-level method that uses TraceParser to parse the input files, and
     * calls the primary Synoptic functions to perform refinement\coarsening and
     * then to output the final graph to the output file (specified as a command
     * line option).
     */
    @Override
    public Integer call() throws Exception {
        PartitionGraph pGraph = createInitialPartitionGraph();
        if (pGraph != null) {
            runSynoptic(pGraph);
        }
        return Integer.valueOf(0);
    }

    /**
     * Uses the values of static variables in Main to (1) read and parse the
     * input log files, (2) to mine invariants from the parsed files, and (3)
     * construct an initial partition graph model of the parsed files.
     * 
     * @return The initial partition graph built from the parsed files or null.
     *         Returns null when the arguments passed to Main require an early
     *         termination.
     * @throws Exception
     */
    public PartitionGraph createInitialPartitionGraph() throws Exception {
        Locale.setDefault(Locale.US);
        TraceParser parser = newTraceParser(Main.regExps, Main.partitionRegExp,
                Main.separatorRegExp);
        long startTime;

        // //////////////////
        // Parses all the log filenames, constructing the parsedEvents List.
        startTime = loggerInfoStart("Parsing input files..");
        List<EventNode> parsedEvents;
        try {
            parsedEvents = parseFiles(parser, Main.logFilenames);
        } catch (ParseException e) {
            logger.severe("Caught ParseException -- unable to continue, exiting. Try cmd line option:\n\t"
                    + Main.getCmdLineOptDesc("help"));
            logger.severe(e.toString());
            return null;
        }
        loggerInfoEnd("Parsing took ", startTime);
        // //////////////////

        if (Main.debugParse) {
            // Terminate since the user is interested in debugging the parser.
            logger.info("Terminating. To continue further, re-run without the debugParse option.");
            return null;
        }

        // PO Logs are processed separately.
        if (!parser.logTimeTypeIsTotallyOrdered()) {
            logger.warning("Partially ordered log input detected. Only mining invariants since refinement/coarsening is not yet supported.");
            processPOLog(parser, parsedEvents);
            return null;
        }

        // //////////////////
        startTime = loggerInfoStart("Generating inter-event temporal relation...");
        ChainsTraceGraph inputGraph = parser
                .generateDirectTORelation(parsedEvents);
        loggerInfoEnd("Generating temporal relation took ", startTime);
        // //////////////////

        if (dumpInitialGraphDotFile) {
            logger.info("Exporting initial graph ["
                    + inputGraph.getNodes().size() + " nodes]..");
            exportInitialGraph(Main.outputPathPrefix + ".initial", inputGraph);
        }

        TOInvariantMiner miner;
        if (useTransitiveClosureMining) {
            miner = new TransitiveClosureInvMiner();
        } else {
            miner = new ChainWalkingTOInvMiner();
        }

        // Parser can be garbage-collected.
        parser = null;

        // //////////////////
        startTime = loggerInfoStart("Mining invariants ["
                + miner.getClass().getName() + "]..");
        TemporalInvariantSet minedInvs = miner.computeInvariants(inputGraph);
        loggerInfoEnd("Mining took ", startTime);
        // //////////////////

        // Miner can be garbage-collected.
        miner = null;

        logger.info("Mined " + minedInvs.numInvariants() + " invariants");

        if (dumpInvariants) {
            logger.info("Mined invariants: " + minedInvs);
        }

        if (outputInvariantsToFile) {
            String invariantsFilename = outputPathPrefix + ".invariants.txt";
            logger.info("Outputting invarians to file: " + invariantsFilename);
            minedInvs.outputToFile(invariantsFilename);
        }

        if (onlyMineInvariants) {
            return null;
        }

        // //////////////////
        // Create the initial partitioning graph.
        startTime = loggerInfoStart("Creating initial partition graph.");
        PartitionGraph pGraph = new PartitionGraph(inputGraph, true, minedInvs);
        loggerInfoEnd("Creating partition graph took ", startTime);
        // //////////////////

        return pGraph;
    }

    /**
     * Runs the Synoptic algorithm starting from the initial graph (pGraph). The
     * pGraph is assumed to be fully initialized and ready for refinement. The
     * Synoptic algorithm first runs a refinement algorithm, and then runs a
     * coarsening algorithm.
     * 
     * @param pGraph
     *            The initial graph model to start refining.
     */
    public void runSynoptic(PartitionGraph pGraph) {
        long startTime;

        if (logLvlVerbose || logLvlExtraVerbose) {
            System.out.println("");
            System.out.println("");
        }

        // //////////////////
        startTime = loggerInfoStart("Refining (Splitting)...");
        Bisimulation.splitPartitions(pGraph);
        loggerInfoEnd("Splitting took ", startTime);
        // //////////////////

        if (logLvlVerbose || logLvlExtraVerbose) {
            System.out.println("");
            System.out.println("");
        }

        // //////////////////
        startTime = loggerInfoStart("Coarsening (Merging)..");
        Bisimulation.mergePartitions(pGraph);
        loggerInfoEnd("Merging took ", startTime);
        // //////////////////

        // At this point, we have the final model in the pGraph object.

        // TODO: check that none of the initially mined synoptic.invariants are
        // unsatisfied in the result

        // export the resulting graph
        if (Main.outputPathPrefix != null) {
            logger.info("Exporting final graph [" + pGraph.getNodes().size()
                    + " nodes]..");
            startTime = System.currentTimeMillis();

            exportNonInitialGraph(Main.outputPathPrefix, pGraph);

            logger.info("Exporting took "
                    + (System.currentTimeMillis() - startTime) + "ms");
        } else {
            logger.warning("Cannot output final graph. Specify output path prefix using:\n\t"
                    + Main.getCmdLineOptDesc("outputPathPrefix"));
        }
    }
}
