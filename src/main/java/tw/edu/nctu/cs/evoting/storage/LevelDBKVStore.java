package tw.edu.nctu.cs.evoting.storage;

import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.iq80.leveldb.*;
import static org.iq80.leveldb.impl.Iq80DBFactory.*;
import java.io.*;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

public class LevelDBKVStore implements KVStore<String, byte[]>  {
    private static final Logger logger = LoggerFactory.getLogger(LevelDBKVStore.class);

    protected DB db;

    private File dbFile;

    private boolean isClosed = false;

    public LevelDBKVStore(File dbFile) {
        this.dbFile = dbFile;

        Options options = new Options();
        options.createIfMissing(true);

        try {
            this.db = factory.open(dbFile, options);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void put(String key, byte[] value) {
        this.db.put(key.getBytes(StandardCharsets.UTF_8), value);
    }

    @Override
    public byte[] get(String key) {
        return this.db.get(key.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void delete(String key) {
        this.db.delete(key.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void clear() {
        try {
            this.db.close();
        } catch (IOException e) {
            logger.error("this.db.close", e);
        }

        Options options = new Options();
        try {
            factory.destroy(dbFile, options); // for lab 2
        } catch (IOException e) {
            logger.error("this.db.destory", e);
        }

        try {
            this.db = factory.open(dbFile, options);
        } catch (IOException e) {
            logger.error("factory.open", e);
        }
    }

    @Override
    public void close() {
        try {
            // TODO: race condition
            this.db.close();
            isClosed = true;
        } catch (IOException e) {
            logger.error("close", e);
        }
    }

    @Override
    public int size() {
        try (DBIterator iterator = db.iterator()) {
            int size = 0;
            for(iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
                size++;
            }
            return size;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public LinkedHashMap<String, byte[]> prefixScans(String prefix) {
        try (DBIterator iterator = db.iterator()) {
            LinkedHashMap<String, byte[]> result = new LinkedHashMap<>();
            for (iterator.seek(bytes(prefix)); iterator.hasNext(); iterator.next()) {
                String key = asString(iterator.peekNext().getKey());
                if (!key.startsWith(prefix)) {
                    break;
                }

                result.put(key, iterator.peekNext().getValue());
            }

            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Not good
    public LinkedHashMap<String, ByteString> prefixScansForKVS(String prefix) {
        try (DBIterator iterator = db.iterator()) {
            LinkedHashMap<String, ByteString> result = new LinkedHashMap<>();
            for (iterator.seek(bytes(prefix)); iterator.hasNext(); iterator.next()) {
                String key = asString(iterator.peekNext().getKey());
                if (!key.startsWith(prefix)) {
                    break;
                }

                result.put(key, ByteString.copyFrom(iterator.peekNext().getValue()));
            }

            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
