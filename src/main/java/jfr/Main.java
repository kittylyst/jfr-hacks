package jfr;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import java.io.IOException;
import java.nio.file.Paths;

public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: Main <file>");
            System.exit(1);
        }


        var fileName = args[0];
        try (var recordingFile = new RecordingFile(Paths.get(fileName))) {
            var registry = HandlerRegistry.createDefault();
            while (recordingFile.hasMoreEvents()) {
                var event = recordingFile.readEvent();
                if (event != null) {
                    registry.all().stream()
                            .filter(h -> h.test(event))
                            .forEach(h -> h.accept(event));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Object decodeEvent(RecordedEvent event) {
        return null;
    }
}
