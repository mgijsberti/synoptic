package synoptic.invariants.fsmcheck;

import synoptic.invariants.BinaryInvariant;
import synoptic.model.event.EventType;
import synoptic.model.interfaces.INode;

/**
 * Represents a set of "A never followed by B" synoptic.invariants to simulate,
 * recording the shortest historical path to reach a particular state. This
 * finite state machine enters a particular state (s2) when A is provided. If we
 * are in this state when a B is provided, then we enter into a failure state.
 * 
 * @author Michael Sloan (mgsloan@gmail.com)
 * @see NFbyInvFsms
 * @see FsmStateSet
 */
public class NFbyTracingSet<T extends INode<T>> extends TracingStateSet<T> {
    HistoryNode aNotSeen; // A not seen
    HistoryNode aSeen; // A seen (and no Bs yet after it)
    HistoryNode bSeenAfter; // A seen, followed by B -- failure state

    EventType a, b;

    public NFbyTracingSet(EventType a, EventType b) {
        this.a = a;
        this.b = b;
    }

    public NFbyTracingSet(BinaryInvariant inv) {
        this(inv.getFirst(), inv.getSecond());
    }

    @Override
    public void setInitialEventTest(T x, HistoryNode newHistory) {
        EventType name = x.getEType();
        if (a.equals(name)) {
            aSeen = newHistory;
        } else {
            aNotSeen = newHistory;
        }
    }

    @Override
    public HistoryNode setInitialHistoryReset(T x) {
        aNotSeen = aSeen = bSeenAfter = null; 
        return new HistoryNode(x, null, 1);
    }
    
    @Override
    public void transitionEventTest(T x) {
        EventType name = x.getEType();

        // bSeenAfter is set if aSeen is set or bSeenAfter is already set. 
        if (b.equals(name)) {
            bSeenAfter = yieldShorter(aSeen, bSeenAfter);
            aSeen = null;
        }
        /*
         * aSeen can be set unless bSeenAfter is set.
         * 
         * NOTE: there is no else here, because for this invariant, isA and isB
         * can be simultaneously true (A NFby A, eg, A is singleton).
         */
        if (a.equals(name)) {
            aSeen = yieldShorter(aNotSeen, aSeen);
            aNotSeen = null;
        }
    }

    @Override
    public void transitionHistoryExtend(T x) {
        // Advance history for all states.
        aNotSeen = extendIfNonNull(x, aNotSeen);
        aSeen = extendIfNonNull(x, aSeen);
        bSeenAfter = extendIfNonNull(x, bSeenAfter);
    }

    @Override
    public HistoryNode failpath() {
        return bSeenAfter;
    }

    @Override
    public NFbyTracingSet<T> copy() {
        NFbyTracingSet<T> result = new NFbyTracingSet<T>(a, b);
        result.aNotSeen = aNotSeen;
        result.aSeen = aSeen;
        result.bSeenAfter = bSeenAfter;
        return result;
    }

    @Override
    public void mergeWith(TracingStateSet<T> other) {
        NFbyTracingSet<T> casted = (NFbyTracingSet<T>) other;
        aNotSeen = yieldShorter(aNotSeen, casted.aNotSeen);
        aSeen = yieldShorter(aSeen, casted.aSeen);
        bSeenAfter = yieldShorter(bSeenAfter, casted.bSeenAfter);
    }

    @Override
    public boolean isSubset(TracingStateSet<T> other) {
        NFbyTracingSet<T> casted = (NFbyTracingSet<T>) other;
        if (casted.aNotSeen == null) {
            if (aNotSeen != null) {
                return false;
            }
        }
        if (casted.aSeen == null) {
            if (aSeen != null) {
                return false;
            }
        }
        if (casted.bSeenAfter == null) {
            if (bSeenAfter != null) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("NFby: ");
        appendWNull(result, bSeenAfter); // Failure case first.
        result.append(" | ");
        appendWNull(result, aSeen);
        result.append(" | ");
        appendWNull(result, aNotSeen);
        return result.toString();
    }

}
