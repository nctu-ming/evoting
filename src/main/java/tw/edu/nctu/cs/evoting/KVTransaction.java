package tw.edu.nctu.cs.evoting;

// key: election_student_union_2022
// cf:key:version
// key format
// cf_lock_:election_student_union_2022:255073580723571
// cf_write:election_student_union_2022:255073580723571
// cf_data_:election_student_union_2022:255073580723571

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;
import tw.edu.nctu.cs.evoting.storage.KVStore;
import tw.edu.nctu.cs.evoting.storage.LevelDBKVStore;

import java.io.File;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Set;

import static org.iq80.leveldb.impl.Iq80DBFactory.asString;
import static org.iq80.leveldb.impl.Iq80DBFactory.bytes;

public class KVTransaction {
    static int bytesCFLen = 8;
    static int bytesTS = Long.BYTES;

    public enum CF {
        LOCK, WRITE, DATA
    }

    private long startTS;
    private long commitTS;
    private Write[] writes;
    private KVStore<String, byte[]> store;

    public long VirtualOracleTimestamp() {
        Clock clock = Clock.systemDefaultZone();
        Instant instant = clock.instant();   // or Instant.now();

        long time = instant.getEpochSecond();
        time *= 1000000000L;
        time += instant.getNano();

        return time;
    }

    KVTransaction(KVStore<String, byte[]> store) {
        this.startTS = this.VirtualOracleTimestamp();
        this.store = store;
    }

    boolean hasWriteRecordAfterStartTimestamp(Write w) {
        // Inefficient implementation
        LinkedHashMap<String, byte[]> scans = store.prefixScans(w.GetScanPrefix(CF.WRITE));

        Set<String> keys = scans.keySet();

        if (keys.size() == 0) {
            return false;
        }

        // start_ts ~ max
        for (String k : keys) {
            RetDecodeKey rdk = w.DecodeKey(bytes(k));

            if (rdk.ts >= this.startTS) {
                return false;
            }
        }

        return true;
    }

    boolean hasAnotherLockExists(Write w) {
        // Inefficient implementation
        LinkedHashMap<String, byte[]> scans = store.prefixScans(w.GetScanPrefix(CF.LOCK));
        Set<String> keys = scans.keySet();
        return keys.size() != 0;
    }

    // Prewrite tries to lock cell
    boolean PreWrite() {
        for (Write w : this.writes) {
            // check write-write conflict
            // if the transaction sees another write record after its start timestamp, it aborts
            if (this.hasWriteRecordAfterStartTimestamp(w)) {
                // abort
                return false;
            }

            // check has another lock at any timestamp
            // If the transaction sees another lock at any timestamp, it also aborts
            if (this.hasAnotherLockExists(w)) {
                // abort
                return false;
            }
        }

        byte[] primaryKey = writes[0].EncodeKey(CF.LOCK, startTS);

        // no conflict
        // write back 2pc's status to KVService
        for (Write w : this.writes) {
            store.put(asString(w.EncodeKey(CF.DATA, this.startTS)), w.value);
            store.put(asString(w.EncodeKey(CF.LOCK, this.startTS)), primaryKey);
        }

        return true;
    }

    boolean Commit() {
        byte[] primaryKey = writes[0].EncodeKey(CF.LOCK, startTS);
        long commitTS = this.VirtualOracleTimestamp();

        // check primary key lock
        if (store.get(asString(primaryKey)) == null) {
            // aborted while working
            return false;
        }

        byte[] bytesStartTS = Longs.toByteArray(startTS);

        // commit data to storage
        for (Write w : this.writes) {
            // put cf_write:key:commitTS -> value
            store.put(asString(w.EncodeKey(CF.WRITE, commitTS)), bytesStartTS); // commit
            // delete cf_lock_:key:startTS
            store.delete(asString(w.EncodeKey(CF.LOCK, startTS))); // release key's lock
        }

        return true;
    }

    public static class RetDecodeKey {
        byte[] cf;
        byte[] key;
        long ts;
    }

    public static class Write {
        byte[] bytesKey;
        byte[] value;

        public byte[] getCFBytes(CF cf) {
            if (cf == CF.WRITE) {
                return bytes("cf_write");
            }

            if (cf == CF.DATA) {
                return bytes("cf_data_");
            }

            return bytes("cf_lock_");
        }

        public String GetScanPrefix(CF cf) {
            return asString(getCFBytes(cf)) + asString(this.bytesKey);
        }

        public byte[] EncodeKey(CF cf, long ts) {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.write(getCFBytes(cf));
            out.write(bytesKey);
            out.write(Longs.toByteArray(~ts));

            return out.toByteArray();
        }

        public RetDecodeKey DecodeKey(byte[] key) {
            RetDecodeKey ret = new RetDecodeKey();

            byte[] cf = Arrays.copyOfRange(key, 0, bytesCFLen);
            ret.cf = cf;
            byte[] originKey = Arrays.copyOfRange(key, bytesCFLen, key.length - bytesTS);
            ret.key = originKey;
            byte[] bNotTS = Arrays.copyOfRange(key, bytesCFLen + originKey.length, key.length);
            long notTS = Longs.fromByteArray(bNotTS);
            ret.ts = ~notTS;

            return ret;
        }
    }

}
