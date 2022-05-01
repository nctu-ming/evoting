package tw.edu.nctu.cs.evoting;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.ratis.proto.RaftProtos;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.protocol.TermIndex;
import org.apache.ratis.server.raftlog.RaftLog;
import org.apache.ratis.server.storage.RaftStorage;
import org.apache.ratis.statemachine.TransactionContext;
import org.apache.ratis.statemachine.impl.BaseStateMachine;
import org.apache.ratis.statemachine.impl.SimpleStateMachineStorage;
import org.apache.ratis.statemachine.impl.SingleFileSnapshotInfo;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;
import org.apache.ratis.util.JavaUtils;
import org.slf4j.LoggerFactory;
import tw.edu.nctu.cs.evoting.storage.KVStore;
import tw.edu.nctu.cs.evoting.storage.LevelDBKVStore;

import java.io.*;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static tw.edu.nctu.cs.evoting.KVRequest.Command.SIZE;

public class KVStateMachine extends BaseStateMachine {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(KVStateMachine.class);

    private final SimpleStateMachineStorage storage = new SimpleStateMachineStorage();

    private static final LevelDBKVStore store = new LevelDBKVStore(new File("./db/kvdb"));

    @Override
    public void initialize(RaftServer server, RaftGroupId groupId,
                           RaftStorage raftStorage) throws IOException {
        super.initialize(server, groupId, raftStorage);
        this.storage.init(raftStorage);
    }

    @Override
    public CompletableFuture<Message> query(Message request) {
        KVRequest kvReq = null;
        try {
            kvReq = KVRequest.parseFrom(request.getContent().toByteArray());
        } catch (InvalidProtocolBufferException e) {
            logger.error("KVRequest.parseFrom", e);
        }

        byte[] data = new byte[0];
        int intData = 0;

        switch (Objects.requireNonNull(kvReq).getCommand()) {
            case GET -> data = store.get(kvReq.getKey());
            case PREFIX_SCANS -> {
                LinkedHashMap<String, com.google.protobuf.ByteString> lhm = store.prefixScansForKVS(kvReq.getKey());
                data = MapMessage.newBuilder().putAllM(lhm).build().toByteArray();
            }
            case SIZE -> {
                intData = store.size();
            }
        }

        KVResponse kvResponse;

        com.google.protobuf.ByteString dataByteString;

        if (kvReq.getCommand().equals(SIZE)) {
            kvResponse = KVResponse.newBuilder().setIntData(intData).build();
        } else {
            try {
                dataByteString = com.google.protobuf.ByteString.copyFrom(data);
                kvResponse = KVResponse.newBuilder().setData(dataByteString).build();
            } catch (NullPointerException e) {
                kvResponse = KVResponse.newBuilder().build();
            }
        }

        return CompletableFuture.completedFuture(Message.valueOf(ByteString.copyFrom(kvResponse.toByteArray())));
    }

    @Override
    public CompletableFuture<Message> applyTransaction(TransactionContext trx) {
        final RaftProtos.LogEntryProto entry = trx.getLogEntry();

        KVRequest kvReq = null;
        try {
            kvReq = KVRequest.parseFrom(entry.getStateMachineLogEntry().getLogData().toByteArray());
        } catch (InvalidProtocolBufferException e) {
            logger.error("KVRequest.parseFrom", e);
        }

        // update the last applied term and index
        final long index = entry.getIndex();
        updateLastAppliedTermIndex(entry.getTerm(), index);

        // actual execution of the command
        switch (Objects.requireNonNull(kvReq).getCommand()) {
            case PUT -> store.put(kvReq.getKey(), kvReq.getValue().toByteArray());
            case DELETE -> store.delete(kvReq.getKey());
        }

        return CompletableFuture.completedFuture(Message.valueOf("TODO"));
    }
}
