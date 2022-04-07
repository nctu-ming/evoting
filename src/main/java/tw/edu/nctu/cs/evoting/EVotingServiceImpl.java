package tw.edu.nctu.cs.evoting;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;

import java.util.logging.Logger;

class EVotingServiceImpl extends eVotingGrpc.eVotingImplBase {
    private static final Logger logger = Logger.getLogger(EVotingServiceImpl.class.getName());

    private static final Integer temp_votes = 123321;
    private static final String temp_name = "Ming Wang";
    private static final String temp_auth_token = "test-auth-token";

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
        if (Globals.store.get(userName) != null) {
            Status response = Status.newBuilder().setCode(1).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            return;
        }

        Globals.store.put(userName, request.toByteArray());

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
        Challenge response = Challenge.newBuilder().setValue(ByteString.copyFromUtf8(temp_auth_token)).build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void auth(AuthRequest request, StreamObserver<AuthToken> responseObserver) {
        AuthToken response = AuthToken.newBuilder().setValue(ByteString.copyFromUtf8(temp_auth_token)).build();

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