package synoptic.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import synoptic.util.InternalSynopticException;

/**
 * Represents a connected subgraph over a relation through a trace. Supports
 * operations to count event Occurrences, Follows, and Precedes over the
 * specified relation within the trace.
 * 
 * @author timjv
 */
public class RelationPath {

    /*
     * The representation for a relation path is the first non initial node in a
     * trace that is part of the connected subgraph of the relation. Using the
     * second node in the path, the rest of the nodes can be implicitly accessed
     * through a combination of relation and transitive relation edges.
     * 
     * We can get away with not having the initial node in the relation path
     * because of a dependency on how counting is done for the initial node. The
     * initial node appears once in each trace and is never preceded by any
     * event. The followedBy counts for an initial node is equivalent to the
     * event types seen in the trace.
     */

    /** First non-INITIAL node in this relation path */
    private EventNode eNode;
    /** Final non-TERMINAL node in this relation path */
    private EventNode eFinal;
    /** Relation this path is over */
    private String relation;
    /** Relation this path uses for transitivity, usually "t" */
    private String transitiveRelation;
    /**
     * Keeps track of the state of seen, eventcounts, followedByCounts, and
     * precedesCounts. True if these data structures are populated.
     */
    private boolean counted;
    
    /** Whether or not initial has an outgoing relation edge */
    private boolean initialConnected;

    /** The set of nodes seen prior to some point in the trace. */
    private Set<EventType> seen;
    /** Maintains the current event count in the path. */
    private Map<EventType, Integer> eventCounts;
    /**
     * Maintains the current FollowedBy count for the path.
     * followedByCounts[a][b] = count iff the number of a's that appeared before
     * this b is count.
     */
    private Map<EventType, Map<EventType, Integer>> followedByCounts;
    /**
     * Maintains the current precedes count for the path. precedesCounts[a][b] =
     * count iff the number of b's that appeared after this a is count.
     */
    private Map<EventType, Map<EventType, Integer>> precedesCounts;

    public RelationPath(EventNode eNode, String relation,
            String transitiveRelation, boolean initialConnected) {
        this.eNode = eNode;
        this.relation = relation;
        this.transitiveRelation = transitiveRelation;
        this.counted = false;
        this.seen = new LinkedHashSet<EventType>();
        this.eventCounts = new LinkedHashMap<EventType, Integer>();
        this.followedByCounts = new LinkedHashMap<EventType, Map<EventType, Integer>>();
        this.precedesCounts = new LinkedHashMap<EventType, Map<EventType, Integer>>();
        this.initialConnected = initialConnected;
    }

    /**
     * Assumes tracegraph is already constructed Walks over the tracegraph that
     * eNode is part of to populate seen, eventcounts, followedByCounts, and
     * precedesCounts throws an error if a node has multiple transitions for a
     * single relation
     */
    public void count() {
        EventNode curNode = eNode;
        
        boolean hasNonTransitiveIncomingRelation = initialConnected;
        List<Transition<EventNode>> transitions = curNode.getTransitions(relation);

        if (transitions.isEmpty()) {
            transitions = curNode.getTransitions(transitiveRelation);
        }

        while (!transitions.isEmpty()) {

            /*
             * For now let's use the defaultTimeRelationString, this will need
             * to be changed if the traceGraph specification is parameterized to
             * not have time relations.
             * 
             * Maybe this should be changed to use the transitiveRelation field,
             * however as of now whether or not the transitive relation is
             * totally ordered is undefined.
             */
            if (curNode.getTransitions(Event.defaultTimeRelationString).size() != 1) {
                throw new InternalSynopticException(
                        "SpecializedInvariantMiner does not work on partially ordered traces.");
            }
            
            boolean hasNonTransitiveOutgoingRelation = curNode.getTransitions(relation).size() == 1;
            
            if (!(hasNonTransitiveOutgoingRelation || hasNonTransitiveIncomingRelation)) {
                // Move on to the next node in the trace.
                List<Transition<EventNode>> searchTransitions = curNode
                        .getTransitions(relation);

                if (searchTransitions.isEmpty()) {
                    searchTransitions = curNode.getTransitions(transitiveRelation);
                }

                if (curNode.equals(eFinal)) {
                    break;
                }

                curNode = searchTransitions.get(0).getTarget();

                transitions = curNode.getTransitions(relation);

                if (transitions.isEmpty()) {
                    transitions = curNode.getTransitions(transitiveRelation);
                }
                continue;
            }
            
            hasNonTransitiveIncomingRelation = hasNonTransitiveOutgoingRelation;
              
            // The current event is 'b', and all prior events are 'a' --
            // this notation indicates that an 'a' always occur prior to a
            // 'b' in the path.
            EventType b = curNode.getEType();

            // Update the precedes counts based on the a events that
            // preceded the current b event in this path.
            for (EventType a : seen) {
                Map<EventType, Integer> bValues;
                if (!precedesCounts.containsKey(a)) {
                    precedesCounts.put(a,
                            new LinkedHashMap<EventType, Integer>());
                }

                bValues = precedesCounts.get(a);

                if (!bValues.containsKey(b)) {
                    bValues.put(b, 0);
                }

                bValues.put(b, bValues.get(b) + 1);

            }

            // Update the followed by counts for this path: the number of a
            // FollowedBy b at this point in this trace is exactly the
            // number of a's that we've seen so far.
            for (EventType a : seen) {
                Map<EventType, Integer> bValues;

                if (!followedByCounts.containsKey(a)) {
                    followedByCounts.put(a,
                            new LinkedHashMap<EventType, Integer>());
                }

                bValues = followedByCounts.get(a);

                bValues.put(b, eventCounts.get(a));
            }
            seen.add(b);

            // Update the trace event counts.
            if (!eventCounts.containsKey(b)) {
                eventCounts.put(b, 1);
            } else {
                eventCounts.put(b, eventCounts.get(b) + 1);
            }

            // Move on to the next node in the trace.
            List<Transition<EventNode>> searchTransitions = curNode
                    .getTransitions(relation);

            if (searchTransitions.isEmpty()) {
                searchTransitions = curNode.getTransitions(transitiveRelation);
            }

            if (curNode.equals(eFinal)) {
                break;
            }

            curNode = searchTransitions.get(0).getTarget();

            transitions = curNode.getTransitions(relation);

            if (transitions.isEmpty()) {
                transitions = curNode.getTransitions(transitiveRelation);
            }
        }

        counted = true;
    }

    public Set<EventType> getSeen() {
        if (!counted) {
            count();
        }
        return Collections.unmodifiableSet(seen);
    }

    public Map<EventType, Integer> getEventCounts() {
        if (!counted) {
            count();
        }
        return Collections.unmodifiableMap(eventCounts);
    }

    /**
     * Map<a, Map<b, count>> iff the number of a's that appeared before this b
     * is count.
     */
    public Map<EventType, Map<EventType, Integer>> getFollowedByCounts() {
        if (!counted) {
            count();
        }
        // TODO: Make the return type deeply unmodifiable
        return Collections.unmodifiableMap(followedByCounts);
    }

    /**
     * Map<a, Map<b, count>> iff the number of b's that appeared after this a is
     * count.
     */
    public Map<EventType, Map<EventType, Integer>> getPrecedesCounts() {
        if (!counted) {
            count();
        }
        // TODO: Make the return type deeply unmodifiable
        return Collections.unmodifiableMap(precedesCounts);
    }

    public String getRelation() {
        return relation;
    }

    public void setFinalNode(EventNode eNode2) {
        this.eFinal = eNode2;
    }
}
