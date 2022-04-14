package tw.edu.nctu.cs.evoting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.*;
import static tw.edu.nctu.cs.evoting.Globals.store;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import com.goterl.lazysodium.LazySodiumJava;
import com.goterl.lazysodium.SodiumJava;
import com.goterl.lazysodium.exceptions.SodiumException;
import com.goterl.lazysodium.utils.Key;
import com.goterl.lazysodium.utils.KeyPair;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.edu.nctu.cs.evoting.dao.UserDao;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

@RunWith(JUnit4.class)
@ExtendWith(MockitoExtension.class)
public class EVotingClientTest {
    @Rule
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();
    LazySodiumJava lazySodium = new LazySodiumJava(new SodiumJava());
    private final UserDao userDao = new UserDao(store);


    private final eVotingGrpc.eVotingImplBase serviceImpl =
            mock(eVotingGrpc.eVotingImplBase.class, delegatesTo(
                    new eVotingGrpc.eVotingImplBase() {
                        @Override
                        public void registerVoter(Voter request, StreamObserver<Status> responseObserver) {
                            if (!request.isInitialized()) {
                                Status response = Status.newBuilder().setCode(2).build();
                                responseObserver.onNext(response);
                                responseObserver.onCompleted();
                                return;
                            }

                            String userName = request.getName();

                            // Voter with the same name already exists.
                            if (userDao.getUser(userName) != null) {
                                Status response = Status.newBuilder().setCode(1).build();
                                responseObserver.onNext(response);
                                responseObserver.onCompleted();
                                return;
                            }

                            userDao.insertUser(userName, request.toByteArray());

                            Status response = Status.newBuilder().setCode(0).build();
                            responseObserver.onNext(response);
                            responseObserver.onCompleted();
                        }
                        @Override
                        public void unregisterVoter(VoterName request, StreamObserver<Status> responseObserver) {
                            if (!request.isInitialized()) {
                                Status response = Status.newBuilder().setCode(2).build();
                                responseObserver.onNext(response);
                                responseObserver.onCompleted();
                                return;
                            }

                            // User does not exist
                            if (userDao.getUser(request.getName()) == null) {
                                Status response = Status.newBuilder().setCode(1).build();
                                responseObserver.onNext(response);
                                responseObserver.onCompleted();
                                return;
                            }

                            userDao.removeUser(request.getName());

                            Status response = Status.newBuilder().setCode(0).build();
                            responseObserver.onNext(response);
                            responseObserver.onCompleted();
                        }
                        @Override
                        public void preAuth(VoterName request, StreamObserver<Challenge> responseObserver) {
                            byte[] randomBytes = lazySodium.randomBytesBuf(16);

                            Challenge response = Challenge.newBuilder().setValue(ByteString.copyFrom(randomBytes)).build();

                            store.put("user_challenge_" + request.getName().toString(), randomBytes);

                            responseObserver.onNext(response);
                            responseObserver.onCompleted();
                        }

                        @Override
                        public void auth(AuthRequest request, StreamObserver<AuthToken> responseObserver) {
                            String userName = request.getName().getName();

                            byte[] voterInfoBytes = userDao.getUser(userName);

                            Voter voter = null;

                            try {
                                voter = Voter.parseFrom(voterInfoBytes);
                            } catch (Exception e) {
                                AuthToken response = AuthToken.newBuilder().setValue(ByteString.copyFromUtf8("verification failed")).build();
                                responseObserver.onNext(response);
                                responseObserver.onCompleted();
                                return;
                            }

                            byte[] challengeBytes = store.get("user_challenge_" + userName);
                            byte[] signatureBytes = request.getResponse().getValue().toByteArray();

                            if (!lazySodium.cryptoSignVerifyDetached(signatureBytes, challengeBytes, challengeBytes.length, voter.getPublicKey().toByteArray())) {
                                AuthToken response = AuthToken.newBuilder().setValue(ByteString.copyFromUtf8("verification failed")).build();
                                responseObserver.onNext(response);
                                responseObserver.onCompleted();
                                return;
                            }

                            String authToken = Globals.jwtManager.nextToken(userName, voter.getGroup());

                            AuthToken response = AuthToken.newBuilder().setValue(ByteString.copyFromUtf8(authToken)).build();
                            responseObserver.onNext(response);
                            responseObserver.onCompleted();
                        }

                        @Override
                        public void createElection(Election request, StreamObserver<Status> responseObserver) {
                            String authToken = request.getToken().getValue().toStringUtf8();
                            if(!Globals.jwtManager.validateToken(authToken)) {
                                // Invalid authentication token
                                Status response = Status.newBuilder().setCode(1).build();
                                responseObserver.onNext(response);
                                responseObserver.onCompleted();
                                return;
                            }

                            if(request.getChoicesList().size() == 0 || request.getGroupsList().size() == 0) {
                                // Missing groups or choices specification (at least one group and one choice should be listed for the election)
                                Status response = Status.newBuilder().setCode(2).build();
                                responseObserver.onNext(response);
                                responseObserver.onCompleted();
                                return;
                            }

                            Instant endDate = Instant.ofEpochSecond(request.getEndDate().getSeconds(), request.getEndDate().getNanos());
                            if(endDate.isBefore(Instant.now())) {
                                // The election end date is in the past
                                Status response = Status.newBuilder().setCode(3).build();
                                responseObserver.onNext(response);
                                responseObserver.onCompleted();
                                return;
                            }

                            String electionName = request.getName();

                            if (store.get("election_" + electionName) != null) {
                                // duplicate elections
                                Status response = Status.newBuilder().setCode(3).build();
                                responseObserver.onNext(response);
                                responseObserver.onCompleted();
                                return;
                            }

                            ElectionData electionData = ElectionData.newBuilder().setName(request.getName()).addAllChoices(request.getChoicesList()).addAllGroups(request.getGroupsList()).setEndDate(request.getEndDate()).setStatus(ElectionData.Status.ONGOING).build();

                            store.put("election_" + electionName, electionData.toByteArray());

                            Status response = Status.newBuilder().setCode(0).build();

                            responseObserver.onNext(response);
                            responseObserver.onCompleted();
                        }

                        @Override
                        public void castVote(Vote request, StreamObserver<Status> responseObserver) {
                            String authToken = request.getToken().getValue().toStringUtf8();
                            DecodedJWT dJwt = Globals.jwtManager.decodedJWT(authToken);
                            if(dJwt == null) {
                                // Invalid authentication token
                                Status response = Status.newBuilder().setCode(1).build();
                                responseObserver.onNext(response);
                                responseObserver.onCompleted();
                                return;
                            }

                            // TODO: Validate
                            String userName = dJwt.getClaim("username").asString();
                            String userGroup = dJwt.getClaim("user_group").asString();

                            byte[] electionBytes = store.get("election_" + request.getElectionName());

                            ElectionData electionData;
                            try {
                                electionData = ElectionData.parseFrom(electionBytes);
                            } catch (Exception e) {
                                Status response = Status.newBuilder().setCode(2).build();
                                responseObserver.onNext(response);
                                responseObserver.onCompleted();
                                return;
                            }

                            if (!electionData.getGroupsList().contains(userGroup)) {
                                // The voter’s group is not allowed in the election
                                Status response = Status.newBuilder().setCode(3).build();
                                responseObserver.onNext(response);
                                responseObserver.onCompleted();
                                return;
                            }

                            Instant endDate = Instant.ofEpochSecond(electionData.getEndDate().getSeconds(), electionData.getEndDate().getNanos());
                            if (endDate.isBefore(Instant.now())) {
                                // The election end date is in the past
                                Status response = Status.newBuilder().setCode(2).build();
                                responseObserver.onNext(response);
                                responseObserver.onCompleted();
                                return;
                            }

                            StringJoiner joiner = new StringJoiner("_");
                            String key = joiner.add("vote").add(request.getElectionName()).add(request.getChoiceName()).add(userName).toString(); // vote_electionName_choiceName_userName

                            if (store.get(key) != null) {
                                // A previous vote has been cast.
                                Status response = Status.newBuilder().setCode(4).build();
                                responseObserver.onNext(response);
                                responseObserver.onCompleted();
                                return;
                            }

                            store.put(key, new byte[0]);

                            Status response = Status.newBuilder().setCode(0).build();
                            responseObserver.onNext(response);
                            responseObserver.onCompleted();
                        }

                        @Override
                        public void getResult(ElectionName request, StreamObserver<ElectionResult> responseObserver) {
                            String electionName = request.getName();

                            byte[] electionBytes = store.get("election_" + electionName);

                            ElectionData electionData;
                            try {
                                electionData = ElectionData.parseFrom(electionBytes);
                            } catch (Exception e) {
                                ElectionResult response = ElectionResult.newBuilder().setStatus(1).build();
                                responseObserver.onNext(response);
                                responseObserver.onCompleted();
                                return;
                            }

                            Instant endDate = Instant.ofEpochSecond(electionData.getEndDate().getSeconds(), electionData.getEndDate().getNanos());
                            if (!endDate.isBefore(Instant.now())) {
                                // The election is still ongoing.
                                ElectionResult response = ElectionResult.newBuilder().setStatus(2).build();
                                responseObserver.onNext(response);
                                responseObserver.onCompleted();
                                return;
                            }

                            // Poll Opening (counting votes)
                            // TODO: Cache the opening result.
                            LinkedHashMap<String, Integer> resultMap = new LinkedHashMap<>();
                            for (String choice : electionData.getChoicesList()) {
                                StringJoiner joiner = new StringJoiner("_");
                                String prefix = joiner.add("vote").add(electionName).add(choice).toString(); // vote_electionName_choiceName_userName
                                int count = store.prefixScans(prefix).size();
                                resultMap.put(choice, count);
                            }

                            ElectionResult.Builder erBuilder = ElectionResult.newBuilder();
                            for (Map.Entry<String, Integer> entry: resultMap.entrySet()) {
                                VoteCount.Builder vcBuilder = VoteCount.newBuilder();
                                vcBuilder.setChoiceName(entry.getKey());
                                vcBuilder.setCount(entry.getValue());

                                erBuilder.addCount(vcBuilder.build());
                            }

                            erBuilder.setStatus(0);
                            ElectionResult response = erBuilder.build();
                            responseObserver.onNext(response);
                            responseObserver.onCompleted();
                        }
                    }));


