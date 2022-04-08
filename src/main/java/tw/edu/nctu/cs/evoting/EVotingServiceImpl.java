package tw.edu.nctu.cs.evoting;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.protobuf.ByteString;
import com.goterl.lazysodium.LazySodiumJava;
import com.goterl.lazysodium.SodiumJava;
import io.grpc.stub.StreamObserver;
import tw.edu.nctu.cs.evoting.dao.UserDao;

import java.time.Instant;
import java.util.Objects;
import java.util.logging.Logger;

class EVotingServiceImpl extends eVotingGrpc.eVotingImplBase {
    private static final Logger logger = Logger.getLogger(EVotingServiceImpl.class.getName());
    LazySodiumJava lazySodium = new LazySodiumJava(new SodiumJava());

    private final UserDao userDao = new UserDao(Globals.store);

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
        Status response = Status.newBuilder().setCode(200).build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void preAuth(VoterName request, StreamObserver<Challenge> responseObserver) {
        byte[] randomBytes = lazySodium.randomBytesBuf(16);

        Challenge response = Challenge.newBuilder().setValue(ByteString.copyFrom(randomBytes)).build();

        Globals.store.put("user_challenge_" + request.getName().toString(), randomBytes);

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
            logger.info(e.toString()); // TODO: Refactor
            AuthToken response = AuthToken.newBuilder().setValue(ByteString.copyFromUtf8("verification failed")).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            return;
        }

        byte[] challengeBytes = Globals.store.get("user_challenge_" + userName);
        byte[] signatureBytes = request.getResponse().getValue().toByteArray();

        if (!lazySodium.cryptoSignVerifyDetached(signatureBytes, challengeBytes, challengeBytes.length, voter.getPublicKey().toByteArray())) {
            AuthToken response = AuthToken.newBuilder().setValue(ByteString.copyFromUtf8("verification failed")).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            return;
        }

        String authToken = Globals.jwtManager.nextToken(userName);

        AuthToken response = AuthToken.newBuilder().setValue(ByteString.copyFromUtf8(authToken)).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void createElection(Election request, StreamObserver<Status> responseObserver) {
        String jwtToken = request.getToken().toString();
        if(!Globals.jwtManager.validateToken(jwtToken)) {
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

        // TODO: Check for duplicate elections

        String electionName = request.getName();
        Globals.store.put("election_" + electionName, request.toByteArray());

        Status response = Status.newBuilder().setCode(0).build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void castVote(Vote request, StreamObserver<Status> responseObserver) {
        Status response = Status.newBuilder().setCode(200).build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getResult(ElectionName request, StreamObserver<ElectionResult> responseObserver) {
        ElectionResult.Builder resultBuilder = ElectionResult.newBuilder().setStatus(200);

        VoteCount.Builder vcBuilder = VoteCount.newBuilder().setCount(123321).setChoiceName("Ming Wang").setToken(
                AuthToken.newBuilder().setValue(ByteString.copyFromUtf8("test-auth-token"))
        );
        resultBuilder.addCount(vcBuilder);

        ElectionResult response = resultBuilder.build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}