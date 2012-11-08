package dynoptic.model.fifosys.gfsm;

import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import dynoptic.main.DynopticMain;
import dynoptic.model.fifosys.gfsm.observed.fifosys.ObsFifoSysState;
import dynoptic.util.Util;

import synoptic.model.event.DistEventType;

/**
 * Represents a path in the GFSM. This is used to store GFSM paths that
 * correspond to an event-based counter-example that is returned by the McScM
 * model checker. Note that this correspondence is not exact -- a GFSMPath
 * represents the events sub-sequence for a particular process.
 */
public class GFSMPath {
    protected static Logger logger = Logger.getLogger("GFSMCExample");

    // Path of states in the GFSM that this path corresponds to.
    protected final List<GFSMState> states;

    // Sequence of events that connect partitions in the path.
    private final List<DistEventType> events;

    // The process that this GFSMPath corresponds to -- used during
    // counter-example conversion and refinement.
    private final int pid;

    // /////////////////////////////////////////////////////////////

    /**
     * Internal constructor for a complete path: length |path|, with |path| - 1
     * inter-connecting events.
     */
    private GFSMPath(List<GFSMState> states, List<DistEventType> events, int pid) {
        assert states != null;
        assert events != null;

        this.pid = pid;
        // Make defensive copies of path/events lists.
        this.states = Util.newList(states);
        this.events = Util.newList(events);
    }

    /** Checks the path for completeness/consistency. */
    static private void checkPathCompleteness(GFSMPath p) {
        assert p.states != null;
        assert p.events != null;

        // Empty path is a complete path.
        if (p.states.isEmpty()) {
            assert p.events.isEmpty();
            return;
        }

        // A complete path must start/end with a state.
        assert p.states.size() == (p.events.size() + 1);

        // A state must contain at least one observation that emits the
        // appropriate event, and this event must point to the appropriate next
        // state.
        for (int sid = 0; sid < p.states.size() - 2; sid++) {
            GFSMState s = p.states.get(sid);
            DistEventType e = p.events.get(sid);
            GFSMState sNext = p.states.get(sid + 1);

            assert s.getTransitioningEvents().contains(e);
            assert s.getNextStates(e).contains(sNext);
        }
    }

    // /////////////////////////////////////////////////////////////

    /** New empty path. */
    public GFSMPath(int pid) {
        this(Util.<GFSMState> newList(), Util.<DistEventType> newList(), pid);
    }

    /** New path with a single initial state. */
    public GFSMPath(GFSMState s, int pid) {
        this(pid);
        this.states.add(s);
    }

    /** New path based off of an existing path. */
    public GFSMPath(GFSMPath path) {
        this(path.states, path.events, path.pid);
    }

    /**
     * New path that is the stitching of pre-existing (complete path) prefix and
     * suffix paths.
     */
    public GFSMPath(GFSMPath prefix, GFSMPath suffix) {
        this(prefix);
        checkPathCompleteness(prefix);

        assert prefix.pid == suffix.pid;

        // Prefix must begin and end on a state.
        assert prefix.states.size() == (prefix.events.size() + 1);
        // Suffix must begin on an event and end on a state.
        assert suffix.states.size() == (suffix.events.size());

        this.states.addAll(suffix.states);
        this.events.addAll(suffix.events);

        // Check that the resulting stitched path is valid.
        checkPathCompleteness(this);
    }

    /*
     * private List<GFSMState> getStates() { return states; }
     * 
     * private List<DistEventType> getEvents() { return events; }
     * 
     * public int getPid() { return pid; }
     */
    // /////////////////////////////////////////////////////////////

    /** Adds a state and event to the _front_ of a path. */
    public void prefixEventAndState(DistEventType e, GFSMState s) {
        states.add(0, s);
        events.add(0, e);
    }

    public GFSMState lastState() {
        assert !this.states.isEmpty();
        return this.states.get(this.states.size() - 1);
    }

