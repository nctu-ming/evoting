package tw.edu.nctu.cs.evoting;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Scanner;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import io.grpc.*;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.goterl.lazysodium.exceptions.SodiumException;
import com.goterl.lazysodium.LazySodiumJava;
import com.goterl.lazysodium.SodiumJava;
import com.goterl.lazysodium.utils.Key;
import com.goterl.lazysodium.utils.KeyPair;
import com.goterl.lazysodium.interfaces.Sign;

/**
 * A simple client that requests a EVoting from the {@link EVotingServer}.
 */
public class EVotingClient {
    private static final Logger logger = Logger.getLogger(EVotingClient.class.getName());
    static LazySodiumJava lazySodium = new LazySodiumJava(new SodiumJava());

    public static final int testVoterNum = 5;

    private static String name = "TeamB";
    private static byte[] priKey;
    private static byte[] auth_token;
    public static Key[] pubKeys;
    public static Key[] priKeys;
    public static String[] testVoterNames;
    public static String[] testVoterGroups;
    public static final byte[][] auth_tokens = new byte[testVoterNum][];


    private final eVotingGrpc.eVotingBlockingStub blockingStub;

    /** Construct client for accessing HelloWorld server using the existing channel. */
    public EVotingClient(Channel channel) {
        // 'channel' here is a Channel, not a ManagedChannel, so it is not this code's responsibility to
        // shut it down.

        // Passing Channels to code makes code easier to test and makes it easier to reuse Channels.
        blockingStub = eVotingGrpc.newBlockingStub(channel);
    }

    public Status RegisterVoter(String name, String group, byte[] pubKeyBytes) {
        Voter voter = Voter.newBuilder().setName(name).setGroup(group).setPublicKey(ByteString.copyFrom(pubKeyBytes)).build();
        Status status;
        try {
            status = blockingStub.registerVoter(voter);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return null;
        }
        logger.info("RegisterVoter " + name + ", status: " + status.getCode());
        return status;
    }

    public Status UnregisterVoter(String name) {
        VoterName votername = VoterName.newBuilder().setName(name).build();
        Status status;
        try {
            status = blockingStub.unregisterVoter(votername);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return null;
        }
        logger.info("UnregisterVoter " + name + ", status: " + status.getCode());
        return status;
    }

    /** get challenge from server. */
    public Challenge PreAuth(String name) {
        VoterName request = VoterName.newBuilder().setName(name).build();
        Challenge challenge;
        try {
            challenge = blockingStub.preAuth(request);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return null;
        }
        logger.info(name + " PreAuth, Challenge: " + challenge.getValue());
        return challenge;
    }

    /** Authenticate from server. */
    public AuthToken Auth(String name, Challenge challenge) {
        byte[] signatureBytes = new byte[Sign.BYTES];
        lazySodium.cryptoSignDetached(signatureBytes, challenge.getValue().toByteArray(), challenge.getValue().toByteArray().length, priKey);
        VoterName voterName = VoterName.newBuilder().setName(name).build();
        Response res = Response.newBuilder().setValue(ByteString.copyFrom(signatureBytes)).build();
        AuthRequest request = AuthRequest.newBuilder().setName(voterName).setResponse(res).build();
        AuthToken token;
        try {
            token = blockingStub.auth(request);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return null;
        }
        logger.info(name + " Auth, Token value: " + token.getValue());
        return token;
    }

