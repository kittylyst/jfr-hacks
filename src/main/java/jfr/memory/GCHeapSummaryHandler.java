package jfr.memory;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jfr.RecordedEventHandler;

import java.util.HashMap;
import java.util.Map;

/** This class handles GCHeapSummary JFR events. For GC purposes they come in pairs. */
public final class GCHeapSummaryHandler implements RecordedEventHandler {
  private static final String METRIC_NAME_DURATION = "runtime.jvm.gc.duration";
  private static final String METRIC_DESCRIPTION_DURATION = "GC Duration";
  private static final String METRIC_NAME_MEMORY = "runtime.jvm.memory.utilization";
  private static final String METRIC_DESCRIPTION_MEMORY = "Heap utilization";
  private static final String EVENT_NAME = "jdk.GCHeapSummary";
  private static final String BEFORE = "Before GC";
  private static final String AFTER = "After GC";
  private static final String GC_ID = "gcId";
  private static final String WHEN = "when";
  private static final String HEAP_USED = "heapUsed";
  private static final String HEAP_SPACE = "heapSpace";
  private static final String COMMITTED_SIZE = "committedSize";

  private final Map<Long, RecordedEvent> awaitingPairs = new HashMap<>();

  public GCHeapSummaryHandler() {
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
  public void accept(RecordedEvent ev) {
    String when = null;
    if (ev.hasField(WHEN)) {
      when = ev.getString(WHEN);
    }
    if (when != null) {
      if (!(when.equals(BEFORE) || when.equals(AFTER))) {
        return;
      }
    }

    if (!ev.hasField(GC_ID)) {
      return;
    }
    long gcId = ev.getLong(GC_ID);

    var pair = awaitingPairs.get(gcId);
    if (pair == null) {
      awaitingPairs.put(gcId, ev);
    } else {
      awaitingPairs.remove(gcId);
      if (when != null && when.equals(BEFORE)) {
        recordValues(ev, pair);
      } else { //  i.e. when.equals(AFTER)
        recordValues(pair, ev);
      }
    }
  }

  private void recordValues(RecordedEvent before, RecordedEvent after) {
    durationHistogram.record(
        after.getStartTime().toEpochMilli() - before.getStartTime().toEpochMilli());
    if (after.hasField(HEAP_USED)) {
      memoryHistogram.record(after.getLong(HEAP_USED), ATTR_MEMORY_USED);
    }
    if (after.hasField(HEAP_SPACE)) {
      if (after.getValue(HEAP_SPACE) instanceof RecordedObject ro) {
        memoryHistogram.record(ro.getLong(COMMITTED_SIZE), ATTR_MEMORY_COMMITTED);
      }
    }
  }
}
