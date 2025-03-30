package imd;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.StringTokenizer;


public class UDPServerGameStore {
    private GameStore gameStore;

    public UDPServerGameStore(String port) {
        gameStore = new GameStore();
        System.out.println("UDP Game Store Server started");
        
        try {
            DatagramSocket serverSocket = new DatagramSocket(Integer.parseInt(port));
            String operation = null;
            int accountId = 0;
            String gameName = "";
            int value = 0;
            String response;
            
            while (true) {
                byte[] receiveBuffer = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                serverSocket.receive(receivePacket);
                
                String message = new String(receivePacket.getData()).trim();
                StringTokenizer tokenizer = new StringTokenizer(message, ";");
                
                if (tokenizer.hasMoreTokens()) {
                    operation = tokenizer.nextToken();
                }
                if (tokenizer.hasMoreTokens()) {
                    accountId = Integer.parseInt(tokenizer.nextToken().trim());
                }
                if (tokenizer.hasMoreTokens()) {
                    gameName = tokenizer.nextToken().trim();
                }
                if (tokenizer.hasMoreTokens()) {
                    value = Integer.parseInt(tokenizer.nextToken().trim());
                }
                
                switch (operation) {
                    case "CriarConta":
                        gameStore.createAccount(accountId);
                        response = "Conta " + accountId + " criada.";
                        break;
                    case "ComprarJogo":
                        if (gameStore.buyGame(accountId, gameName, value)) {
                            response = "Jogo " + gameName + " comprado com sucesso!";
                        } else {
                            response = "Saldo insuficiente para comprar " + gameName;
                        }
                        break;
                    case "IniciarJogo":
                        if (gameStore.startGame(accountId, gameName)) {
                            response = "Iniciando " + gameName;
                        } else {
                            response = "Jogo " + gameName + " não encontrado na biblioteca.";
                        }
                        break;
                    case "Saldo":
                        response = "Saldo atual: R$" + gameStore.getBalance(accountId);
                        break;
                    default:
                        response = "Operação inválida.";
                        break;
                }
                
                System.out.println("Operação: " + operation + " - Conta: " + accountId + " - Resposta: " + response);
                byte[] sendBuffer = response.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length,
                        receivePacket.getAddress(), receivePacket.getPort());
                serverSocket.send(sendPacket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("UDP Game Store Server terminating");
    }

    public static void main(String[] args) {
        new UDPServerGameStore(args[0]);
    }
}