package jfr;

import jfr.cpu.OverallCPULoadHandler;
import jfr.memory.G1HeapSummaryHandler;
import jfr.memory.GCHeapSummaryHandler;
import jfr.memory.ObjectAllocationInNewTLABHandler;
import jfr.memory.ObjectAllocationOutsideTLABHandler;

import java.util.ArrayList;
import java.util.List;

final class HandlerRegistry {
  private final List<RecordedEventHandler> mappers;

  private HandlerRegistry(List<? extends RecordedEventHandler> mappers) {
    this.mappers = new ArrayList<>(mappers);
  }

  static HandlerRegistry createDefault() {
    var grouper = new ThreadGrouper();
    var handlers =
        List.of(
            new ObjectAllocationInNewTLABHandler(grouper),
            new ObjectAllocationOutsideTLABHandler(grouper),
//            new NetworkReadHandler(grouper),
//            new NetworkWriteHandler(grouper),
            new G1HeapSummaryHandler(),
            new GCHeapSummaryHandler(),
//            new ContextSwitchRateHandler(),
            new OverallCPULoadHandler()
//            new ContainerConfigurationHandler(),
//            new LongLockHandler(grouper)
        );
//    handlers.forEach(handler -> handler.initializeMeter());

    return new HandlerRegistry(handlers);
  }

  /** @return all entries in this registry. */
  List<RecordedEventHandler> all() {
    return mappers;
  }
}
