package jfr.memory;

import jdk.jfr.consumer.RecordedEvent;
import jfr.RecordedEventHandler;

/** This class aggregates the duration of G1 Garbage Collection JFR events */
public final class G1GarbageCollectionHandler implements RecordedEventHandler {
  private static final String METRIC_NAME = "runtime.jvm.gc.duration";
  private static final String METRIC_DESCRIPTION = "GC Duration";
  private static final String EVENT_NAME = "jdk.G1GarbageCollection";

  public G1GarbageCollectionHandler() {
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
    // FIXME Is this a getDuration, or is it named?
    histogram.record(ev.getDuration().toMillis(), ATTR_G1);
  }
}
