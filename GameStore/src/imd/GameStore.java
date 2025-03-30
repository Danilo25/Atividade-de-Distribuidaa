package imd;

import java.util.HashMap;
import java.util.HashSet;

public class GameStore {
    private HashMap<Integer, Integer> accounts = new HashMap<>();
    private HashMap<Integer, HashSet<String>> ownedGames = new HashMap<>();

    public void createAccount(int accountId) {
        accounts.put(accountId, 0);
        ownedGames.put(accountId, new HashSet<>());
    }

    public void addFunds(int accountId, int amount) {
        accounts.put(accountId, accounts.get(accountId) + amount);
    }

    public boolean buyGame(int accountId, String gameName, int price) {
        int balance = accounts.getOrDefault(accountId, -1);
        if (balance >= price) {
            accounts.put(accountId, balance - price);
            ownedGames.get(accountId).add(gameName);
            return true;
        }
        return false;
    }

    public boolean startGame(int accountId, String gameName) {
        return ownedGames.getOrDefault(accountId, new HashSet<>()).contains(gameName);
    }

    public int getBalance(int accountId) {
        return accounts.getOrDefault(accountId, -1);
    }
}
