package tw.edu.nctu.cs.evoting.storage;

import org.apache.ratis.client.RaftClient;
import org.apache.ratis.conf.Parameters;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.grpc.GrpcFactory;
import org.apache.ratis.protocol.ClientId;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.protocol.RaftClientReply;
import org.apache.ratis.protocol.RaftGroup;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.edu.nctu.cs.evoting.Globals;
import tw.edu.nctu.cs.evoting.KVRequest;
import tw.edu.nctu.cs.evoting.KVResponse;
import tw.edu.nctu.cs.evoting.MapMessage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.iq80.leveldb.impl.Iq80DBFactory.*;

public class KVServerStore implements KVStore<String, byte[]>  {
    private static final Logger logger = LoggerFactory.getLogger(KVServerStore.class);

    RaftClient dbClient;

    private boolean isClosed = false;

    private static RaftClient buildClient(RaftGroup raftGroup) {
        RaftProperties raftProperties = new RaftProperties();
        RaftClient.Builder builder = RaftClient.newBuilder()
                .setProperties(raftProperties)
                .setRaftGroup(raftGroup)
                .setClientRpc(
                        new GrpcFactory(new Parameters())
                                .newRaftClientRpc(ClientId.randomId(), raftProperties));
        return builder.build();
    }

    public KVServerStore(RaftGroup raftGroup) {
        dbClient = buildClient(raftGroup);
    }

    @Override
    public void put(String key, byte[] value) {
        KVRequest kvRequest = KVRequest.newBuilder().setCommand(KVRequest.Command.PUT).setKey(key).setValue(com.google.protobuf.ByteString.copyFrom(value)).build();
        try {
            this.dbClient.io().send(Message.valueOf(ByteString.copyFrom(kvRequest.toByteArray())));
        } catch (Exception e) {
            logger.error("put " + key, e);
        }
    }

    @Override
    public byte[] get(String key) {
        KVRequest kvRequest = KVRequest.newBuilder().setCommand(KVRequest.Command.GET).setKey(key).build();
        KVResponse kvResponse = null;
        RaftClientReply reply = null;

        try {
            reply = this.dbClient.io().sendReadOnly(Message.valueOf(ByteString.copyFrom(kvRequest.toByteArray())));
            kvResponse = KVResponse.parseFrom(reply.getMessage().getContent().toByteArray());
        } catch (Exception e) {
            logger.error("get" + key, e);
        }

        assert kvResponse != null;
        if (kvResponse.getData().isEmpty()) {
            return null;
        }

        return kvResponse.getData().toByteArray();
    }

    @Override
    public void delete(String key) {
        KVRequest kvRequest = KVRequest.newBuilder().setCommand(KVRequest.Command.DELETE).setKey(key).build();
        try {
            this.dbClient.io().send(Message.valueOf(ByteString.copyFrom(kvRequest.toByteArray())));
        } catch (Exception e) {
            logger.error("delete" + key, e);
        }
    }

    @Override
    public void clear() {
        // TODO
    }

    @Override
    public void close() {
        try {
            // TODO: race condition
            this.dbClient.close();
            isClosed = true;
        } catch (IOException e) {
            logger.error("close", e);
        }
    }

    @Override
    public int size() {
        KVRequest kvRequest = KVRequest.newBuilder().setCommand(KVRequest.Command.SIZE).setKey("SIZE").build();
        KVResponse kvResponse = null;
        RaftClientReply reply;
        try {
            reply = this.dbClient.io().sendReadOnly(Message.valueOf(ByteString.copyFrom(kvRequest.toByteArray())));
            kvResponse = KVResponse.parseFrom(reply.getMessage().getContent().toByteArray());
        } catch (Exception e) {
            logger.error("size", e);
        }

        assert kvResponse != null;
        return kvResponse.getIntData();
    }

    @Override
    public LinkedHashMap<String, byte[]> prefixScans(String prefix) {
        KVRequest kvRequest = KVRequest.newBuilder().setCommand(KVRequest.Command.PREFIX_SCANS).setKey(prefix).build();
        KVResponse kvResponse = null;
        MapMessage mapMessage = null;
        RaftClientReply reply = null;

        try {
            reply = this.dbClient.io().sendReadOnly(Message.valueOf(ByteString.copyFrom(kvRequest.toByteArray())));
            kvResponse = KVResponse.parseFrom(reply.getMessage().getContent().toByteArray());
            mapMessage = MapMessage.parseFrom(kvResponse.getData().toByteArray());
        } catch (Exception e) {
            logger.error("prefixScans " + prefix, e);
        }

        Map<String, com.google.protobuf.ByteString> retMap = mapMessage.getMMap();

        Map<String, byte[]> newMap = retMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toByteArray()));

        LinkedHashMap<String, byte[]> retLHMap = new LinkedHashMap<>(newMap);

        return retLHMap;
    }
}
