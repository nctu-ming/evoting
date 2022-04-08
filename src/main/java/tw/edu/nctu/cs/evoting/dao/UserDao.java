package tw.edu.nctu.cs.evoting.dao;

import tw.edu.nctu.cs.evoting.storage.KVStore;

public class UserDao {
    protected final KVStore<String, byte[]> store;

    public UserDao(KVStore<String, byte[]> store) {
        this.store = store;
    }

    private static final String USER_PREFIX = "user_";

    public static String genUserKey(String userName) {
        return USER_PREFIX + userName;
    }

    public void insertUser(String userName, byte[] userData) {
        String key = genUserKey(userName);
        store.put(key, userData);
    }

    public byte[] getUser(String userName) {
        String key = genUserKey(userName);
        return store.get(key);
    }

    public void removeUser(String userName) {
        String key = genUserKey(userName);
        store.delete(key);
    }
}
