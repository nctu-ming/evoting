package tw.edu.nctu.cs.evoting.storage;

import java.util.concurrent.ConcurrentHashMap;

public class ConcurrentHashMapKVStore implements KVStore<byte[], byte[]> {
    final private ConcurrentHashMap<byte[], byte[]> syncMap;

    public ConcurrentHashMapKVStore() {
        this.syncMap = new ConcurrentHashMap<>();
    }

    @Override
    public void put(byte[] key, byte[] value) {
        this.syncMap.put(key, value);
    }

    @Override
    public byte[] get(byte[] key) {
        return this.syncMap.get(key);
    }

    @Override
    public void delete(byte[] key) {
        this.syncMap.remove(key);
    }
}
