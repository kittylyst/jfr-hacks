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
    private Map<String, STWEventDetails> stwEvents = new HashMap<>();
    private Map<Long, STWGCSummary> stwCollectionsById = new HashMap<>();

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
//            getConcurrentTimes();
        } catch (SQLException sqlx) {
            sqlx.printStackTrace();
        }
        outputReport();
    }

    void getConcurrentTimes() throws SQLException {
        var statement = conn.prepareStatement("""
            SELECT TRUNCATE_STACKTRACE("stackTrace", 40), SUM("weight")
            FROM "JFR"."jdk.ObjectAllocationSample"
            GROUP BY TRUNCATE_STACKTRACE("stackTrace", 40)
            ORDER BY SUM("weight") DESC
            LIMIT 10
            """);

        try (var rs = statement.executeQuery()) {
            while (rs.next()) {
                System.out.println("Trace : " + rs.getString(1));
                System.out.println("Weight: " + rs.getLong(2));
            }
        }
    }

    private record STWEventDetails(Instant startTime, String gcId, String when, long heapUsed) {}

    private record STWGCSummary(Instant startTime, String gcId, long durationMs, long heapUsedAfter) {}

    // FIXME What about heapSpace?
    void getSTWTimes() throws SQLException {
        var statement = conn.prepareStatement("""
            SELECT "startTime", "gcId", "when", "heapUsed"
            FROM "JFR"."jdk.GCHeapSummary"
            ORDER BY "gcId"
            """);

        try (var rs = statement.executeQuery()) {
            while (rs.next()) {
                var gcId = rs.getString(2);
                var when = rs.getString(3);
                var current = new STWEventDetails(
                        rs.getTimestamp(1).toInstant(),
                        gcId,
                        when,
                        rs.getLong(4));
                //                System.out.println("Space : " + rs.getLong(4));

                var pair = stwEvents.remove(gcId);
                if (pair == null) {
                    stwEvents.put(gcId, current);
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
                    stwCollectionsById.put(Long.parseLong(gcId), gcSummary);
                }
            }
        }

    }

    STWGCSummary recordValuesGC(STWEventDetails before, STWEventDetails after) {
        var stwDuration = after.startTime.toEpochMilli() - before.startTime.toEpochMilli();
        return new STWGCSummary(before.startTime, before.gcId, stwDuration, after.heapUsed);
    }

    void outputReport() {
        System.out.println("In outputReport");
        for (var id : stwCollectionsById.keySet().stream().sorted().toList()) {
            System.out.println(stwCollectionsById.get(id));
        }
    }


}