    /** Create election from server. */
    public Status CreateElection(String election_name, String[] group, String[] choices, Timestamp date) {
        AuthToken token = AuthToken.newBuilder().setValue(ByteString.copyFrom(auth_token)).build();
        Election election = Election.newBuilder().setName(election_name).addAllGroups(Arrays.asList(group)).addAllChoices(Arrays.asList(choices)).setEndDate(date).setToken(token).build();
        Status status;

        try {
            status = blockingStub.createElection(election);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return null;
        }
        StringBuilder groupString = new StringBuilder(" {");
        StringBuilder choiceString = new StringBuilder(" {");
        for (String s : group) {
            groupString.append(s).append(", ");
        }
        groupString.append("}");
        for (String choice : choices) {
            choiceString.append(choice).append(", ");
        }
        choiceString.append("}");
        logger.info(name + " CreateElection: " + election_name + groupString + choiceString + " {" + date.getSeconds() + " " + date.getNanos() + "}, Election Status: " + status.getCode());
        return status;
    }

    /** Cast vote from server. */
    public Status CastVote(String election_name, String choice_name) {
        AuthToken token = AuthToken.newBuilder().setValue(ByteString.copyFrom(auth_token)).build();
        Vote vote = Vote.newBuilder().setElectionName(election_name).setChoiceName(choice_name).setToken(token).build();
        Status status;

        try {
            status = blockingStub.castVote(vote);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return null;
        }
        logger.info(name + " CastVote: {" + election_name + ", " + choice_name + "}, Vote Status: " + status.getCode());
        return status;
    }

    /** Get result from server. */
    public ElectionResult GetResult(String election_name) {
        ElectionName vote = ElectionName.newBuilder().setName(election_name).build();
        ElectionResult result;

        try {
            result = blockingStub.getResult(vote);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return null;
        }
        StringBuilder resultString = new StringBuilder("GetResult " + election_name + ", Result Status: " + result.getStatus() + ", Result=");
        for(int i = 0; i < result.getCountList().size(); i++){
            resultString.append("{").append(result.getCount(i).getChoiceName()).append(": ").append(result.getCount(i).getCount()).append("} ");
        }
        logger.info(resultString.toString());
        return result;
    }

    private void printTestInfo(){
        System.out.println("**Test mode**");
        System.out.println("voter: " + name);
        System.out.println("-1: Register all voters");
        System.out.println("-2: Unregister all voters");
        System.out.println("-3: PreAuth & Auth all voters");
        System.out.println("-4: Set the voter index");
        System.out.println("-5: List all voters' info");
    }

    private void printInfo(){
        System.out.println("**Welcome to Election Voting System**");
        System.out.println("1: PreAuth & Auth");
        System.out.println("2: CreateElection");
        System.out.println("3: CastVote");
        System.out.println("4: GetResult");
//        System.out.println("5: Change target");
        System.out.println("6: Exit");
        System.out.print(">");
    }

   public void registerAllVoters(EVotingClient client){
       for(int i = 0; i < testVoterNum; i++){
           client.RegisterVoter(testVoterNames[i] , testVoterGroups[i], pubKeys[i].getAsBytes());
       }
   }

    public void unregisterAllVoters(EVotingClient client){
        for(int i = 0; i < testVoterNum; i++){
            client.UnregisterVoter(testVoterNames[i]);
        }
    }

    public void preAuthAndAuthAllVoters(EVotingClient client){
        byte[] pri_bak = priKey;
        for(int i = 0; i < testVoterNum; i++){
            Challenge challenge = client.PreAuth(testVoterNames[i]);
            priKey = priKeys[i].getAsBytes();
            auth_tokens[i] = client.Auth(testVoterNames[i], challenge).getValue().toByteArray();
        }
        priKey = pri_bak;
    }

    public void preAuthAndAuthTestVoter(EVotingClient client, int index){
        byte[] pri_bak = priKey;
        Challenge challenge = client.PreAuth(testVoterNames[index]);
        priKey = priKeys[index].getAsBytes();
        auth_tokens[index] = client.Auth(testVoterNames[index], challenge).getValue().toByteArray();
        priKey = pri_bak;
    }

    public void changeTestVoter(int voterIndex){
        name = testVoterNames[voterIndex];
        priKey = priKeys[voterIndex].getAsBytes();
        auth_token = auth_tokens[voterIndex];
    }

