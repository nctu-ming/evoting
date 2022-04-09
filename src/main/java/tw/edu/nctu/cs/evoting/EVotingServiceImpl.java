package tw.edu.nctu.cs.evoting;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.protobuf.ByteString;
import com.goterl.lazysodium.LazySodiumJava;
import com.goterl.lazysodium.SodiumJava;
import io.grpc.stub.StreamObserver;
import org.slf4j.LoggerFactory;
import tw.edu.nctu.cs.evoting.dao.UserDao;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

class EVotingServiceImpl extends eVotingGrpc.eVotingImplBase {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(EVotingServiceImpl.class);

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
            logger.error("Voter.parseFrom", e); // TODO: Refactor
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

        if (Globals.store.get("election_" + electionName) != null) {
            // duplicate elections
            Status response = Status.newBuilder().setCode(3).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            return;
        }

        ElectionData electionData = ElectionData.newBuilder().setName(request.getName()).addAllChoices(request.getChoicesList()).addAllGroups(request.getGroupsList()).setEndDate(request.getEndDate()).setStatus(ElectionData.Status.ONGOING).build();

        Globals.store.put("election_" + electionName, electionData.toByteArray());

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

        byte[] electionBytes = Globals.store.get("election_" + request.getElectionName());

        ElectionData electionData;
        try {
             electionData = ElectionData.parseFrom(electionBytes);
        } catch (Exception e) {
            logger.error("ElectionData.parseFrom", e);
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

        StringJoiner joiner = new StringJoiner("_");
        String key = joiner.add("vote").add(request.getElectionName()).add(request.getChoiceName()).add(userName).toString(); // vote_electionName_choiceName_userName

        if (Globals.store.get(key) != null) {
            // A previous vote has been cast.
            Status response = Status.newBuilder().setCode(4).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            return;
        }

        Globals.store.put(key, new byte[0]);

        Status response = Status.newBuilder().setCode(0).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getResult(ElectionName request, StreamObserver<ElectionResult> responseObserver) {
        String electionName = request.getName();

        byte[] electionBytes = Globals.store.get("election_" + electionName);

        ElectionData electionData;
        try {
            electionData = ElectionData.parseFrom(electionBytes);
        } catch (Exception e) {
            logger.error("ElectionData.parseFrom", e);
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
            int count = Globals.store.prefixScans(prefix).size();
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
}