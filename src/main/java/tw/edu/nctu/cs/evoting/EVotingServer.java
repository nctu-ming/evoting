package tw.edu.nctu.cs.evoting;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import tw.edu.nctu.cs.evoting.storage.KVServerStore;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Server that manages startup/shutdown of a {@code EVoting} server.
 */
public class EVotingServer {
    private static final Logger logger = Logger.getLogger(EVotingServer.class.getName());

    /**
     * Main launches the server from the command line.
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 1) {
            logger.info("Please provide a config path eg. ./node1.config");
            System.exit(1);
        }

        String configPath = args[0];

        // KVServer
        final KVServer kvServer = new KVServer(configPath);
        kvServer.start();

        // AppServer
        final AppServer server = new AppServer(configPath, kvServer.RAFT_GROUP);
        server.start();
        server.blockUntilShutdown();
    }
}