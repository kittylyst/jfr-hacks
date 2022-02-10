package jfr.memory;

import jdk.jfr.consumer.RecordedEvent;
import jfr.AbstractThreadDispatchingHandler;
import jfr.ThreadGrouper;

import java.util.function.Consumer;

/**
 * This class handles all non-TLAB allocation JFR events, and delegates them to the actual
 * per-thread aggregators
 */
public final class ObjectAllocationOutsideTLABHandler extends AbstractThreadDispatchingHandler {
  private static final String EVENT_NAME = "jdk.ObjectAllocationOutsideTLAB";

  public ObjectAllocationOutsideTLABHandler(ThreadGrouper grouper) {
    super(grouper);
    initializeMeter();
  }

  @Override
  public void initializeMeter() {

  }

  @Override
  public String getEventName() {
    return EVENT_NAME;
  }

  @Override
  public Consumer<RecordedEvent> createPerThreadSummarizer(String threadName) {
    return new PerThreadObjectAllocationOutsideTLABHandler(histogram, threadName);
  }

  /** This class aggregates all non-TLAB allocation JFR events for a single thread */
  private static class PerThreadObjectAllocationOutsideTLABHandler
      implements Consumer<RecordedEvent> {
    private static final String ALLOCATION_SIZE = "allocationSize";

    private final DoubleHistogram histogram;
    private final Attributes attributes;

    public PerThreadObjectAllocationOutsideTLABHandler(
        DoubleHistogram histogram, String threadName) {
      this.histogram = histogram;
      this.attributes = Attributes.of(ATTR_THREAD_NAME, threadName, ATTR_ARENA_NAME, "Main");
    }

    @Override
    public void accept(RecordedEvent ev) {
      histogram.record(ev.getLong(ALLOCATION_SIZE), attributes);
      // Probably too high a cardinality
      // ev.getClass("objectClass").getName();
    }
  }
}
