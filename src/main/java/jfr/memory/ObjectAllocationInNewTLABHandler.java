package jfr.memory;

import jdk.jfr.consumer.RecordedEvent;
import jfr.AbstractThreadDispatchingHandler;
import jfr.ThreadGrouper;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * This class handles TLAB allocation JFR events, and delegates them to the actual per-thread
 * aggregators
 */
public final class ObjectAllocationInNewTLABHandler extends AbstractThreadDispatchingHandler {
  private static final String EVENT_NAME = "jdk.ObjectAllocationInNewTLAB";

  public ObjectAllocationInNewTLABHandler(String prefix, ThreadGrouper grouper) throws IOException {
    super(prefix, grouper);
  }

  @Override
  protected String getPrefix() {
    return "tlab_";
  }

  @Override
  public String getEventName() {
    return EVENT_NAME;
  }

  @Override
  public Consumer<RecordedEvent> createPerThreadSummarizer(String threadName) {
    return new PerThreadObjectAllocationInNewTLABHandler(threadName);
  }

  /** This class aggregates all TLAB allocation JFR events for a single thread */
  private static class PerThreadObjectAllocationInNewTLABHandler
      implements Consumer<RecordedEvent> {
    private static final String TLAB_SIZE = "tlabSize";
    private final String attributes;

    public PerThreadObjectAllocationInNewTLABHandler(String threadName) {
      this.attributes = threadName;
    }

    @Override
    public void accept(RecordedEvent ev) {
      var allocated = ev.getLong(TLAB_SIZE);
      // Probably too high a cardinality
      // ev.getClass("objectClass").getName();
    }
  }
}
