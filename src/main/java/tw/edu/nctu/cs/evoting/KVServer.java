package tw.edu.nctu.cs.evoting;

import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.grpc.GrpcConfigKeys;
import org.apache.ratis.protocol.RaftGroup;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftPeer;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.RaftServerConfigKeys;
import org.apache.ratis.util.NetUtils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class KVServer implements Closeable {
    private static final Logger logger = Logger.getLogger(EVotingServer.class.getName());

    private final RaftServer server;

    private Integer SERVER_INDEX;

    private String STORAGE_PATH;

    public static List<RaftPeer> PEERS = null;

    protected static final Properties properties = new Properties();

    protected UUID CLUSTER_GROUP_ID;

    protected RaftGroup RAFT_GROUP;

    private void init() {
        final String kvServerIndexKey = "kv.server.index";

        SERVER_INDEX = Integer.parseInt(properties.getProperty(kvServerIndexKey));

        final String kvServerAddressListKey = "kv.server.address.list";

        final String[] addresses = Optional.ofNullable(properties.getProperty(kvServerAddressListKey))
                .map(s -> s.split(","))
                .orElse(null);
        if (addresses == null || addresses.length == 0) {
            throw new IllegalArgumentException("Failed to get " + kvServerAddressListKey);
        }

        final List<RaftPeer> peers = new ArrayList<>(addresses.length);
        for (int i = 0; i < addresses.length; i++) {
            peers.add(RaftPeer.newBuilder().setId("node" + i).setAddress(addresses[i]).build());
        }

        PEERS = Collections.unmodifiableList(peers);

        final String kvServerStoragePathKey = "kv.server.root.storage.path";
        STORAGE_PATH = properties.getProperty(kvServerStoragePathKey);
        if (STORAGE_PATH.isEmpty()) {
            throw new IllegalArgumentException("Failed to get " + kvServerStoragePathKey);
        }
    }

    public KVServer(String configPath) throws IOException {
        Globals.parseConfig(configPath, properties);

        init();

        final RaftPeer peer = PEERS.get(SERVER_INDEX);
        final File storageDir = new File(STORAGE_PATH);

        CLUSTER_GROUP_ID = UUID.fromString("72b4619a-c8df-11ec-9d64-0242ac120002");
        RAFT_GROUP = RaftGroup.valueOf(
                RaftGroupId.valueOf(CLUSTER_GROUP_ID), PEERS);

        // create a property object
        RaftProperties properties = new RaftProperties();

        // set the storage directory (different for each peer) in RaftProperty object
        RaftServerConfigKeys.setStorageDir(properties, Collections.singletonList(storageDir));

        // set the port which server listen to in RaftProperty object
        final int port = NetUtils.createSocketAddr(peer.getAddress()).getPort();
        GrpcConfigKeys.Server.setPort(properties, port);

        // create the KV state machine which hold the kv value
        KVStateMachine kvStateMachine = new KVStateMachine();

        this.server = RaftServer.newBuilder()
                .setGroup(RAFT_GROUP)
                .setProperties(properties)
                .setServerId(peer.getId())
                .setStateMachine(kvStateMachine)
                .build();
    }

    public void start() throws IOException {
        server.start();
        logger.info("KVServer started, listening on " + NetUtils.createSocketAddr(PEERS.get(SERVER_INDEX).getAddress()).getPort());
    }

    @Override
    public void close() throws IOException {
        server.close();
        logger.info("*** kv server shut down");
    }
}
