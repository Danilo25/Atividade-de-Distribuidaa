package imd;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Scanner;

public class UDPClientGameStore {
    public UDPClientGameStore() {
        System.out.println("UDP Game Store Client Started");
        Scanner scanner = new Scanner(System.in);

        try {
            DatagramSocket clientSocket = new DatagramSocket();
            InetAddress serverAddress = InetAddress.getByName("localhost");
            byte[] sendBuffer;
            byte[] receiveBuffer = new byte[1024];

            while (true) {
                System.out.println("\nEscolha uma opção:");
                System.out.println("1 - Criar Conta");
                System.out.println("2 - Comprar Jogo");
                System.out.println("3 - Iniciar Jogo");
                System.out.println("4 - Consultar Saldo");
                System.out.println("5 - Sair");
                int choice = scanner.nextInt();
                scanner.nextLine();

                String message = "";
                switch (choice) {
                    case 1:
                        System.out.print("Digite o ID da conta: ");
                        int accountId = scanner.nextInt();
                        scanner.nextLine();
                        message = "CriarConta;" + accountId;
                        break;
                    case 2:
                        System.out.print("Digite o ID da conta: ");
                        accountId = scanner.nextInt();
                        scanner.nextLine();
                        System.out.print("Digite o nome do jogo: ");
                        String gameName = scanner.nextLine();
                        System.out.print("Digite o valor do jogo: ");
                        int value = scanner.nextInt();
                        scanner.nextLine();
                        message = "ComprarJogo;" + accountId + ";" + gameName + ";" + value;
                        break;
                    case 3:
                        System.out.print("Digite o ID da conta: ");
                        accountId = scanner.nextInt();
                        scanner.nextLine();
                        System.out.print("Digite o nome do jogo: ");
                        gameName = scanner.nextLine();
                        message = "IniciarJogo;" + accountId + ";" + gameName;
                        break;
                    case 4:
                        System.out.print("Digite o ID da conta: ");
                        accountId = scanner.nextInt();
                        scanner.nextLine();
                        message = "Saldo;" + accountId;
                        break;
                    case 5:
                        System.out.println("Encerrando cliente...");
                        clientSocket.close();
                        scanner.close();
                        return;
                    default:
                        System.out.println("Opção inválida.");
                        continue;
                }

                sendBuffer = message.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, serverAddress, 9004);
                clientSocket.send(sendPacket);

                Arrays.fill(receiveBuffer, (byte) 0);
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                clientSocket.receive(receivePacket);

                String response = new String(receivePacket.getData()).trim();
                System.out.println("Resposta do servidor: " + response);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("UDP Client Terminating");
    }

    public static void main(String[] args) {
        new UDPClientGameStore();
    }
}