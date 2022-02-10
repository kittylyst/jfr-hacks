package jfr.memory;

import jdk.jfr.consumer.RecordedEvent;
import jfr.AbstractThreadDispatchingHandler;
import jfr.ThreadGrouper;

import java.util.function.Consumer;

/**
 * This class handles TLAB allocation JFR events, and delegates them to the actual per-thread
 * aggregators
 */
public final class ObjectAllocationInNewTLABHandler extends AbstractThreadDispatchingHandler {
  private static final String EVENT_NAME = "jdk.ObjectAllocationInNewTLAB";

  public ObjectAllocationInNewTLABHandler(ThreadGrouper grouper) {
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
    return new PerThreadObjectAllocationInNewTLABHandler(histogram, threadName);
  }

  /** This class aggregates all TLAB allocation JFR events for a single thread */
  private static class PerThreadObjectAllocationInNewTLABHandler
      implements Consumer<RecordedEvent> {
    private static final String TLAB_SIZE = "tlabSize";

    private final DoubleHistogram histogram;
    private final Attributes attributes;

    public PerThreadObjectAllocationInNewTLABHandler(DoubleHistogram histogram, String threadName) {
      this.histogram = histogram;
      this.attributes = Attributes.of(ATTR_THREAD_NAME, threadName, ATTR_ARENA_NAME, "TLAB");
    }

    @Override
    public void accept(RecordedEvent ev) {
      histogram.record(ev.getLong(TLAB_SIZE), attributes);
      // Probably too high a cardinality
      // ev.getClass("objectClass").getName();
    }
  }
}