    private EVotingClient client;
    int testVoterNum = client.testVoterNum;
    String[] testVoterNames = new String[testVoterNum];
    String[] testVoterGroups = new String[testVoterNum];
    KeyPair[] kp = new KeyPair[testVoterNum];
    Key[] pubKeys = new Key[testVoterNum];
    Key[] priKeys = new Key[testVoterNum];

    @Before
    public void setUp() throws Exception {
        // Generate a unique in-process server name.
        String serverName = InProcessServerBuilder.generateName();

        // Create a server, add service, start, and register for automatic graceful shutdown.
        grpcCleanup.register(InProcessServerBuilder
                .forName(serverName).directExecutor().addService(serviceImpl).build().start());

        // Create a client channel and register for automatic graceful shutdown.
        ManagedChannel channel = grpcCleanup.register(
                InProcessChannelBuilder.forName(serverName).directExecutor().build());

        // Create a HelloWorldClient using the in-process channel;
        client = new EVotingClient(channel);

        // create test voters
        testVoterNames = new String[testVoterNum];
        testVoterGroups = new String[testVoterNum];
        kp = new KeyPair[testVoterNum];
        pubKeys = new Key[testVoterNum];
        priKeys = new Key[testVoterNum];
        for (int i = 0; i<testVoterNum; i++){
            testVoterNames[i] = "user_" + i;
            if(i%2 == 0){
                testVoterGroups[i] = "A";
            }
            else{
                testVoterGroups[i] = "B";
            }
            kp[i] = lazySodium.cryptoSignKeypair();
            pubKeys[i] = kp[i].getPublicKey();
            priKeys[i] = kp[i].getSecretKey();
            if (!lazySodium.cryptoSignKeypair(pubKeys[i].getAsBytes(), priKeys[i].getAsBytes())) {
                throw new SodiumException("Could not generate a signing keypair.");
            }
        }
        client.testVoterNames = testVoterNames;
        client.testVoterGroups = testVoterGroups;
        client.pubKeys = pubKeys;
        client.priKeys = priKeys;
    }

