package tw.edu.nctu.cs.evoting;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.protobuf.ByteString;
import com.goterl.lazysodium.LazySodiumJava;
import com.goterl.lazysodium.SodiumJava;
import com.goterl.lazysodium.interfaces.Sign;
import com.goterl.lazysodium.utils.Key;
import com.goterl.lazysodium.utils.KeyPair;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import tw.edu.nctu.cs.evoting.dao.UserDao;

import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

import static org.junit.Assert.*;

//@RunWith(JUnit4.class)
//public class EVotingServerTest {
//    private static final Integer temp_votes = 123321;
//    private static final String temp_name = "test-name";
//    private static final String temp_group = "test-group";
//    private static final String temp_auth_token = "test-auth-token";
//    private final String testValuePrefix = "test-value-";
//
//    private static String genKey(String prefix, String suffix) {
//        return prefix + suffix;
//    }
//
//    private static byte[] getBytes(String prefix, String suffix) {
//        return (prefix + suffix).getBytes(StandardCharsets.UTF_8);
//    }
//
//    /**
//     * This rule manages automatic graceful shutdown for the registered servers and channels at the
//     * end of test.
//     */
//    @Rule
//    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();
//
//    /**
//     * To test the server, make calls with a real stub using the in-process channel, and verify
//     * behaviors or state changes from the client side.
//     */
//    @Test
//    public void EVotingServiceImpl_registerVoter() throws Exception {
//        // Generate a unique in-process server name.
//        String serverName = InProcessServerBuilder.generateName();
//
//        EVotingServiceImpl eVotingService = new EVotingServiceImpl();
//
//        // Create a server, add service, start, and register for automatic graceful shutdown.
//        grpcCleanup.register(InProcessServerBuilder
//                .forName(serverName).directExecutor().addService(eVotingService).build().start());
//
//        eVotingGrpc.eVotingBlockingStub blockingStub = eVotingGrpc.newBlockingStub(
//                grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build()));
//
//        Voter request = Voter.newBuilder().setName(temp_name).setGroup(temp_group).setPublicKey(ByteString.copyFromUtf8(temp_auth_token)).build();
//
//        Status response = blockingStub.registerVoter(request);
//
//        assertEquals(0, response.getCode());
//        Assert.assertArrayEquals(request.toByteArray(), eVotingService.store.get(UserDao.genUserKey(temp_name)));
//
//        // Voter with the same name already exists
//        Status response1 = blockingStub.registerVoter(request);
//        assertEquals(1, response1.getCode());
//
//        eVotingService.store.clear();
//    }
//
//    @Test
//    public void EVotingServiceImpl_unregisterVoter() throws Exception {
//        // Generate a unique in-process server name.
//        String serverName = InProcessServerBuilder.generateName();
//
//        EVotingServiceImpl eVotingService = new EVotingServiceImpl();
//
//        // Create a server, add service, start, and register for automatic graceful shutdown.
//        grpcCleanup.register(InProcessServerBuilder
//                .forName(serverName).directExecutor().addService(eVotingService).build().start());
//
//        eVotingGrpc.eVotingBlockingStub blockingStub = eVotingGrpc.newBlockingStub(
//                grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build()));
//
//        // Put test data to store
//        eVotingService.store.put(UserDao.genUserKey(temp_name), getBytes(testValuePrefix, temp_name));
//
//        VoterName request = VoterName.newBuilder().setName(temp_name).build();
//
//        Status response = blockingStub.unregisterVoter(request);
//
//        assertEquals(0, response.getCode());
//        assertNull(eVotingService.store.get(UserDao.genUserKey(temp_name)));
//
//        Status response2 = blockingStub.unregisterVoter(request);
//        assertEquals(1, response2.getCode());
//
//        eVotingService.store.clear();
//    }
//
//    @Test
//    public void RVotingServiceImpl_preAuth() throws Exception {
//        // Generate a unique in-process server name.
//        String serverName = InProcessServerBuilder.generateName();
//
//        EVotingServiceImpl eVotingService = new EVotingServiceImpl();
//
//        // Create a server, add service, start, and register for automatic graceful shutdown.
//        grpcCleanup.register(InProcessServerBuilder
//                .forName(serverName).directExecutor().addService(eVotingService).build().start());
//
//        eVotingGrpc.eVotingBlockingStub blockingStub = eVotingGrpc.newBlockingStub(
//                grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build()));
//
//        VoterName request = VoterName.newBuilder().setName(temp_name).build();
//
//        Challenge challenge = blockingStub.preAuth(request);
//
//        assertEquals(16, challenge.getValue().toByteArray().length);
//
//        byte[] randomBytes = eVotingService.store.get("user_challenge_" + temp_name);
//        assertArrayEquals(challenge.getValue().toByteArray(), randomBytes);
//
//        eVotingService.store.clear();
//    }
//
//    @Test
//    public void RVotingServiceImpl_auth() throws Exception {
//        // Generate a unique in-process server name.
//        String serverName = InProcessServerBuilder.generateName();
//
//        EVotingServiceImpl eVotingService = new EVotingServiceImpl();
//
//        // Create a server, add service, start, and register for automatic graceful shutdown.
//        grpcCleanup.register(InProcessServerBuilder
//                .forName(serverName).directExecutor().addService(eVotingService).build().start());
//
//        eVotingGrpc.eVotingBlockingStub blockingStub = eVotingGrpc.newBlockingStub(
//                grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build()));
//
//        LazySodiumJava lazySodium = new LazySodiumJava(new SodiumJava());
//
//        KeyPair kp = lazySodium.cryptoSignKeypair();
//        Key pk = kp.getPublicKey();
//        Key sk = kp.getSecretKey();
//        lazySodium.cryptoSignKeypair(pk.getAsBytes(), sk.getAsBytes());
//
//        // insert test data
//        Voter testVoter = Voter.newBuilder().setName(temp_name).setGroup(temp_group).setPublicKey(ByteString.copyFrom(pk.getAsBytes())).build();
//        byte[] testRandomBytes = lazySodium.randomBytesBuf(16);
//        eVotingService.store.put("user_" + temp_name, testVoter.toByteArray());
//        eVotingService.store.put("user_challenge_" + temp_name, testRandomBytes);
//
//        // client crypto_sign_detached
//        byte[] signatureBytes = new byte[Sign.BYTES];
//        lazySodium.cryptoSignDetached(signatureBytes, testRandomBytes, testRandomBytes.length, sk.getAsBytes());
//
//        // gRPC request
//        VoterName voterName = VoterName.newBuilder().setName(temp_name).build();
//        Response res = Response.newBuilder().setValue(ByteString.copyFrom(signatureBytes)).build();
//        AuthRequest request = AuthRequest.newBuilder().setName(voterName).setResponse(res).build();
//
//        AuthToken token = blockingStub.auth(request);
//
//        String authToken = token.getValue().toStringUtf8();
//
//        assertTrue(eVotingService.jwtManager.validateToken(authToken));
//        DecodedJWT decodedJwt = eVotingService.jwtManager.decodedJWT(authToken);
//
//        assertEquals(temp_name, decodedJwt.getClaim("username").asString());
//
//        eVotingService.store.clear();
//    }
//}