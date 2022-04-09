package tw.edu.nctu.cs.evoting.storage;

import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;

public class ConcurrentHashMapKVStore implements KVStore<String, byte[]> {
    final protected ConcurrentHashMap<String, byte[]> syncMap;

    public ConcurrentHashMapKVStore() {
        this.syncMap = new ConcurrentHashMap<>();
    }

    @Override
    public void put(String key, byte[] value) {
        this.syncMap.put(key, value);
    }

    @Override
    public byte[] get(String key) {
        return this.syncMap.get(key);
    }

    @Override
    public int size() {
        return this.syncMap.size();
    }

    @Override
    public void delete(String key) {
        this.syncMap.remove(key);
    }

    @Override
    public void clear() {
        this.syncMap.clear();
    }

    @Override
    public void close() {

    }

    @Override
    public LinkedHashMap<String, byte[]> prefixScans(String prefix) {
        // TODO:
        return null;
    }
}