    /**
     * Returns a string representing the path, using states list of partitions
     * and events list of events to connect the partitions.
     */
    @Override
    public String toString() {
        // TODO: this does not handle incomplete paths.

        String ret = "GFSMPath : ";
        int i = 0;
        for (GFSMState p : states) {
            ret += p.toString();
            if (i != states.size() - 1) {
                ret += "-- " + events.get(i).toString() + " --> ";
            }
            i += 1;
        }
        return ret;
    }

    /**
     * To resolve a partial counter-example in a GFSM (regardless of the
     * corresponding invariant type) we find the partition that stitches
     * together observations and is farthest from the end of the path and refine
     * this partition. Returns true if a stitching partition was found and
     * refined, and false if not such partition was found.
     * 
     * @param pGraph
     */
    public boolean refine(GFSM pGraph) {
        checkPathCompleteness(this);

        if (states.size() == 1) {
            // No transitions observed along this path, so we cannot refine it.
            return false;
        }

        int maxStitchPartIndex = findMaxStitchPartIndex(0);
        if (maxStitchPartIndex == -1) {
            return false;
        }

        // Now refine the partition at maxStitchPartIndex.
        refinePartition(pGraph, maxStitchPartIndex);
        return true;
    }

    /**
     * Refine partition at partIndex in the path of partition along the path.
     * The goal is to isolate setLeft and setRight of observations in this
     * partition. These two sets are constructed so that after the partitioning
     * this counter-example path is eliminated. Observations that are not
     * pertinent to this refinement in the partition are assigned at random to
     * setLeft and setRight.
     * 
     * @param pGraph
     * @param partIndex
     */
    private void refinePartition(GFSM pGraph, int partIndex) {
        GFSMState part = states.get(partIndex);

        logger.info("Refining partition: " + part + ", at index " + partIndex);

        // We can't refine a partition if it contains just a single observation.
        //
        // NOTE: the code that determines the partition to refine cannot return
        // a partition with a single concrete state as stitching, since the
        // abstract counter-example can be extended in the concrete event-space
        // by at least by one more event.
        assert part.getObservedStates().size() > 1;

        // Construct setRight.
        Set<ObsFifoSysState> setRight;

        if (partIndex == (states.size() - 1)) {
            // Part is the last (terminal) partition in path, so we want to
            // isolate the observations that allow the counter-example path
            // to terminate at this partition from events that have
            // transitioned the path into this partition.
            setRight = part.getTerminalObsForPid(pid);
        } else {
            // Construct setRight to contain observations that transition
            // from part to partNext in the counter-example path.
            setRight = Util.newSet();
            DistEventType eNext = events.get(partIndex);
            GFSMState partNext = states.get(partIndex + 1);
            for (ObsFifoSysState s : part
                    .getObservedStatesWithTransition(eNext)) {
                if (s.getNextState(eNext).getParent() == partNext) {
                    setRight.add(s);
                }
            }

            // setRight might not be differentiating enough. So, we might have
            // to instead construct setRight to contain observations whose
            // predecessors where in the partPrev partition, which preceded
            // partNext.
            if (setRight.size() == part.getObservedStates().size()) {
                // Cannot be the initial partition -- otherwise no stitching
                // exists at this partition.
                assert partIndex > 0;

                setRight.clear();
                GFSMState partPrev = states.get(partIndex - 1);
                DistEventType ePrev = events.get(partIndex - 1);

                for (ObsFifoSysState sPrev : partPrev
                        .getObservedStatesWithTransition(ePrev)) {
                    ObsFifoSysState s = sPrev.getNextState(ePrev);
                    if (s.getParent() == part) {
                        setRight.add(s);
                    }
                }
            }

        }
        assert !setRight.isEmpty();
        assert setRight.size() < part.getObservedStates().size();

        // Construct setLeft.
        Set<ObsFifoSysState> setLeft;

        // ///////////////
        // For simplicity, we construct setLeft to be the complement of
        // setRight.
        setLeft = Util.newSet();
        for (ObsFifoSysState s : part.getObservedStates()) {
            if (!setRight.contains(s)) {
                setLeft.add(s);
            }
        }

        // ///////////////
        // TODO: more advanced setLeft construction, which does not work when
        // there are self-loops along the counter-example path.
        //
        // if (partIndex == 0) {
        // // Part is the first (initial) partition in path, so we want to
        // // isolate the initial observations in this partition from those
        // // that generate the counter-example path.
        // setLeft = part.getInitialObservations();
        // } else {
        // // As above in determining setRight, except we head to the left
        // // and build a set of observations in part that can be reached
        // // from the previous partition along the counter-example path.
        // setLeft = new LinkedHashSet<ObsFifoSysState>();
        //
        // DistEventType ePrev = mcCExample.getEvents().get(partIndex - 1);
        // GFSMState partPrev = path.get(partIndex - 1);
        // for (ObsFifoSysState s : partPrev
        // .getObservedStatesWithTransition(ePrev)) {
        // if (s.getNextState(ePrev).getParent() == part) {
        // setLeft.add(s.getNextState(ePrev));
        // }
        // }
        // }
        assert !setLeft.isEmpty();

        if (DynopticMain.assertsOn) {
            // Make sure that the two sets are disjoint.
            for (ObsFifoSysState s : setLeft) {
                if (setRight.contains(s)) {
                    assert !setRight.contains(s);
                }
            }
            for (ObsFifoSysState s : setRight) {
                assert !setLeft.contains(s);
            }
        }

        pGraph.refineWithRandNonRelevantObsAssignment(part, setLeft, setRight);
    }

