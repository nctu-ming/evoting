package tw.edu.nctu.cs.evoting.storage;

public interface KVStore<K, V> {
    void put(K key, V value);

    V get(K key);

    void delete(K key);
}
