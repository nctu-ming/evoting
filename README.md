# E-Voting

The goal of the project is to develop a remote electronic voting system.

## 需要的工具

- Java Development Kit (11+)
- Maven (3.8.5+)
- libsodium (1.0.18+)

## 編譯 Jar

```
~$ git clone https://github.com/nctu-ming/evoting && cd evoting
~$ mvn package
...
[INFO] BUILD SUCCESS
...
```

`將會在 target 目錄編譯出 evoting-1.0.0.jar`

## 執行 E-Voting Server

```
~$ java -cp target/evoting-1.0.0.jar tw.edu.nctu.cs.evoting.EVotingServer
INFO: Server started, listening on 50051
```

## 執行 E-Voting Client

```
~$ java -cp target/evoting-1.0.0.jar tw.edu.nctu.cs.evoting.EVotingClient
... client's output ...
```

## 清除

```
~$ mvn clean
```