    /**
     * To test the client, call from the client against the fake server, and verify behaviors or state
     * changes from the server side.
     */
    @Test
    public void RegisterVoter() {
        // test registerVoter
        Status status = client.RegisterVoter(testVoterNames[0], testVoterGroups[0], pubKeys[0].getAsBytes()); // Status.code=0 : Successful registration
        assertEquals(0, status.getCode());
        status = client.RegisterVoter(testVoterNames[0], testVoterGroups[0], pubKeys[0].getAsBytes()); // Status.code=1 : Voter with the same name already exists
        assertEquals(1, status.getCode());

        client.UnregisterVoter(testVoterNames[0]);
    }
    @Test
    public void unregisterVoter() {
        // test unregisterVoter
        client.RegisterVoter(testVoterNames[0], testVoterGroups[0], pubKeys[0].getAsBytes());
        Status status = client.UnregisterVoter(testVoterNames[0]); // Status.code=0 : Successful unregistration
        assertEquals(0, status.getCode());
        status = client.UnregisterVoter(testVoterNames[0]); // Status.code=1 : No voter with the name exists on the server
        assertEquals(1, status.getCode());
    }
    @Test
    public void PreAuthAndAuth() {
        client.changeTestVoter(0);
        Challenge challenge = client.PreAuth("nobody"); // Challenge.value={Challenge}
        AuthToken token = client.Auth("nobody", challenge); // verification failed: No voter with the name exists on the server
        assertEquals("verification failed", token.getValue().toStringUtf8());

        client.RegisterVoter(testVoterNames[0], testVoterGroups[0], pubKeys[0].getAsBytes());
        challenge = client.PreAuth(testVoterNames[0]); // Challenge.value={Challenge}
        token = client.Auth(testVoterNames[0], challenge); // authToken.value={token} : Successful verification
        assertNotEquals("verification failed", token.getValue().toStringUtf8());

        client.changeTestVoter(1);
        challenge = client.PreAuth(testVoterNames[0]); // Challenge.value={Challenge}
        token = client.Auth(testVoterNames[0], challenge); // verification failed: Wrong secret key
        assertEquals("verification failed", token.getValue().toStringUtf8());

        client.UnregisterVoter(testVoterNames[0]);
    }

