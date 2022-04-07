package tw.edu.nctu.cs.evoting;

import static org.junit.Assert.assertEquals;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.logging.Level;

@RunWith(JUnit4.class)
public class EVotingServerTest {
    private static final Integer temp_votes = 123321;
    private static final String temp_name = "test-name";
    private static final String temp_group = "test-group";
    private static final String temp_auth_token = "test-auth-token";

    /**
     * This rule manages automatic graceful shutdown for the registered servers and channels at the
     * end of test.
     */
    @Rule
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    /**
     * To test the server, make calls with a real stub using the in-process channel, and verify
     * behaviors or state changes from the client side.
     */
    @Test
    public void EVotingServiceImpl_registerVoter() throws Exception {
        // Generate a unique in-process server name.
        String serverName = InProcessServerBuilder.generateName();

        // Create a server, add service, start, and register for automatic graceful shutdown.
        grpcCleanup.register(InProcessServerBuilder
                .forName(serverName).directExecutor().addService(new EVotingServiceImpl()).build().start());

        eVotingGrpc.eVotingBlockingStub blockingStub = eVotingGrpc.newBlockingStub(
                grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build()));

        Voter request = Voter.newBuilder().setName(temp_name).setGroup(temp_group).setPublicKey(ByteString.copyFromUtf8(temp_auth_token)).build();

        Status response = blockingStub.registerVoter(request);

        assertEquals(0, response.getCode());
        Assert.assertArrayEquals(request.toByteArray(), Globals.store.get(temp_name));

        // Voter with the same name already exists
        Status response1 = blockingStub.registerVoter(request);
        assertEquals(1, response1.getCode());
    }

    @Test
    public void RVotingServiceImpl_preAuth() throws Exception {
        // Generate a unique in-process server name.
        String serverName = InProcessServerBuilder.generateName();

        // Create a server, add service, start, and register for automatic graceful shutdown.
        grpcCleanup.register(InProcessServerBuilder
                .forName(serverName).directExecutor().addService(new EVotingServiceImpl()).build().start());

        eVotingGrpc.eVotingBlockingStub blockingStub = eVotingGrpc.newBlockingStub(
                grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build()));

        VoterName request = VoterName.newBuilder().setName(temp_name).build();

        Challenge challenge = blockingStub.preAuth(request);

        assertEquals(16, challenge.getValue().toByteArray().length);
    }
}
