/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package jfr.memory;

import jdk.jfr.consumer.RecordedEvent;
import jfr.AbstractFileWritingRecordedEventHandler;
import jfr.RecordedEventHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * This class handles G1HeapSummary JFR events. For GC purposes they come in pairs. Basic heap
 * values are sourced from GCHeapSummary - this is young generational details
 */
public final class G1HeapSummaryHandler extends AbstractFileWritingRecordedEventHandler {
  private static final Logger logger = Logger.getLogger(G1HeapSummaryHandler.class.getName());

  private static final String EVENT_NAME = "jdk.G1HeapSummary";
  private static final String BEFORE = "Before GC";
  private static final String AFTER = "After GC";
  private static final String GC_ID = "gcId";
  private static final String WHEN = "when";

  private final Map<Long, RecordedEvent> awaitingPairs = new HashMap<>();

  public G1HeapSummaryHandler(String fileName) throws IOException {
    super(fileName);
  }

  @Override
  protected String getPrefix() {
    return "g1gc_";
  }

  @Override
  protected String getHeader() {
    return "timestamp,edenUsed,edenDelta,edenTotal,survivorUsed,regions";
  }

  @Override
  public String getEventName() {
    return EVENT_NAME;
  }

  @Override
  public void accept(RecordedEvent ev) {
    String when;
    if (ev.hasField(WHEN)) {
      when = ev.getString(WHEN);
    } else {
      logger.fine(String.format("G1 GC Event seen without when: %s", ev));
      return;
    }
    if (!(BEFORE.equals(when) || AFTER.equals(when))) {
      logger.fine(String.format("G1 GC Event seen where when is neither before nor after: %s", ev));
      return;
    }

    if (!ev.hasField(GC_ID)) {
      logger.fine(String.format("G1 GC Event seen without GC ID: %s", ev));
      return;
    }
    long gcId = ev.getLong(GC_ID);

    var pair = awaitingPairs.remove(gcId);
    if (pair == null) {
      awaitingPairs.put(gcId, ev);
    } else {
      if (when.equals(BEFORE)) {
        recordValues(ev, pair);
      } else { //  i.e. when.equals(AFTER)
        recordValues(pair, ev);
      }
    }
  }

  private void recordValues(RecordedEvent before, RecordedEvent after) {
    if (after.hasField("edenUsedSize")) {
      var edenUsed = after.getLong("edenUsedSize");
      if (before.hasField("edenUsedSize")) {
        var edenDelta = edenUsed - before.getLong("edenUsedSize");
        if (after.hasField("edenTotalSize")) {
          var edenTotal = after.getLong("edenTotalSize");
          if (after.hasField("survivorUsedSize")) {
            var survivorUsed = after.getLong("survivorUsedSize");
            if (after.hasField("numberOfRegions")) {
              var regions = after.getLong("numberOfRegions");
              try {
                var timestamp = after.getStartTime().toEpochMilli();
                writer.write(String.format("%d,%d,%d,%d,%d,%d%n",timestamp, edenUsed, edenDelta, edenTotal, survivorUsed, regions));
              } catch (IOException e) {
                System.err.println("Couldn't write to CPU output file");
              }
            }
          }
        }
      }
    }
  }
}
