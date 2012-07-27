package dynoptic.model.fifosys.channel;

import java.util.ArrayList;

import dynoptic.model.alphabet.EventType;

/**
 * The ChannelState maintains the queue state for a channel, identified with a
 * specific channel id.
 */
public class ChannelState {
    final ChannelId chId;
    final ArrayList<EventType> queue;

    public ChannelState(ChannelId chId) {
        this(chId, new ArrayList<EventType>());
    }

    private ChannelState(ChannelId chId, ArrayList<EventType> queue) {
        this.chId = chId;
        this.queue = queue;
    }

    // //////////////////////////////////////////////////////////////////

    /** Adds an event to the back of the queue. */
    public void enqueue(EventType e) {
        queue.add(e);
    }

    /** Removes and returns the event at the top of the queue. */
    public EventType dequeue() {
        return queue.remove(0);
    }

    /** Returns the event at the top of the queue, without removing it. */
    public EventType peek() {
        return queue.get(0);
    }

    /** Returns the number of events in the queue. */
    public int size() {
        return queue.size();
    }

    /**
     * Returns a copy of this ChannelState.
     */
    @SuppressWarnings("unchecked")
    public ChannelState clone() {
        // Since ChannelId is immutable and Event is immutable all we need to do
        // is make sure to clone the ArrayList that maintains events to produce
        // a new independent deep-copy of ChannelState.
        return new ChannelState(chId, (ArrayList<EventType>) queue.clone());
    }
}