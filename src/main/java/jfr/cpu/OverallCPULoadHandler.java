package jfr.cpu;

import jdk.jfr.consumer.RecordedEvent;
import jfr.RecordedEventHandler;

import java.time.Duration;
import java.util.Optional;

public final class OverallCPULoadHandler implements RecordedEventHandler {
  private static final String EVENT_NAME = "jdk.CPULoad";
  private static final String JVM_USER = "jvmUser";
  private static final String JVM_SYSTEM = "jvmSystem";
  private static final String MACHINE_TOTAL = "machineTotal";

  public OverallCPULoadHandler() {
    initializeMeter();
  }

  @Override
  public void initializeMeter() {
  }

  @Override
  public void accept(RecordedEvent ev) {
    if (ev.hasField(JVM_USER)) {
      var user = ev.getDouble(JVM_USER);
      if (ev.hasField(JVM_SYSTEM)) {
        var system = ev.getDouble(JVM_SYSTEM);
        if (ev.hasField(MACHINE_TOTAL)) {
          var total = ev.getDouble(MACHINE_TOTAL);
        }
      }
    }
  }

  @Override
  public String getEventName() {
    return EVENT_NAME;
  }

  @Override
  public Optional<Duration> getPollingDuration() {
    return Optional.of(Duration.ofSeconds(1));
  }
}
