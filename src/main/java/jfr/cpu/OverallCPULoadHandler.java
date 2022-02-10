package jfr.cpu;

import jdk.jfr.consumer.RecordedEvent;
import jfr.RecordedEventHandler;

import java.time.Duration;
import java.util.Optional;

public final class OverallCPULoadHandler implements RecordedEventHandler {
  private static final String METRIC_NAME = "runtime.jvm.cpu.utilization";
  private static final String METRIC_DESCRIPTION = "CPU Utilization";
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
      histogram.record(ev.getDouble(JVM_USER), ATTR_USER);
    }
    if (ev.hasField(JVM_SYSTEM)) {
      histogram.record(ev.getDouble(JVM_SYSTEM), ATTR_SYSTEM);
    }
    if (ev.hasField(MACHINE_TOTAL)) {
      histogram.record(ev.getDouble(MACHINE_TOTAL), ATTR_MACHINE);
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
