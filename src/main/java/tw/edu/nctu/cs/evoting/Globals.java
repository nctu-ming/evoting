package tw.edu.nctu.cs.evoting;

import tw.edu.nctu.cs.evoting.storage.ConcurrentHashMapKVStore;
import tw.edu.nctu.cs.evoting.storage.KVStore;

public class Globals {
    protected static final KVStore<String, byte[]> store = new ConcurrentHashMapKVStore();
}