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

  private record CollectionData(long stwDuration, long totalUsed, long committed, Duration concurrentDuration) {
    public static CollectionData of(Duration duration) {
      return new CollectionData(0L, 0L, 0L, duration);
    }

    public CollectionData plusConcurrent(Duration duration) {
      return new CollectionData(stwDuration, totalUsed, committed, concurrentDuration.plus(duration));
    }

    public CollectionData withSTW(long stwDuration, long totalUsed, long committed) {
      return new CollectionData(stwDuration, totalUsed, committed, concurrentDuration);
    }
  }

  private final Map<Long, RecordedEvent> awaitingG1Pairs = new HashMap<>();
  private final Map<Long, RecordedEvent> awaitingGCPairs = new HashMap<>();
  private final Map<Long, CollectionData> collections = new HashMap<>();

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
        acceptConcurrent(event);
        break;
      default:
        logger.fine(String.format("G1 GC Event seen with odd name, this shouldn't happen: %s", name));
    }
  }

  public void accept(RecordedEvent event, Map<Long, RecordedEvent> beforeCache, BiConsumer<RecordedEvent, RecordedEvent> consumer) {
    String when;
    if (event.hasField(WHEN)) {
      when = event.getString(WHEN);
    } else {
      logger.fine(String.format("G1 GC Event seen without when: %s", event));
      return;
    }
    if (!(BEFORE.equals(when) || AFTER.equals(when))) {
      logger.fine(String.format("G1 GC Event seen where when is neither before nor after: %s", event));
      return;
    }

    if (!event.hasField(GC_ID)) {
      logger.fine(String.format("G1 GC Event seen without GC ID: %s", event));
      return;
    }
    long gcId = event.getLong(GC_ID);

    var pair = beforeCache.remove(gcId);
    if (pair == null) {
      beforeCache.put(gcId, event);
    } else {
      consumer.accept(pair, event);
    }
  }

  private void acceptConcurrent(RecordedEvent event) {
    if (!event.hasField(GC_ID)) {
      logger.fine(String.format("G1 GC Event seen without GC ID: %s", event));
      return;
    }
    long gcId = event.getLong(GC_ID);

    var current = collections.get(gcId);
    if (current == null) {
      collections.put(gcId, CollectionData.of(event.getDuration()));
    } else {
      collections.put(gcId, current.plusConcurrent(event.getDuration()));
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
                var data = collections.remove(gcId);
                var timestamp = after.getStartTime().toEpochMilli();
                // Add STW duration, totalHeapUsed, heapCommitted
                writer.write(String.format("%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d%n", timestamp, gcId, data.stwDuration, data.concurrentDuration.toMillis(), data.totalUsed, data.committed, edenUsed, edenDelta, edenTotal, survivorUsed, regions));
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
    var stwDuration = after.getStartTime().toEpochMilli() - before.getStartTime().toEpochMilli();
    if (after.hasField(HEAP_USED)) {
      var totalUsed = after.getLong(HEAP_USED);
      if (after.hasField(HEAP_SPACE)) {
        if (after.getValue(HEAP_SPACE) instanceof RecordedObject ro) {
          var committed = ro.getLong(COMMITTED_SIZE);
          var gcId = after.getLong("gcId");
          var current = collections.get(gcId);
          if (current == null) {
            // (long stwDuration, long totalUsed, long committed, Duration concurrentDuration)
            collections.put(gcId, new CollectionData(stwDuration, totalUsed, committed, Duration.ZERO));
          } else {
            collections.put(gcId, current.withSTW(stwDuration, totalUsed, committed));
          }

        }
      }
    }
  }


}
