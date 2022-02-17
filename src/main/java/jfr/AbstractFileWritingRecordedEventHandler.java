package jfr;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class AbstractFileWritingRecordedEventHandler implements RecordedEventHandler {

    private final String fileName;
    protected final BufferedWriter writer;
    private final Path tempPath;

    public AbstractFileWritingRecordedEventHandler(String fileName) throws IOException {
        this.fileName = fileName;
        tempPath = Files.createTempFile(fileName,"tmp");
        writer = Files.newBufferedWriter(tempPath);
    }

    @Override
    public void shutdown() throws IOException {
        writer.close();
        var target = Path.of(getPrefix() + fileName + ".csv");
        Files.move(tempPath, target);
    }

    protected abstract String getPrefix();

}
