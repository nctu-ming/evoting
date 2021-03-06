package tw.edu.nctu.cs.evoting;

import tw.edu.nctu.cs.evoting.storage.KVStore;
import tw.edu.nctu.cs.evoting.storage.LevelDBKVStore;
import tw.edu.nctu.cs.evoting.util.JwtManager;

public class Globals {
    protected static final KVStore<String, byte[]> store = new LevelDBKVStore();
    protected static final JwtManager jwtManager = JwtManager.EVotingJwtManager();
}
