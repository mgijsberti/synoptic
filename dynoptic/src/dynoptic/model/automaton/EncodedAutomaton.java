package dynoptic.model.automaton;

import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.MinimizationOperations;
import dk.brics.automaton.State;
import dk.brics.automaton.Transition;
import dynoptic.model.AbsFSM;
import dynoptic.model.AbsFSMState;
import dynoptic.util.Util;

import synoptic.model.event.IDistEventType;

/**
 * Wrapper class for dk.brics.automaton.Automaton which provides character
 * encodings for building Automaton with EventTypes rather than characters.
 */
public class EncodedAutomaton<T extends AbsFSMState<T, TxnEType>, TxnEType extends IDistEventType> {

    public static Logger logger;
    static {
        logger = Logger.getLogger("EncodedAutomaton");
    }

    // The Automaton wrapped with EventType encodings.
    private Automaton model;

    // The encoding scheme for the Automaton.
    // NOTE: To compare 2 EncodedAutomatons, their EventType encodings
    // must be equivalent.
    private EventTypeEncodings<TxnEType> encodings;

    public EncodedAutomaton(EventTypeEncodings<TxnEType> encodings,
            AbsFSM<T, TxnEType> fsm) {
        this.encodings = encodings;
        model = new Automaton();
        convertFSMToAutomaton(fsm);
    }

    private void convertFSMToAutomaton(AbsFSM<T, TxnEType> fsm) {
        // initial state of this automaton
        State initialState = new State();

        Map<T, State> visited = Util.newMap();
        Set<T> initStates = fsm.getInitStates();

        for (T initState : initStates) {
            if (!visited.containsKey(initState)) {
                DFS(initState, initialState, visited);
            }
        }

        model.setInitialState(initialState);
        model.setDeterministic(false);
        model.restoreInvariant();
    }

    /**
     * Traverses the FSM while constructing an equivalent Automaton.
     * 
     * @param state
     *            - FSM state to begin DFS
     * @param autoState
     *            - Automaton state equivalent to the FSM state
     * @param visited
     *            - mapping from visited FSM states to their corresponding
     *            Automaton states
     */
    private void DFS(T state, State autoState, Map<T, State> visited) {
        visited.put(state, autoState);
        autoState.setAccept(state.isAccept());
        Set<TxnEType> transitions = state.getTransitioningEvents();

        for (TxnEType transition : transitions) {
            Set<T> nextStates = state.getNextStates(transition);

            for (T nextState : nextStates) {
                State nextAutoState = visited.get(nextState);
                boolean recurse = false;

                if (nextAutoState == null) {
                    // nextState has not been visited
                    recurse = true;
                    nextAutoState = new State();
                } // else: all descendants of nextState have been visited; no
                  // need to recurse

                char c = encodings.getEncoding(transition);
                Transition autoTransition = new Transition(c, c, nextAutoState);
                autoState.addTransition(autoTransition);

                if (recurse) {
                    DFS(nextState, nextAutoState, visited);
                }
            }
        }
    }

    /**
     * Performs Hopcroft's algorithm to minimize this Automaton.
     */
    public void minimize() {
        MinimizationOperations.minimizeHopcroft(model);
    }

    public Automaton getAutomaton() {
        return model;
    }

    @Override
    public int hashCode() {
        return model.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if (other == this) {
            return true;
        }
        if (!(other instanceof EncodedAutomaton)) {
            return false;
        }
        EncodedAutomaton<T, TxnEType> encodedAutomaton = (EncodedAutomaton<T, TxnEType>) other;
        return model.equals(encodedAutomaton.model);
    }
}
