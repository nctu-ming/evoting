package tw.edu.nctu.cs.evoting.storage;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class ConcurrentHashMapKVStoreTest {
    private final String testKeyPrefix = "test-key-";
    private final String testValuePrefix = "test-value-";

    int testPutSize = 128;

    private static String genKey(String prefix, String suffix) {
        return prefix + suffix;
    }

    private static byte[] getBytes(String prefix, String suffix) {
        return (prefix + suffix).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    public void testGet() {
        ConcurrentHashMapKVStore kv = new ConcurrentHashMapKVStore();

        for (Integer i = 0; i < testPutSize; i++) {
            kv.syncMap.put(
                    genKey(testKeyPrefix, i.toString()),
                    getBytes(testValuePrefix, i.toString())
            );
        }

        byte[] value = kv.get(genKey(testKeyPrefix, String.valueOf(1)));

        Assert.assertArrayEquals(value, getBytes(testValuePrefix, String.valueOf(1)));
    }

    @Test
    public void testDelete() {
        ConcurrentHashMapKVStore kv = new ConcurrentHashMapKVStore();

        for (Integer i = 0; i < testPutSize; i++) {
            kv.syncMap.put(
                    genKey(testKeyPrefix, i.toString()),
                    getBytes(testValuePrefix, i.toString())
            );
        }

        kv.delete(genKey(testKeyPrefix, String.valueOf(1)));

        assertEquals(testPutSize - 2, kv.syncMap.size());
    }

    @Test
    public void testPut() {
        ConcurrentHashMapKVStore kv = new ConcurrentHashMapKVStore();

        for (Integer i = 0; i < testPutSize; i++) {
            kv.put(
                    genKey(testKeyPrefix, i.toString()),
                    getBytes(testValuePrefix, i.toString())
            );
        }

        assertEquals(testPutSize, kv.syncMap.size());

        for (int i = 0; i < testPutSize; i++) {
            kv.put(genKey(testKeyPrefix, String.valueOf(1)), getBytes(testValuePrefix, String.valueOf(1)));
        }

        assertEquals(testPutSize, kv.syncMap.size());

        byte[] value = kv.syncMap.get(genKey(testKeyPrefix, String.valueOf(1)));

        Assert.assertArrayEquals(value, getBytes(testValuePrefix, String.valueOf(1)));
    }
}
