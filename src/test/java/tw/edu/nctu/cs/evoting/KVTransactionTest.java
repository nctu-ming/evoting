package tw.edu.nctu.cs.evoting;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import tw.edu.nctu.cs.evoting.storage.ConcurrentHashMapKVStore;
import tw.edu.nctu.cs.evoting.storage.KVStore;
import tw.edu.nctu.cs.evoting.storage.LevelDBKVStore;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static org.iq80.leveldb.impl.Iq80DBFactory.bytes;

public class KVTransactionTest {
    @Test
    public void testVirtualOracleTimestamp() throws IOException {
        KVStore<String, byte[]> store = new ConcurrentHashMapKVStore();

        KVTransaction kvTxn = new KVTransaction(store);

        Long v1 = kvTxn.VirtualOracleTimestamp();
        Long v2 = kvTxn.VirtualOracleTimestamp();

        Assert.assertNotEquals(v1, v2);
        Assert.assertEquals(v1.toString().length(), 19);
    }

    @Test
    public void testEncodeDecodeKey() throws IOException {
        KVStore<String, byte[]> store = new ConcurrentHashMapKVStore();

        KVTransaction kvTxn = new KVTransaction(store);
        KVTransaction.Write w = new KVTransaction.Write();

        w.bytesKey = bytes("election_student_union_2022");
        long ts = kvTxn.VirtualOracleTimestamp();
        byte[] encodeKey = w.EncodeKey(KVTransaction.CF.LOCK, ts);
        KVTransaction.RetDecodeKey retDecode = w.DecodeKey(encodeKey);

        Assert.assertEquals(ts, retDecode.ts);
    }
}
