package jfr;

import jfr.cpu.OverallCPULoadHandler;
import jfr.memory.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class HandlerRegistry implements AutoCloseable {
  private final List<RecordedEventHandler> mappers;

  private HandlerRegistry(List<? extends RecordedEventHandler> mappers) {
    this.mappers = new ArrayList<>(mappers);
  }

  static HandlerRegistry createDefault(String rawFileName) throws IOException {
    var fileName = rawFileName.replaceAll("\\..*", "");
    System.out.println("Using filename: "+ fileName);
    var grouper = new ThreadGrouper();
    var handlers =
        List.of(
            new ObjectAllocationInNewTLABHandler(fileName, grouper),
            new ObjectAllocationOutsideTLABHandler(fileName, grouper),
//            new NetworkReadHandler(grouper),
//            new NetworkWriteHandler(grouper),
            new G1HeapSummaryHandler(fileName),
            new GCHeapSummaryHandler(fileName),
            new G1CombinedHandler(fileName),
//            new ContextSwitchRateHandler(),
            new OverallCPULoadHandler(fileName)
//            new ContainerConfigurationHandler(),
//            new LongLockHandler(grouper)
        );
//    handlers.forEach(handler -> handler.initialize());

    return new HandlerRegistry(handlers);
  }

  /** @return all entries in this registry. */
  List<RecordedEventHandler> all() {
    return mappers;
  }

  @Override
  public void close() {
    all().forEach(h -> h.safeShutdown());
//    System.out.println("Handlers closed successfully");
  }
}
