package tw.edu.nctu.cs.evoting;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

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