    /**
     * eVoting server. If provided, the first element of {@code args} is the name to use in the
     * PreAuth. The second argument is the target server.
     */
    public static void main(String[] args) throws Exception {
        boolean testmode = false;
        Scanner scanner = new Scanner(System.in);
        // Access a service running on the local machine on port 50051
//        String target = "localhost:50051";
//        System.out.println("Access a service running on the " + target + "...");

        // Allow passing in the user and target strings as command line arguments
//        if (args.length > 0) {
//            if ("--help".equals(args[0])) {
//                System.err.println("Usage: [name [target]]");
//                System.err.println();
//                System.err.println("  name    Your name. Defaults to " +  name);
//                System.err.println("  target  The server to connect to. Defaults to " + target);
//                System.exit(1);
//            }
//            name = args[0];
//        }
//        if (args.length > 1) {
//            target = args[1];
//        }

        NameResolver.Factory nameResolverFactory = new MultiAddressNameResolverFactory(
                new InetSocketAddress("127.0.0.1", 50050),
                new InetSocketAddress("127.0.0.1", 50051),
                new InetSocketAddress("127.0.0.1", 50052)
        );
        String target = "localhost:50050";

        // Create a communication channel to the server, known as a Channel. Channels are thread-safe
        // and reusable. It is common to create channels at the beginning of your application and reuse
        // them until the application shuts down.

        // connect multiple address
//        ManagedChannel channel = ManagedChannelBuilder.forTarget("service")
//                .nameResolverFactory(nameResolverFactory)
//                .defaultLoadBalancingPolicy("round_robin")
//                .usePlaintext()
//                .build();

        // connect one address
        ManagedChannel channel = ManagedChannelBuilder.forTarget(target)
                .usePlaintext()
                .build();
        try {
            EVotingClient client = new EVotingClient(channel);
            // create test voters
            if(testmode){
                testVoterNames = new String[testVoterNum];
                testVoterGroups = new String[testVoterNum];
                KeyPair[] kp = new KeyPair[testVoterNum];
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
            }
            else{
//                KeyPair KP = lazySodium.cryptoSignKeypair();
//                Key PK = KP.getPublicKey();
//                Key SK = KP.getSecretKey();
//                priKey = SK.getAsBytes();
//                int b64_maxlen = lazySodium.getSodium().sodium_base64_encoded_len(priKey.length, 1);
//                byte[] b64 = new byte[b64_maxlen];
//                String PKbase64 = lazySodium.getSodium().sodium_bin2base64(b64, b64_maxlen, PK.getAsBytes(), PK.getAsBytes().length, 1);
//                String SKbase64 = lazySodium.getSodium().sodium_bin2base64(b64, b64_maxlen, priKey, priKey.length, 1);
//                System.out.println(PKbase64);
//                System.out.println(SKbase64);
                byte[] ignore = new byte[]{0};
                byte[] PubKey = new byte[64];
                byte[] PriKey = new byte[64];
                String PKbase64 = "fiqF70/mam+7TT+Tr0OP2u87I6grv/bdrgJ8ejYkT9w=";
                String SKbase64 = "yWm+kRLuD3zECmdSYzSBgHZZY3GSDsXJI/qcRuntZNp+KoXvT+Zqb7tNP5OvQ4/a7zsjqCu/9t2uAnx6NiRP3A==";
                lazySodium.getSodium().sodium_base642bin(PubKey,64, PKbase64.getBytes(StandardCharsets.UTF_8), PKbase64.getBytes(StandardCharsets.UTF_8).length, ignore, null, null, 1);
                lazySodium.getSodium().sodium_base642bin(PriKey,64, SKbase64.getBytes(StandardCharsets.UTF_8), SKbase64.getBytes(StandardCharsets.UTF_8).length, ignore, null, null, 1);
                priKey = PriKey;
                client.RegisterVoter(name, "test-group", PubKey);
            }

            int testVoterIndex = 0;
            if(testmode){
//                client.registerAllVoters(client);
//                client.preAuthAndAuthAllVoters(client);
                client.changeTestVoter(testVoterIndex);
                client.printTestInfo();
            }
            client.printInfo();
            int input = 0;
            try {
                input = scanner.nextInt();
            }catch (Exception e) {
                System.out.println("Input error! Please input again.");
                scanner.next();
            }
            while(input != 6){
                if(input == 0){
                    continue;
                }
                else if(input == -1){
                    client.registerAllVoters(client);
                }
                else if(input == -2){
                    client.unregisterAllVoters(client);
                }
                else if(input == -3){
                    client.preAuthAndAuthAllVoters(client);
                }
                else if(input == -4){
                    System.out.print("set voter >");
                    testVoterIndex = scanner.nextInt();
                    client.changeTestVoter(testVoterIndex);
                }
                else if(input == -5){
                    for(int i = 0; i < testVoterNum; i++){
                        System.out.println(i + " " + testVoterNames[i] + " " + testVoterGroups[i]);
                    }
                }
                else if(input == 1){
                    if(testmode) {
                        client.preAuthAndAuthTestVoter(client, testVoterIndex);
                        auth_token = auth_tokens[testVoterIndex];
                    }
                    else{
                        Challenge challenge = client.PreAuth(name);
                        auth_token = client.Auth(name, challenge).getValue().toByteArray();
                    }
                }
                else if(input == 4){
                    System.out.print("Please input the name of the election >");
                    String eName = scanner.next();
                    ElectionResult result = client.GetResult(eName);
                    for(int i = 0; i<result.getCountList().size(); i++) {
                        System.out.println("Choice name: " + result.getCount(i).getChoiceName() + ", Choice count: " + result.getCount(i).getCount());
                    }
                }
//                else if(input == 5){
//                    System.out.print("Please input the target >");
//                    String tar = scanner.next();
//                    target = tar;
//                    channel = ManagedChannelBuilder.forTarget(target)
//                            // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
//                            // needing certificates.
//                            .usePlaintext()
//                            .build();

//                }
                else if(input > 6){
                    System.out.println("Input error! Please input again.");
                }
                else if(input > 0 && auth_token != null){
                    if(input == 2){
                        System.out.print("Please set the name of the election >");
                        String eName = scanner.next();
                        System.out.print("Please set groups of the election (ex: group_a,group_b)>");
                        System.out.flush();
                        String inputGroups = scanner.next();
                        String[] groups = inputGroups.split(",");
                        System.out.print("Please set choices of the election (ex: choice_1,choice_2)>");
                        String inputChoices = scanner.next();
                        String[] choices = inputChoices.split(",");
//                        Instant instant = Instant.parse("2022-04-09T15:59:00Z");
                        System.out.print("Please set the end time of the election (seconds from now) >");
                        long s = scanner.nextLong();
                        Instant instant = Instant.now().plusSeconds(s);
                        Timestamp end_date = Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
                        client.CreateElection(eName, groups, choices, end_date);
                    }
                    else if(input == 3){
                        System.out.print("Please input the name of the election >");
                        String eName = scanner.next();
                        System.out.print("Please input the choice of the election >");
                        String c = scanner.next();
                        client.CastVote(eName, c);
                    }
                }
                else if(input > 0){
                    logger.info(name + " auth fail");
                }
                if(testmode){
                    client.printTestInfo();
                }
                client.printInfo();
                try {
                    input = scanner.nextInt();
                }catch (Exception e) {
                    System.out.println("Input error! Please input again.");
                    scanner.next();
                    input = 0;
                }
            }
            if(!testmode){
                client.UnregisterVoter(name);
            }
        } finally {
            // ManagedChannels use resources like threads and TCP connections. To prevent leaking these
            // resources the channel should be shut down when it will no longer be used. If it may be used
            // again leave it running.
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
