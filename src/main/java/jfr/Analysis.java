package jfr;

import org.moditect.jfranalytics.JfrSchemaFactory;

import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class Analysis {
    private Connection conn = null;
    private GCConfig gcConfig = null;
    private Map<Long, GCSummary> stwCollectionsById = new HashMap<>();
    private long fileStartTime = 0L;

    private static final String SERIAL_YOUNG = "DefNew";
    private static final String SERIAL_OLD = "SerialOld";
    private static final String G1_YOUNG = "G1New";
    private static final String G1_OLD = "G1Old";
    private static final String PARALLEL_YOUNG = "ParallelScavenge";
    private static final String PARALLEL_OLD = "ParallelOld";


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
            getGCTimings();
            if (G1_OLD.equals(gcConfig.oldCollector)) {
                getG1PhaseParallelTimes();
            }
        } catch (SQLException sqlx) {
            sqlx.printStackTrace();
        }
        outputReport();
    }

    // FIXME Strings could be enums
    private record GCConfig(String youngCollector, String oldCollector, int stwGCThreads, int concurrentGCThreads) {}

    void getGCConfig() throws SQLException {
        var statement = conn.prepareStatement("""
            SELECT "youngCollector", "oldCollector", "parallelGCThreads", "concurrentGCThreads"
            FROM "JFR"."jdk.GCConfiguration"
            LIMIT 1
            """);

        try (var rs = statement.executeQuery()) {
            while (rs.next()) {
                final var youngGC = rs.getString(1);
                final var oldGC = rs.getString(2);
                var parallelThreads = rs.getInt(3);
                var concThreads = rs.getInt(4);
                if (SERIAL_OLD.equals(oldGC)) {
                    parallelThreads = 1;
//                    concThreads = 1;
                }
                gcConfig = new GCConfig(youngGC, oldGC, parallelThreads, concThreads);
                break;
            }
        }
    }

    private record GCSummary(Instant startTime, long gcId, String name, long elapsedDurationNs, long parallelNs, long heapUsedAfter,
                             long totalPause, long longestPause) {
        public GCSummary plus(long durationNs) {
            return new GCSummary(startTime, gcId, name, elapsedDurationNs, parallelNs + durationNs, heapUsedAfter, totalPause, longestPause);
        }
    }

    void getG1PhaseParallelTimes() throws SQLException {
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
                    System.err.println("GCPhaseParallel seen before start event for gcId: "+ gcId);
                } else {
                    stwCollectionsById.put(gcId, collection.plus(durationNs));
                }
            }
        }
    }

    // FIXME What about heapSpace?
    void getGCTimings() throws SQLException {
        var statement = conn.prepareStatement("""
            SELECT s."startTime", s."gcId", s."heapUsed", c."name", c."duration", c."sumOfPauses", c."longestPause"
            FROM "JFR"."jdk.GCHeapSummary" s, "JFR"."jdk.GarbageCollection" c
            WHERE s."gcId" = c."gcId" AND s."when" = 'After GC'
            ORDER BY s."gcId"
            """);

        try (var rs = statement.executeQuery()) {
            while (rs.next()) {
                var start = rs.getTimestamp(1).toInstant();
                var gcId = Long.valueOf(rs.getString(2));
                var used = rs.getLong(3);
                var name = rs.getString(4);
                var elapsedDurationNs = rs.getLong(5);
                var totalPause = rs.getLong(6);
                var longestPause = rs.getLong(7);
                if (fileStartTime == 0L) {
                    fileStartTime = start.toEpochMilli();
                }

                var summary = new GCSummary(start, gcId, name, elapsedDurationNs, 0L, used, totalPause, longestPause);
                stwCollectionsById.put(gcId, summary);
            }
        }

    }

    void outputReport() {
        System.err.println("Config: "+ gcConfig);
        System.out.println("timestamp,gcId,elapsedMs,cpuUsedMs,totalPause,longestPause,heapUsedAfter");
        for (var id : stwCollectionsById.keySet().stream().sorted().toList()) {
            var collection = stwCollectionsById.get(id);
            var cpuTimeUsed = calculateCPUTimeNs(collection);

            System.out.println(outputCSV(collection, cpuTimeUsed));
        }
    }

    long calculateCPUTimeNs(GCSummary collection) {
        if (isConcurrent(collection)) {
            return collection.elapsedDurationNs * gcConfig.concurrentGCThreads + (collection.totalPause * gcConfig.stwGCThreads - gcConfig.concurrentGCThreads);
        } else {
            return collection.elapsedDurationNs * gcConfig.stwGCThreads;
        }
    }

    static boolean isConcurrent(GCSummary collection) {
        return switch (collection.name) {
            case G1_OLD -> true;
            case G1_YOUNG, SERIAL_YOUNG, SERIAL_OLD  -> false;
            default -> false;
        };
    }

    String outputCSV(GCSummary c, long cpuTimeUsedNs) {
        var timestamp = c.startTime.toEpochMilli() - fileStartTime;
        var cpuTimeUsedMs = ((double)cpuTimeUsedNs / 1_000_000);
        var elapsedMs = ((double)c.elapsedDurationNs / 1_000_000);
        var totalPauseMs = ((double)c.totalPause / 1_000_000);
        var longestPauseMs = ((double)c.longestPause / 1_000_000);

        return String.format("%d,%d,%f,%f,%f,%f,%d", timestamp, c.gcId, elapsedMs, cpuTimeUsedMs, totalPauseMs, longestPauseMs, c.heapUsedAfter);
    }


}
