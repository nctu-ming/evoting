package tw.edu.nctu.cs.evoting.storage;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import tw.edu.nctu.cs.evoting.KVServer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

import static org.junit.Assert.*;

public class KVServerStoreTest {
    private final String testKeyPrefix = "test-key-";
    private final String testKeyForPrefixScans = "prefix-scans-key-";
    private final String testValuePrefix = "test-value-";

    int testPutSize = 128;

    private static String genKey(String prefix, String suffix) {
        return prefix + suffix;
    }

    private static byte[] getBytes(String prefix, String suffix) {
        return (prefix + suffix).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] genKeyBytes(String prefix, String suffix) {
        return getBytes(prefix, suffix);
    }

    File testDB = new File("testdb");

    @Test
    public void testGet() throws IOException {
        LevelDBKVStore kv = new LevelDBKVStore(testDB);

        for (Integer i = 0; i < testPutSize; i++) {
            kv.db.put(
                    genKeyBytes(testKeyPrefix, i.toString()),
                    getBytes(testValuePrefix, i.toString())
            );
        }

        byte[] value = kv.get(genKey(testKeyPrefix, String.valueOf(1)));

        Assert.assertArrayEquals(value, getBytes(testValuePrefix, String.valueOf(1)));

        byte[] value2 = kv.get("key-not-exists");

        Assert.assertNull(value2);

        kv.clear();
        kv.close();
    }

    @Test
    public void testDelete() throws IOException {
        LevelDBKVStore kv = new LevelDBKVStore(testDB);

        for (Integer i = 0; i < testPutSize; i++) {
            kv.db.put(
                    genKeyBytes(testKeyPrefix, i.toString()),
                    getBytes(testValuePrefix, i.toString())
            );
        }

        assertNotNull(kv.db.get(genKeyBytes(testKeyPrefix, String.valueOf(1))));
        kv.delete(genKey(testKeyPrefix, String.valueOf(1)));

        assertEquals(testPutSize - 1, kv.size());
        assertNull(kv.db.get(genKeyBytes(testKeyPrefix, String.valueOf(1))));

        kv.close();
    }

    @Test
    public void testSize() throws IOException {
        LevelDBKVStore kv = new LevelDBKVStore(testDB);

        for (Integer i = 0; i < testPutSize; i++) {
            kv.put(
                    genKey(testKeyPrefix, i.toString()),
                    getBytes(testValuePrefix, i.toString())
            );
        }

        assertEquals(testPutSize, kv.size());

        kv.delete(genKey(testKeyPrefix, String.valueOf(1)));

        assertEquals(testPutSize - 1, kv.size());

        kv.clear();
        kv.close();
    }

    @Test
    public void testClear() throws IOException {
        LevelDBKVStore kv = new LevelDBKVStore(testDB);

        for (Integer i = 0; i < testPutSize; i++) {
            kv.put(
                    genKey(testKeyPrefix, i.toString()),
                    getBytes(testValuePrefix, i.toString())
            );
        }

        kv.clear();

        assertEquals(0, kv.size());

        kv.close();
    }

    @Test
    public void testPut() throws IOException {
        LevelDBKVStore kv = new LevelDBKVStore(testDB);

        for (Integer i = 0; i < testPutSize; i++) {
            kv.put(
                    genKey(testKeyPrefix, i.toString()),
                    getBytes(testValuePrefix, i.toString())
            );
        }

        assertEquals(testPutSize, kv.size());

        for (int i = 0; i < testPutSize; i++) {
            kv.put(genKey(testKeyPrefix, String.valueOf(1)), getBytes(testValuePrefix, String.valueOf(1)));
        }

        assertEquals(testPutSize, kv.size());

        byte[] value = kv.db.get(genKeyBytes(testKeyPrefix, String.valueOf(1)));

        Assert.assertArrayEquals(value, getBytes(testValuePrefix, String.valueOf(1)));

        kv.clear();
        kv.close();
    }

    @Test
    public void testPrefixScans() throws IOException {
        LevelDBKVStore kv = new LevelDBKVStore(testDB);

        for (Integer i = 0; i < testPutSize; i++) {
            if (i % 2 == 0) {
                kv.put(
                        genKey(testKeyPrefix, i.toString()),
                        getBytes(testValuePrefix, i.toString())
                );
            } else {
                kv.put(
                        genKey(testKeyForPrefixScans, i.toString()),
                        getBytes(testValuePrefix, i.toString())
                );
            }
        }

        LinkedHashMap<String, byte[]> result = kv.prefixScans(testKeyForPrefixScans);

        assertEquals(testPutSize / 2, result.size());

        kv.clear();
        kv.close();
    }
}