    // /////////////////////////////////////////////////////////////

    /**
     * Takes the observed state traces that begin in partition at index
     * xPartSrcIndex (and emits event events[xPartSrcIndex]), and follows these
     * along the path until we cannot follow it any further. We then return the
     * maximum stitch partition index, which is the max over all these observed
     * state traces we've followed. If a stitching does _not_ exist, then this
     * return -1.
     * 
     * @param xPartSrcIndex
     *            The partition we start tracing from
     */
    private int findMaxStitchPartIndex(int xPartSrcIndex) {
        GFSMState xPartSrc = states.get(xPartSrcIndex);
        DistEventType x = events.get(xPartSrcIndex);

        // The set of observations in xPartSrc that emit x.
        Set<ObsFifoSysState> xObsSources = xPartSrc
                .getObservedStatesWithTransition(x);

        // There must be _some_ concrete state that has the abstract transition.
        assert !xObsSources.isEmpty();

        // Track forward each observation that emits x from xPartSrcIndex
        // partition along the path and identify the partition
        // where the observation was 'stitched' onto another observation. The
        // partition farthest along the path is the one we return.
        int maxStitchPartIndex = -1;
        for (ObsFifoSysState xObs : xObsSources) {
            int sPartIndex = xPartSrcIndex;
            ObsFifoSysState s = xObs;

            // Track the observed path containing the observation xObs at the
            // start through the counter-example path.
            while (true) {
                if (sPartIndex == (states.size() - 1)) {
                    // Reached the end of the path. This is a valid end to
                    // a path if s is a terminal observed state for pid.
                    if (s.isAcceptForPid(pid)) {
                        // Reset sPartIndex to indicate that this path is valid.
                        sPartIndex = -1;
                    }
                    break;
                }

                GFSMState sPartNext = states.get(sPartIndex + 1);
                DistEventType e = events.get(sPartIndex);
                s = s.getNextState(e);
                if (s == null || s.getParent() != sPartNext) {
                    // If the next observation does not have the right event
                    // (the one required by the counter-example path), or if it
                    // has the right event but the parent of the next
                    // observation is not along the counter-example path, then
                    // stop.
                    break;
                }
                sPartIndex += 1;
            }

            // If we broke out above because of a stitching or because we
            // terminated in a state that we shouldn't have terminated in, then
            // potentially update the maxStitchPartIndex.
            if (sPartIndex != -1) {
                if (sPartIndex > maxStitchPartIndex) {
                    maxStitchPartIndex = sPartIndex;
                }
            }
        }
        return maxStitchPartIndex;
    }

}
