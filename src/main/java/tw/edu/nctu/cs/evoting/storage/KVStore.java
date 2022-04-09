package tw.edu.nctu.cs.evoting.storage;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public interface KVStore<K, V> extends Closeable {
    void put(K key, V value);

    V get(K key);

    int size();

    void delete(K key);

    void clear();

    void close();

    LinkedHashMap<K, V> prefixScans(String prefix);
}
