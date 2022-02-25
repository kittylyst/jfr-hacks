/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package jfr.memory;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jfr.AbstractFileWritingRecordedEventHandler;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

import static jfr.Constants.*;

/**
 * This class handles several different JFR events related to G1.
 *
 * Basic heap values are sourced from GCHeapSummary - G1HeapSummary has more details (e.g.) YG
 * For GC purposes events come in pairs of a specific type.
 *
 * Do we also need GCPhaseParallel for concurrent time of a GC?
 *
 */
public final class G1CombinedHandler extends AbstractFileWritingRecordedEventHandler {
  private static final Logger logger = Logger.getLogger(G1CombinedHandler.class.getName());

  private static final String G1_EVENT_NAME = "jdk.G1HeapSummary";
  private static final String GC_EVENT_NAME = "jdk.GCHeapSummary";
  private static final String PARA_EVENT_NAME = "jdk.GCPhaseParallel";

  private final Map<Long, RecordedEvent> awaitingG1Pairs = new HashMap<>();
  private final Map<Long, RecordedEvent> awaitingGCPairs = new HashMap<>();
  private long stwDuration = 0L;
  private long totalUsed = 0L;
  private long committed = 0L;
  private Duration concurrentDuration = Duration.ZERO;

  public G1CombinedHandler(String fileName) throws IOException {
    super(fileName);
  }

  @Override
  protected String getPrefix() {
    return "g1all_";
  }

  @Override
  protected String getHeader() {
    return "timestamp,gcId,stwDuration,concurrentDuration,totalUsed,committed,edenUsed,edenDelta,edenTotal,survivorUsed,regions";
  }

  @Override
  public String getEventName() {
    return "<G1_MULTIPLE>";
  }

  @Override
  public boolean test(RecordedEvent event) {
    var name = event.getEventType().getName();
    return G1_EVENT_NAME.equals(name) || GC_EVENT_NAME.equals(name) || PARA_EVENT_NAME.equals(name);
  }

  @Override
  public void accept(RecordedEvent event) {
    var name = event.getEventType().getName();
    switch (name) {
      case G1_EVENT_NAME:
        accept(event, awaitingG1Pairs, this::recordValuesG1);
        break;
      case GC_EVENT_NAME:
        accept(event, awaitingGCPairs, this::recordValuesGC);
        break;
      case PARA_EVENT_NAME:
        concurrentDuration = concurrentDuration.plus(event.getDuration());
        break;
      default:
        logger.fine(String.format("G1 GC Event seen with odd name, this shouldn't happen: %s", name));
    }

  }

  public void accept(RecordedEvent ev, Map<Long, RecordedEvent> beforeCache, BiConsumer<RecordedEvent, RecordedEvent> consumer) {
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

    var pair = beforeCache.remove(gcId);
    if (pair == null) {
      beforeCache.put(gcId, ev);
    } else {
      consumer.accept(pair, ev);
    }
  }

  private void recordValuesG1(RecordedEvent before, RecordedEvent after) {
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
                // This is where the write-out happens
                var gcId = after.getLong("gcId");
                var timestamp = after.getStartTime().toEpochMilli();
                // Add STW duration, totalHeapUsed, heapCommitted
                writer.write(String.format("%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d%n", timestamp, gcId, stwDuration, concurrentDuration.toMillis(), totalUsed, committed, edenUsed, edenDelta, edenTotal, survivorUsed, regions));
              } catch (IOException e) {
                System.err.println("Couldn't write to GC output file");
              }
            }
          }
        }
      }
    }
  }

  private void recordValuesGC(RecordedEvent before, RecordedEvent after) {
    stwDuration = after.getStartTime().toEpochMilli() - before.getStartTime().toEpochMilli();
    if (after.hasField(HEAP_USED)) {
      totalUsed = after.getLong(HEAP_USED);
      if (after.hasField(HEAP_SPACE)) {
        if (after.getValue(HEAP_SPACE) instanceof RecordedObject ro) {
          committed = ro.getLong(COMMITTED_SIZE);
       }
      }
    }
  }


}