    @Test
    public void CreateElection() {
        // test CreateElection
        String[] AGroups = {"A"};
        String[] choices = {"c1", "c2", "c3"};
        Instant instant = Instant.now().plusSeconds(5);
        Timestamp end_date = Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
        client.RegisterVoter(testVoterNames[0], testVoterGroups[0], pubKeys[0].getAsBytes());
        client.RegisterVoter(testVoterNames[1], testVoterGroups[1], pubKeys[1].getAsBytes());

        client.auth_tokens[0] = new byte[]{'T', 'E', 'S', 'T'};
        client.changeTestVoter(0);
        Status status = client.CreateElection("e0", AGroups, choices, end_date); // Status.code=1 : Invalid authentication token
        assertEquals(1, status.getCode());

        client.preAuthAndAuthTestVoter(client, 0);
        client.changeTestVoter(0);
        status = client.CreateElection("e0", AGroups, choices, end_date); // Status.code=0 : Election created successfully
        assertEquals(0, status.getCode());

        status = client.CreateElection("e0", AGroups, choices, end_date); // Status.code=3 : Unknown error (the same voter creates an election with a duplicate election name)
        assertEquals(3, status.getCode());

        client.preAuthAndAuthTestVoter(client, 1);
        client.changeTestVoter(1);
        status = client.CreateElection("e0", AGroups, choices, end_date); // Status.code=3 : Unknown error (the different voter creates an election with a duplicate election name)
        assertEquals(3, status.getCode());

        status = client.CreateElection("e2", new String[]{}, new String[]{}, end_date); // Status.code=2 : Missing groups or choices specification
        assertEquals(2, status.getCode());

        client.UnregisterVoter(testVoterNames[0]);
        client.UnregisterVoter(testVoterNames[1]);
    }

