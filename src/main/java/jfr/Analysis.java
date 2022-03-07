package jfr;

import org.moditect.jfranalytics.JfrSchemaFactory;

import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static jfr.Constants.AFTER;
import static jfr.Constants.BEFORE;

public class Analysis {
    private Connection conn = null;
    private GCConfig gcConfig = null;
    private Map<Long, STWEventDetails> stwEventsById = new HashMap<>();
    private Map<Long, GCSummary> stwCollectionsById = new HashMap<>();

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: Main <file>");
            System.exit(1);
        }

        var main = new Analysis();
        main.run(args[0]);
    }

    public void run(String fName) {
        var jfrFile = Path.of(fName);
        var properties = new Properties();
        properties.put("model", JfrSchemaFactory.INLINE_MODEL.formatted(jfrFile));

        try (var connection = DriverManager.getConnection("jdbc:calcite:", properties)) {
            conn = connection;
            getGCConfig();
            getSTWTimes();
            getG1NewParallelTimes();
        } catch (SQLException sqlx) {
            sqlx.printStackTrace();
        }
        outputReport();
    }

    // FIXME Strings could be enums
    private record GCConfig(String youngCollector, String oldCollector, int parallelGCThreads, int concurrentGCThreads) {}

//    jdk.GCConfiguration {
//        startTime = 14:43:44.799
//        youngCollector = "G1New"
//        oldCollector = "G1Old"
//        parallelGCThreads = 2
//        concurrentGCThreads = 1
//        usesDynamicGCThreads = true
//        isExplicitGCConcurrent = false
//        isExplicitGCDisabled = false
//        pauseTarget = N/A
//        gcTimeRatio = 12
//    }
    void getGCConfig() throws SQLException {
        var statement = conn.prepareStatement("""
            SELECT "youngCollector", "oldCollector", "parallelGCThreads", "concurrentGCThreads"
            FROM "JFR"."jdk.GCConfiguration"
            LIMIT 1
            """);

        try (var rs = statement.executeQuery()) {
            while (rs.next()) {
                gcConfig = new GCConfig(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4));
                break;
            }
        }
    }

    private record STWEventDetails(Instant startTime, long gcId, String when, long heapUsed) {}

    private record GCSummary(Instant startTime, long gcId, long elapsedDurationMs, long parallelNs, long heapUsedAfter) {
        public GCSummary(Long gcId, long duration) {
            this(Instant.EPOCH, gcId, 0L, duration, 0L);
        }

        public GCSummary plus(long durationNs) {
            return new GCSummary(startTime, gcId, elapsedDurationMs, parallelNs + durationNs, heapUsedAfter);
        }
    }

    void getG1NewParallelTimes() throws SQLException {
        var statement = conn.prepareStatement("""
            SELECT "gcId", "duration"
            FROM "JFR"."jdk.GCPhaseParallel"
            ORDER BY "gcId"
            """);

        try (var rs = statement.executeQuery()) {
            while (rs.next()) {
                var gcId = Long.valueOf(rs.getString(1));
                var durationNs = rs.getLong(2);

                var collection = stwCollectionsById.get(gcId);
                if (collection == null) {
                    stwCollectionsById.put(gcId, new GCSummary(gcId, durationNs));
                } else {
                    stwCollectionsById.put(gcId, collection.plus(durationNs));
                }
            }
        }
    }

    // FIXME What about heapSpace?
    void getSTWTimes() throws SQLException {
        var statement = conn.prepareStatement("""
            SELECT "s.startTime", "s.gcId", "s.when", "s.heapUsed", "c.name", "c.duration"
            FROM "JFR"."jdk.GCHeapSummary" s, "JFR"."jdk.GarbageCollection" c
            WHERE "s.gcId" = "c.gcId"
            ORDER BY "s.gcId"
            """);

        try (var rs = statement.executeQuery()) {
            while (rs.next()) {
                var gcId = Long.valueOf(rs.getString(2));
                var when = rs.getString(3);
                var current = new STWEventDetails(
                        rs.getTimestamp(1).toInstant(),
                        gcId,
                        when,
                        rs.getLong(4));
                //                System.out.println("Space : " + rs.getLong(4));

                var pair = stwEventsById.remove(gcId);
                if (pair == null) {
                    stwEventsById.put(gcId, current);
                } else {
                    var gcSummary = switch (current.when) {
                        case BEFORE -> recordValuesGC(current, pair);
                        case AFTER -> recordValuesGC(pair, current);
                        default -> {
                            System.err.println("Weird event seen: "+ current);
                            yield null;
                        }
                    };
                    if (gcSummary == null) {
                        continue;
                    }
                    stwCollectionsById.put(gcId, gcSummary);
                }
            }
        }

    }

    GCSummary recordValuesGC(STWEventDetails before, STWEventDetails after) {
        // FIXME This is not the stwDuration, it is the elapsed wall clock time between the first and last event for the GC
        var elapsedDuration = after.startTime.toEpochMilli() - before.startTime.toEpochMilli();
//        System.out.println(String.format("%d %tQ %tQ", stwDuration, before.startTime.toEpochMilli(), after.startTime.toEpochMilli()));
        return new GCSummary(before.startTime, before.gcId, elapsedDuration, 0L, after.heapUsed);
    }

    void outputReport() {
        System.out.println("Config: "+ gcConfig);
        for (var id : stwCollectionsById.keySet().stream().sorted().toList()) {
            System.out.println(stwCollectionsById.get(id));
        }
    }


}
