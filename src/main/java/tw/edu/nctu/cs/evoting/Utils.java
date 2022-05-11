package tw.edu.nctu.cs.evoting;

import com.google.protobuf.ByteString;
import com.goterl.lazysodium.LazySodiumJava;
import com.goterl.lazysodium.SodiumJava;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

import static tw.edu.nctu.cs.evoting.EVotingClient.lazySodium;

public class Utils {
    private static final Logger logger = Logger.getLogger(Utils.class.getName());

    public static void registerVoter(eVotingGrpc.eVotingBlockingStub blockingStub, String name, String group, byte[] pubKeyBytes) {
        Voter voter = Voter.newBuilder().setName(name).setGroup(group).setPublicKey(ByteString.copyFrom(pubKeyBytes)).build();
        Status status;

        try {
            status = blockingStub.registerVoter(voter);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
        }
    }

    public static void importVoters(int serverPort) {
        ManagedChannel channel = ManagedChannelBuilder.forTarget("localhost:" + serverPort)
                .usePlaintext()
                .build();

        eVotingGrpc.eVotingBlockingStub blockingStub = eVotingGrpc.newBlockingStub(channel);

        final String[] pkList = new String[]{
                "5bkBKzX1bA7oEqZnUYhI5LliLrNxoereKxbNbwjfPEw=",
                "fiqF70/mam+7TT+Tr0OP2u87I6grv/bdrgJ8ejYkT9w=",
                "QCj+KLwGNJE8OV9fkoaRXAU4lgp2Orc+JzadBurVS6c=",
                "hXhfEj8xIQrMKeUFQbqnvsPVACP9ZY6QPgfy+LrW8a8=",
                "h9so5m4T01F6hSDWI6R+SiDWM1TNBisZf+V450rYwMs="
        };

        final String[] nameList = new String[]{
            "TeamA",
            "TeamB",
            "TeamE",
            "team 32D",
            "TA",
        };

        final String groupName = "Public";

        LazySodiumJava lazySodium = new LazySodiumJava(new SodiumJava());

        for (int i = 0; i < nameList.length; i++) {
            byte[] ignore = new byte[]{0};
            byte[] PubKey = new byte[64];
            String PKbase64 = pkList[i];
            String username = nameList[i];

            lazySodium.getSodium().sodium_base642bin(PubKey, 64, PKbase64.getBytes(StandardCharsets.UTF_8), PKbase64.getBytes(StandardCharsets.UTF_8).length, ignore, null, null, 1);
            registerVoter(blockingStub, username, groupName, PubKey);
        }

        channel.shutdown();
    }
}
