package synoptic.tests;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.runner.JUnitCore;

import synoptic.main.AbstractMain;
import synoptic.main.SynopticMain;
import synoptic.main.options.SynopticOptions;
import synoptic.main.parser.ParseException;
import synoptic.model.export.DotExportFormatter;
import synoptic.model.interfaces.IGraph;
import synoptic.model.interfaces.INode;

/**
 * Base test class for all projects that depend on Synoptic. Performs common
 * set-up and tear-down tasks expected by the Synoptic library, and defines a
 * few common useful methods, like exporting graphs.
 * 
 * <pre>
 * Requires JUnit 4.7 or higher.
 * </pre>
 */
public abstract class SynopticLibTest {
    /**
     * Can be used to find out the current test name (as of JUnit 4.7) via
     * name.getMethodName().
     **/
    @Rule
    public TestName testName = new TestName();

    /**
     * The logger instance to use across all tests.
     */
    protected static Logger logger;

    /**
     * Initializes the static fields of the test. We don't use a static block
     * because static initialization is parameterized.
     * 
     * @param loggerName
     *            The Logger.getLogger() name to use.
     */
    protected static void initialize(String loggerName) {
        logger = Logger.getLogger(loggerName);
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
     * Whether or not fname exists on disk.
     * 
     * @param fname
     * @return
     */
    public static boolean fileExists(String fname) {
        File f = new File(fname);
        return f.exists();
    }

    /**
     * For each prefix in prefixes, returns the first prefix for which
     * prefix+fname exists on disk.
     * 
     * @param prefixes
     * @param fname
     * @return
     */
    public static String findWorkingPath(List<String> prefixes, String fname) {
        for (String prefix : prefixes) {
            if (fileExists(prefix + fname)) {
                return prefix;
            }
        }
        Assert.fail("Unable to find a working path with prefixes "
                + prefixes.toString() + " and file " + fname);
        return null;
    }

    /**
     * Sets up the Synoptic state that is necessary for running tests that
     * depend on the Synoptic library.
     * 
     * @throws ParseException
     */
    @Before
    public void setUp() throws ParseException {
        if (AbstractMain.instance == null) {
            SynopticMain synopticMain = new SynopticMain(new SynopticOptions(),
                    new DotExportFormatter());
        }
    }

    // //////////////////////////////////////////////
    // Common routines to simplify testing.
    // //////////////////////////////////////////////

    /**
     * Exports a graph to a png file. The index parameter is used to
     * differentiate graphs that are generated by the same test. The reason we
     * require it explicitly is so that it is easier to reason about which graph
     * was generated by which call to exportTestGraph(). Otherwise, we could
     * just as well maintain the index automatically per test.
     * 
     * @param g
     *            Graph to export
     * @param index
     *            Index identifier of this graph, in the context of the test
     *            using the method.
     */
    protected <T extends INode<T>> void exportTestGraph(IGraph<T> g, int index) {
        exportTestGraph(g, Integer.toString(index));
    }

    /**
     * Exports a graph to a png file with a title that will help to uniquely
     * identify the file.
     * 
     * @param g
     *            Graph to export
     */
    protected <T extends INode<T>> void exportTestGraph(IGraph<T> g,
            String title) {
        // Only export test graphs we were told to be verbose.
        AbstractMain main = AbstractMain.getInstanceWithExistenceCheck();
        if (main.options.logLvlVerbose && !main.options.logLvlExtraVerbose) {
            return;
        }
        String path = "test-output" + File.separator + testName.getMethodName()
                + title + ".dot";

        main.exportTraceGraph(path, g);
    }
}
