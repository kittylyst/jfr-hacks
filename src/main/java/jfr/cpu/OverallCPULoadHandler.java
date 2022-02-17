package jfr.cpu;

import jdk.jfr.consumer.RecordedEvent;
import jfr.AbstractFileWritingRecordedEventHandler;
import jfr.RecordedEventHandler;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

public final class OverallCPULoadHandler extends AbstractFileWritingRecordedEventHandler {
  private static final String EVENT_NAME = "jdk.CPULoad";
  private static final String JVM_USER = "jvmUser";
  private static final String JVM_SYSTEM = "jvmSystem";
  private static final String MACHINE_TOTAL = "machineTotal";

  public OverallCPULoadHandler(String fileName) throws IOException {
    super(fileName);
  }

  @Override
  protected String getPrefix() {
    return "cpu_";
  }

  @Override
  public void accept(RecordedEvent ev) {
    if (ev.hasField(JVM_USER)) {
      var user = ev.getDouble(JVM_USER);
      if (ev.hasField(JVM_SYSTEM)) {
        var system = ev.getDouble(JVM_SYSTEM);
        if (ev.hasField(MACHINE_TOTAL)) {
          var total = ev.getDouble(MACHINE_TOTAL);
          var timestamp = ev.getStartTime().toEpochMilli();
          try {
            writer.write(String.format("%d,%f,%f,%f%n",timestamp, user, system, total));
          } catch (IOException e) {
            System.err.println("Couldn't write to CPU output file");
          }
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
