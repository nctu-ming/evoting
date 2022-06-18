# E-Voting

The goal of the project is to develop a remote electronic voting system.

## 需要的工具

- Java Development Kit (11+)
- Maven (3.8.5+)
- libsodium (1.0.18+)

## 編譯 Jar

```
~$ git clone -b part3 --single-branch https://github.com/nctu-ming/evoting && cd evoting
~$ mvn package
...
[INFO] BUILD SUCCESS
...
```

`將會在 target 目錄編譯出 evoting-1.0.0.jar`

## Server 設定檔說明

`/evoting/config/server/` 有放置 sample 的 server 設定檔

如果要將 server 部署到不同 Host 需要修改 `kv.server.address.list` 將 `127.0.0.1` 改為 `Host IP`。

下面以 `172.17.0.100`、`172.17.0.101`、`172.17.0.102` 為例。

```
#VotingService
app.server.port=50050

#KVService
kv.server.index=0
kv.server.address.list=172.17.0.100:18000,172.17.0.101:18001,172.17.0.102:18002
kv.server.root.storage.path=kv_storage
```

## Client 設定檔說明

`/evoting/config/client/` 有放置 sample 的 config 設定檔。

hostname0:port0、hostname1:port1 分別代表其中兩台 server node。

特別注意這裡填的是 `app.server.port` 的 HostIP:HostPort 資訊（並非 kv.server.address.list）的設定資訊。

```
server.hostname0 = 127.0.0.1
server.port0 = 50050
server.hostname1 = 127.0.0.1
server.port1 = 50051
```

## 執行 E-Voting Server

`E-Voting Server` 需要 3 個 `E-Voting Server` 運作在不同的 *Host:Port* 上。

如果 `E-Voting Server` 運作在相同 `Host` 需要確保運作在獨立的目錄（Directory）

以下面目錄結構來說，在同一台 `Host` 執行 3 個 `E-Voting Server`，每個都有獨自的工作目錄（nod1、node2、node3）

```
├── config
│   ├── client
│   │   └── target.config
│   └── server
│       ├── node1.config
│       ├── node2.config
│       └── node3.config
├── node1
│   └── evoting-1.0.0.jar
├── node2
│   └── evoting-1.0.0.jar
└── node3
    └── evoting-1.0.0.jar
```

### Server 啟動參數

```
~$ java -cp evoting-1.0.0.jar tw.edu.nctu.cs.evoting.EVotingServer <config file's path>
```

### 啟動 3 個 server 範例

```
# 啟動 server1
~$ pwd
/evoting/exp/node1
~$ java -cp evoting-1.0.0.jar tw.edu.nctu.cs.evoting.EVotingServer ../config/server/node1.config 
... server1's output ...
# 啟動 server2
~$ pwd
/evoting/exp/node2
~$ java -cp evoting-1.0.0.jar tw.edu.nctu.cs.evoting.EVotingServer ../config/server/node2.config
... server2's output ...
# 啟動 server3
~$ pwd
/evoting/exp/node3
~$ java -cp evoting-1.0.0.jar tw.edu.nctu.cs.evoting.EVotingServer ../config/server/node3.config
... server3's output ...
```

## 執行 E-Voting Client

```
~$ java -cp target/evoting-1.0.0.jar tw.edu.nctu.cs.evoting.EVotingClient <config file's path>
```

```
~$ java -cp target/evoting-1.0.0.jar tw.edu.nctu.cs.evoting.EVotingClient ./config/client/target.config
... client's output ...
```

## 清除

```
~$ mvn clean
```
