package org.opensearch.migrations.trafficcapture;

import com.google.protobuf.CodedOutputStream;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

@Slf4j
public class FileConnectionCaptureFactory implements IConnectionCaptureFactory {
    private final BiFunction<String, Integer, FileOutputStream> outputStreamCreator;

    public FileConnectionCaptureFactory(BiFunction<String, Integer, FileOutputStream> outputStreamCreator) {
        this.outputStreamCreator = outputStreamCreator;
    }

    public FileConnectionCaptureFactory(Path rootPath) {
        this((id, n) -> {
            try {
                return new FileOutputStream(rootPath.resolve(id+"_"+n.toString()+".protocap").toString());
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public FileConnectionCaptureFactory(String path) {
        this(Paths.get(path));
    }

    @Override
    public IChannelConnectionCaptureSerializer createOffloader(String connectionId) throws IOException {
        AtomicInteger supplierCallCounter = new AtomicInteger();
        WeakHashMap<CodedOutputStream,FileOutputStream> codedStreamToFileStreamMap = new WeakHashMap<>();
        return new StreamChannelConnectionCaptureSerializer(connectionId, () -> {
                    var fs = outputStreamCreator.apply(connectionId, new Integer(supplierCallCounter.incrementAndGet()));
                    var cos = CodedOutputStream.newInstance(fs);
                    codedStreamToFileStreamMap.put(cos, fs);
                    return cos;
                }, (stream) -> CompletableFuture.runAsync(() -> {
                    try {
                        var fs = codedStreamToFileStreamMap.get(stream);
                        fs.flush();
                        log.warn("NOT removing the CodedOutputStream from the WeakHashMap, which is a memory leak.  Doing this until the system knows when to properly flush buffers");
                        //codedStreamToFileStreamMap.remove(stream);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }));
    }
}
