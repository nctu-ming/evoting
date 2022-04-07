package tw.edu.nctu.cs.evoting;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import tw.edu.nctu.cs.evoting.storage.ConcurrentHashMapKVStore;
import tw.edu.nctu.cs.evoting.storage.KVStore;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Server that manages startup/shutdown of a {@code EVoting} server.
 */
public class EVotingServer {
    private static final Logger logger = Logger.getLogger(EVotingServer.class.getName());

    final protected KVStore<String, byte[]> store;

    private Server server;

    EVotingServer() {
        this.store = new ConcurrentHashMapKVStore();
    }

    private void start() throws IOException {
        /* The port on which the server should run */
        int port = 50051;
        server = ServerBuilder.forPort(port)
                .addService(new EVotingServiceImpl())
                .build()
                .start();
        logger.info("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                try {
                    EVotingServer.this.stop();
                } catch (InterruptedException e) {
                    e.printStackTrace(System.err);
                }
                System.err.println("*** server shut down");
            }
        });
    }

    private void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Main launches the server from the command line.
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        final EVotingServer server = new EVotingServer();
        server.start();
        server.blockUntilShutdown();
    }
}