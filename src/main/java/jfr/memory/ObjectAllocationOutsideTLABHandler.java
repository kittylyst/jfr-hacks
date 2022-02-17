package jfr.memory;

import jdk.jfr.consumer.RecordedEvent;
import jfr.AbstractThreadDispatchingHandler;
import jfr.ThreadGrouper;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * This class handles all non-TLAB allocation JFR events, and delegates them to the actual
 * per-thread aggregators
 */
public final class ObjectAllocationOutsideTLABHandler extends AbstractThreadDispatchingHandler {
  private static final String EVENT_NAME = "jdk.ObjectAllocationOutsideTLAB";

  public ObjectAllocationOutsideTLABHandler(String fileName, ThreadGrouper grouper) throws IOException {
    super(fileName, grouper);
  }

  @Override
  protected String getPrefix() {
    return "lalloc_";
  }

  @Override
  public String getEventName() {
    return EVENT_NAME;
  }

  @Override
  public Consumer<RecordedEvent> createPerThreadSummarizer(String threadName) {
    return new PerThreadObjectAllocationOutsideTLABHandler(threadName);
  }

  /** This class aggregates all non-TLAB allocation JFR events for a single thread */
  private static class PerThreadObjectAllocationOutsideTLABHandler
      implements Consumer<RecordedEvent> {
    private static final String ALLOCATION_SIZE = "allocationSize";
    private final String threadName;

    public PerThreadObjectAllocationOutsideTLABHandler(String threadName) {
      this.threadName = threadName;
    }

    @Override
    public void accept(RecordedEvent ev) {
      var allocated = ev.getLong(ALLOCATION_SIZE);
      // Probably too high a cardinality
      // ev.getClass("objectClass").getName();
    }
  }
}
