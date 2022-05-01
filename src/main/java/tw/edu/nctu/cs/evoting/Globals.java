package tw.edu.nctu.cs.evoting;

import org.apache.ratis.protocol.RaftGroup;
import tw.edu.nctu.cs.evoting.storage.KVServerStore;
import tw.edu.nctu.cs.evoting.storage.KVStore;
import tw.edu.nctu.cs.evoting.storage.LevelDBKVStore;
import tw.edu.nctu.cs.evoting.util.JwtManager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;

public class Globals {
    public static void parseConfig(String confPath, Properties properties) {
        try(InputStream inputStream = new FileInputStream(confPath);
            Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            BufferedReader bufferedReader = new BufferedReader(reader)) {
            properties.load(bufferedReader);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + confPath, e);
        }
    }
}
