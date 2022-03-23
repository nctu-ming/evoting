package tw.edu.nctu.cs.evoting;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple client that requests a EVoting from the {@link EVotingServer}.
 */
public class EVotingClient {
    private static final Logger logger = Logger.getLogger(EVotingClient.class.getName());

    private static String tem_name = "test-name";
    private static String tem_group = "test-group";
    private static final String temp_auth_token = "test-auth-token";

    private final eVotingGrpc.eVotingBlockingStub blockingStub;

    /** Construct client for accessing HelloWorld server using the existing channel. */
    public EVotingClient(Channel channel) {
        // 'channel' here is a Channel, not a ManagedChannel, so it is not this code's responsibility to
        // shut it down.

        // Passing Channels to code makes code easier to test and makes it easier to reuse Channels.
        blockingStub = eVotingGrpc.newBlockingStub(channel);
    }

    /** get challenge from server. */
    public void PreAuth(String name) {
        logger.info("Will try to PreAuth " + name + " ...");
        VoterName request = VoterName.newBuilder().setName(name).build();
        Challenge challenge;
        try {
            challenge = blockingStub.preAuth(request);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return;
        }
        logger.info("Challenge: " + challenge.getValue());
    }

    /** Authenticate from server. */
    public void Auth(String name, String value) {
        logger.info("Authenticate...");
        VoterName voterName = VoterName.newBuilder().setName(name).build();
        Response res = Response.newBuilder().setValue(ByteString.copyFromUtf8(temp_auth_token)).build();
        AuthRequest request = AuthRequest.newBuilder().setName(voterName).setResponse(res).build();
        AuthToken token;
        try {
            token = blockingStub.auth(request);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return;
        }
        logger.info("Token value: " + token.getValue());
    }

    /** Create election from server. */
    public void CreateElection(String name, String group, String choices, Timestamp date) {
        logger.info("CreateElection...");
        AuthToken token = AuthToken.newBuilder().setValue(ByteString.copyFromUtf8(temp_auth_token)).build();
//        Election election = Election.newBuilder().setName(name).setGroups(0, group).setChoices(0, choices).setEndDate(date).setToken(token).build();
        Election election = Election.newBuilder().setName(name).setEndDate(date).setToken(token).build();
        ElectionStatus status;

        try {
            status = blockingStub.createElection(election);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return;
        }
        logger.info("Election Status: " + status.getCode());
    }

    /** Cast vote from server. */
    public void CastVote(String election_name, String choice_name) {
        logger.info("CastVote...");
        AuthToken token = AuthToken.newBuilder().setValue(ByteString.copyFromUtf8(temp_auth_token)).build();
        Vote vote = Vote.newBuilder().setElectionName(election_name).setChoiceName(choice_name).setToken(token).build();
        Status status;

        try {
            status = blockingStub.castVote(vote);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return;
        }
        logger.info("Vote Status: " + status.getCode());
    }

    /** Get result from server. */
    public void GetResult(String election_name) {
        logger.info("GetResult...");
        ElectionName vote = ElectionName.newBuilder().setName(election_name).build();
        ElectionResult result;

        try {
            result = blockingStub.getResult(vote);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return;
        }
        logger.info("Result Status: " + result.getStatus());
        logger.info("Choice Name: " + result.getCount(0).getChoiceName());
    }

    /**
     * eVoting server. If provided, the first element of {@code args} is the name to use in the
     * PreAuth. The second argument is the target server.
     */
    public static void main(String[] args) throws Exception {
        // Access a service running on the local machine on port 50051
        String target = "localhost:50051";
        // Allow passing in the user and target strings as command line arguments
        if (args.length > 0) {
            if ("--help".equals(args[0])) {
                System.err.println("Usage: [name [target]]");
                System.err.println("");
                System.err.println("  name    Your name. Defaults to " + tem_name);
                System.err.println("  target  The server to connect to. Defaults to " + target);
                System.exit(1);
            }
            tem_name = args[0];
        }
        if (args.length > 1) {
            target = args[1];
        }

        // Create a communication channel to the server, known as a Channel. Channels are thread-safe
        // and reusable. It is common to create channels at the beginning of your application and reuse
        // them until the application shuts down.
        ManagedChannel channel = ManagedChannelBuilder.forTarget(target)
                // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
                // needing certificates.
                .usePlaintext()
                .build();
        try {
            EVotingClient client = new EVotingClient(channel);
            client.PreAuth(tem_name);
            client.Auth(tem_name, temp_auth_token);
            Timestamp end_date = Timestamp.newBuilder().setSeconds(123).setNanos(123).build();
            client.CreateElection(tem_name, tem_group, "1", end_date);
            client.CastVote(tem_name, "1");
            client.GetResult(tem_name);
        } finally {
            // ManagedChannels use resources like threads and TCP connections. To prevent leaking these
            // resources the channel should be shut down when it will no longer be used. If it may be used
            // again leave it running.
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
