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
        Properties properties = new Properties();
        properties.put("model", JfrSchemaFactory.INLINE_MODEL.formatted(jfrFile));

        try (var connection = DriverManager.getConnection("jdbc:calcite:", properties)) {
            conn = connection;
            getSTWTimes();
            getConcurrentTimes();
        } catch (SQLException sqlx) {
            sqlx.printStackTrace();
        }
        outputReport();
    }

    private record STWEventDetails(Instant startTime, long gcId, String when, long heapUsed) {}

    private record GCSummary(Instant startTime, long gcId, long stwDurationMs, long concurrentNs, long heapUsedAfter) {
        public GCSummary(Long gcId, long duration) {
            this(Instant.EPOCH, gcId, 0L, duration, 0L);
        }

        public GCSummary plus(long durationNs) {
            return new GCSummary(startTime, gcId, stwDurationMs, concurrentNs + durationNs, heapUsedAfter);
        }
    }

//    private record ConcurrentEventDetails(Instant startTime, String gcId, String when, long heapUsed) {}

    void getConcurrentTimes() throws SQLException {
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
            SELECT "startTime", "gcId", "when", "heapUsed"
            FROM "JFR"."jdk.GCHeapSummary"
            ORDER BY "gcId"
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
        var stwDuration = after.startTime.toEpochMilli() - before.startTime.toEpochMilli();
        return new GCSummary(before.startTime, before.gcId, stwDuration, 0L, after.heapUsed);
    }

    void outputReport() {
        System.out.println("In outputReport");
        for (var id : stwCollectionsById.keySet().stream().sorted().toList()) {
            System.out.println(stwCollectionsById.get(id));
        }
    }


}
