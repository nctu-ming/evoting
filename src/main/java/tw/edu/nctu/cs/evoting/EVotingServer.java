package tw.edu.nctu.cs.evoting;

import java.io.*;
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

        // KVService
        final KVService kvServer = new KVService(configPath);
        kvServer.start();

        // VotingService
        final VotingService server = new VotingService(configPath, kvServer.RAFT_GROUP);
        server.start();
        server.blockUntilShutdown();
    }
}