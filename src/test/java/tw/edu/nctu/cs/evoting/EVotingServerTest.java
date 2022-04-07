package tw.edu.nctu.cs.evoting;

import static org.junit.Assert.assertEquals;

import com.google.protobuf.ByteString;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class EVotingServerTest {
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
    public void eVotingImpl_registerVoter() throws Exception {
        // Generate a unique in-process server name.
        String serverName = InProcessServerBuilder.generateName();

        // Create a server, add service, start, and register for automatic graceful shutdown.
        grpcCleanup.register(InProcessServerBuilder
                .forName(serverName).directExecutor().addService(new EVotingServer.EVotingImpl()).build().start());

        eVotingGrpc.eVotingBlockingStub blockingStub = eVotingGrpc.newBlockingStub(
                grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build()));

        Voter request = Voter.newBuilder().setName("test-name").setGroup("test-group").setPublicKey(ByteString.copyFromUtf8("test-public-key")).build();

        Status response = blockingStub.registerVoter(request);

        assertEquals(0, response.getCode());
    }

}
