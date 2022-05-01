package tw.edu.nctu.cs.evoting;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.apache.ratis.protocol.RaftGroup;
import tw.edu.nctu.cs.evoting.storage.KVServerStore;
import tw.edu.nctu.cs.evoting.storage.KVStore;
import tw.edu.nctu.cs.evoting.util.JwtManager;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class AppServer {
    private static final Logger logger = Logger.getLogger(AppServer.class.getName());

    private Server server;

    protected static final Properties properties = new Properties();

    RaftGroup raftGroup;

    int port = -1;

    AppServer(String configPath, RaftGroup raftGroup) {
        Globals.parseConfig(configPath, properties);
        String appServerKey = "app.server.port";
        port = Integer.parseInt(properties.getProperty(appServerKey));
        this.raftGroup = raftGroup;
    }

    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(new EVotingServiceImpl(this.raftGroup))
                .build()
                .start();
        logger.info("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                try {
                    AppServer.this.stop();
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
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
}
