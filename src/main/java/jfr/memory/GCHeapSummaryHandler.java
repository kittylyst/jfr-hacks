package jfr.memory;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jfr.AbstractFileWritingRecordedEventHandler;
import jfr.RecordedEventHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static jfr.Constants.*;

/** This class handles GCHeapSummary JFR events. For GC purposes they come in pairs. */
public final class GCHeapSummaryHandler extends AbstractFileWritingRecordedEventHandler {
  private static final String EVENT_NAME = "jdk.GCHeapSummary";

  private final Map<Long, RecordedEvent> awaitingPairs = new HashMap<>();

  public GCHeapSummaryHandler(String fileName) throws IOException {
    super(fileName);
  }

  @Override
  protected String getPrefix() {
    return "gc_";
  }

  @Override
  protected String getHeader() {
    return "timestamp,duration,used,committed";
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
    var duration = after.getStartTime().toEpochMilli() - before.getStartTime().toEpochMilli();
    if (after.hasField(HEAP_USED)) {
      var used = after.getLong(HEAP_USED);
      if (after.hasField(HEAP_SPACE)) {
        if (after.getValue(HEAP_SPACE) instanceof RecordedObject ro) {
          var committed = ro.getLong(COMMITTED_SIZE);
          try {
            var timestamp = after.getStartTime().toEpochMilli();
            writer.write(String.format("%d,%d,%d,%d%n",timestamp, duration, used, committed));
          } catch (IOException e) {
            System.err.println("Couldn't write to CPU output file");
          }

        }
      }
    }
  }

}