    @Test
    public void castVote() throws InterruptedException {
        // test CastVote
        String[] AGroups = {"A"};
        String[] choices = {"c1", "c2", "c3"};
        Instant instant = Instant.now().plusSeconds(3);
        Timestamp end_date = Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();

        client.auth_tokens[0] = new byte[]{'T', 'E', 'S', 'T'};
        client.changeTestVoter(0);
        Status status = client.CastVote("e1", "c1"); // Status.code=1 : Invalid authentication token
        assertEquals(1, status.getCode());

        client.RegisterVoter(testVoterNames[0], testVoterGroups[0], pubKeys[0].getAsBytes());
        client.preAuthAndAuthTestVoter(client, 0);
        client.changeTestVoter(0);
        client.CreateElection("e1", AGroups, choices, end_date);

        status = client.CastVote("no_election", "c1"); // Status.code=2 : Invalid election name (No election with the name exists on the server)
        assertEquals(2, status.getCode());

        status = client.CastVote("e1", "c1"); // Status.code=0 : Successful vote
        assertEquals(0, status.getCode());
        status = client.CastVote("e1", "c1"); // Status.code=4 : A previous vote has been cast twice
        assertEquals(4, status.getCode());
        status = client.CastVote("e1", "c2"); // Status.code=0 : Successful vote
        assertEquals(0, status.getCode());

        client.RegisterVoter(testVoterNames[1], testVoterGroups[1], pubKeys[1].getAsBytes());
        client.preAuthAndAuthTestVoter(client, 1);
        client.changeTestVoter(1);
        status = client.CastVote("e1", "c1"); // Status.code=3 : The voter’s group is not allowed in the election
        assertEquals(3, status.getCode());

        Thread.sleep(3000); // After the end date
        client.changeTestVoter(0);
        status = client.CastVote("e1", "c3"); // Status.code=2 : Invalid election name (Voting time has ended)
        assertEquals(2, status.getCode());

        client.UnregisterVoter(testVoterNames[0]);
        client.UnregisterVoter(testVoterNames[1]);
    }

    @Test
    public void getResult() throws InterruptedException {
        // test getResult
        String[] Groups = {"A", "B"};
        String[] choices = {"c1", "c2", "c3"};
        Instant instant = Instant.now().plusSeconds(3);
        Timestamp end_date = Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();

        client.RegisterVoter(testVoterNames[0], testVoterGroups[0], pubKeys[0].getAsBytes());
        client.preAuthAndAuthTestVoter(client, 0);
        client.changeTestVoter(0);
        client.CreateElection("e2", Groups, choices, end_date);
        client.CastVote("e2", "c1");
        client.CastVote("e2", "c2");
        client.RegisterVoter(testVoterNames[1], testVoterGroups[1], pubKeys[1].getAsBytes());
        client.preAuthAndAuthTestVoter(client, 1);
        client.changeTestVoter(1);
        client.CastVote("e2", "c1");

        ElectionResult result = client.GetResult("no_election"); // ElectionResult.status = 1: Non-existent election
        assertEquals(1, result.getStatus());

        result = client.GetResult("e2"); // ElectionResult.status=2: The election is still ongoing. Election result is not available yet
        assertEquals(2, result.getStatus());

        Thread.sleep(3000); // After the end date
        result = client.GetResult("e2"); // ElectionResult.status = 0
        assertEquals(0, result.getStatus());
        VoteCount c1 = VoteCount.newBuilder().setChoiceName("c1").setCount(2).build();
        VoteCount c2 = VoteCount.newBuilder().setChoiceName("c2").setCount(1).build();
        VoteCount c3 = VoteCount.newBuilder().setChoiceName("c3").setCount(0).build();
        assertEquals(c1, result.getCount(0));
        assertEquals(c2, result.getCount(1));
        assertEquals(c3, result.getCount(2));

        client.UnregisterVoter(testVoterNames[0]);
        client.UnregisterVoter(testVoterNames[1]);
    }
}
