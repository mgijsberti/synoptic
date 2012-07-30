package dynoptic.model.fifosys;

import java.util.Collection;

import dynoptic.model.AbsFSMState;

/**
 * Interface for the non-channel state of multiple FSM processes.
 * 
 * @param <State>
 *            The state of a single FSM process.
 */
public abstract class AbsMultiFSMState<State extends AbsFSMState<State>>
        extends AbsFSMState<State> {

    /** The total number of processes that this multi-FSM state captures, */
    protected int numProcesses;

    public AbsMultiFSMState(int numProcesses) {
        this.numProcesses = numProcesses;
    }

    public int getNumProcesses() {
        return numProcesses;
    }

    /**
     * Whether or not this state is an accept state for the specific process id.
     * For processes that communicate over queues, this is defined as:
     * 
     * <pre>
     * (1) the specified pid is in an accept state, and
     * (2) all queues where this pid is a _receiver_ are empty.
     * </pre>
     * 
     * @param pid
     * @return
     */
    abstract public boolean isAcceptForPid(int pid);

    /**
     * Returns true if all the AbsFSMState states in the collection are accept
     * states.
     * 
     * @param states
     * @return
     */
    protected boolean allAreAccept(Collection<? extends AbsFSMState<?>> states) {
        for (AbsFSMState<?> state : states) {
            if (!state.isAccept()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if there is at least one state that is accept
     * 
     * @param states
     * @return
     */
    /*
     * protected boolean atLeastOneIsAccept(Collection<? extends AbsFSMState<?>>
     * states) { for (ObservedFifoSysState s : observedStates) { if
     * (s.isAcceptForPid(pid)) { return true; } } return false; }
     */
}
