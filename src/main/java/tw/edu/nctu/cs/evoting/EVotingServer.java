package tw.edu.nctu.cs.evoting;

import com.google.protobuf.ByteString;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Server that manages startup/shutdown of a {@code EVoting} server.
 */
public class EVotingServer {
    private static final Logger logger = Logger.getLogger(EVotingServer.class.getName());

    private static final Integer temp_votes = 123321;
    private static final String temp_name = "Ming Wang";
    private static final String temp_auth_token = "test-auth-token";

    private Server server;

    private void start() throws IOException {
        /* The port on which the server should run */
        int port = 50051;
        server = ServerBuilder.forPort(port)
                .addService(new EVotingImpl())
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

    static class EVotingImpl extends eVotingGrpc.eVotingImplBase {
        @Override
        public void getResult(ElectionName request, StreamObserver<ElectionResult> responseObserver) {
            ElectionResult.Builder resultBuilder = ElectionResult.newBuilder();
            resultBuilder.setStatus(200);

            VoteCount.Builder vcBuilder = VoteCount.newBuilder().setCount(temp_votes).setChoiceName(temp_name);
            vcBuilder.setToken(
                    AuthToken.newBuilder().setValue(ByteString.copyFromUtf8(temp_auth_token))
            );
            resultBuilder.addCount(vcBuilder);

            ElectionResult result = resultBuilder.build();

            responseObserver.onNext(result);
            responseObserver.onCompleted();
        }
    }
}