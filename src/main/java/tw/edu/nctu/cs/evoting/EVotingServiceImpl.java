package tw.edu.nctu.cs.evoting;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.protobuf.ByteString;
import com.goterl.lazysodium.LazySodium;
import com.goterl.lazysodium.LazySodiumJava;
import com.goterl.lazysodium.Sodium;
import com.goterl.lazysodium.SodiumJava;
import com.goterl.lazysodium.exceptions.SodiumException;
import com.goterl.lazysodium.interfaces.KeyExchange;
import com.goterl.lazysodium.interfaces.Sign;
import com.goterl.lazysodium.utils.Key;
import com.goterl.lazysodium.utils.KeyPair;
import io.grpc.stub.StreamObserver;
import tw.edu.nctu.cs.evoting.dao.UserDao;
import tw.edu.nctu.cs.evoting.util.JwtManager;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.logging.Logger;

class EVotingServiceImpl extends eVotingGrpc.eVotingImplBase {
    private static final Logger logger = Logger.getLogger(EVotingServiceImpl.class.getName());
    LazySodiumJava lazySodium = new LazySodiumJava(new SodiumJava());

    private static final Integer temp_votes = 123321;
    private static final String temp_name = "Ming Wang";
    private static final String temp_auth_token = "test-auth-token";

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
        Challenge response = Challenge.newBuilder().setValue(ByteString.copyFrom(
                lazySodium.randomBytesBuf(16)
        )).build();

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
            AuthToken response = AuthToken.newBuilder().setValue(ByteString.copyFromUtf8("")).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        byte[] challengeBytes = Globals.store.get("user_challenge_" + userName);
        byte[] signatureBytes = request.getResponse().getValue().toByteArray();

        if (!lazySodium.cryptoSignVerifyDetached(signatureBytes, challengeBytes, challengeBytes.length, voter.getPublicKey().toByteArray())) {
            AuthToken response = AuthToken.newBuilder().setValue(ByteString.copyFromUtf8("verification failed")).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        String authToken = Globals.jwtManager.nextToken(userName);

        AuthToken response = AuthToken.newBuilder().setValue(ByteString.copyFromUtf8(authToken)).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void createElection(Election request, StreamObserver<Status> responseObserver) {
        Status response = Status.newBuilder().setCode(200).build();

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

        VoteCount.Builder vcBuilder = VoteCount.newBuilder().setCount(temp_votes).setChoiceName(temp_name).setToken(
                AuthToken.newBuilder().setValue(ByteString.copyFromUtf8(temp_auth_token))
        );
        resultBuilder.addCount(vcBuilder);

        ElectionResult response = resultBuilder.build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}