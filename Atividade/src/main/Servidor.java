package main;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.*;

public class Servidor {

    private static InetSocketAddress apiAddress = new InetSocketAddress("localhost", 4000);
    private static InetSocketAddress DBAddress = null;

    private final int port;

    public Servidor(int port) {
        this.port = port;
    }

    public void start() {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("Servidor iniciado na porta " + port);

            // Envia mensagem para ApiGateway
            sendMessage(socket, "SERVER", apiAddress);

            byte[] buffer = new byte[1024];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String received = new String(packet.getData(), 0, packet.getLength()).trim();
                InetSocketAddress sender = new InetSocketAddress(packet.getAddress(), packet.getPort());
                System.out.println("Recebido do ApiGateway: " + received);
                
                if (received.equals("PING")) {
                    sendMessage(socket, "PONG", sender);
                    continue; 
                }

                if (received.startsWith("DB_PORT:")) {
                    int DBPort = Integer.parseInt(received.split(":")[1]);
                    DBAddress = new InetSocketAddress("localhost", DBPort);
                    System.out.println("Porta da DB recebida: " + DBPort);

                } else {
                    if (DBAddress != null) {
                        sendMessage(socket, received, DBAddress); // Repassa o comando original para o DB
                        System.out.println("Comando enviado para DB: " + received);

                        // Espera resposta do DB
                        byte[] buf = new byte[1024];
                        DatagramPacket respostaPacote = new DatagramPacket(buf, buf.length);
                        socket.receive(respostaPacote);

                        String resposta = new String(respostaPacote.getData(), 0, respostaPacote.getLength());
                        System.out.println("Resposta recebida do DB: " + resposta);

                        // Envia resposta ao ApiGateway (ele se encarrega de repassar ao cliente)
                        sendMessage(socket, resposta, apiAddress);
                    } else {
                        System.out.println("DB ainda não definida. Ignorando comando.");
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(DatagramSocket socket, String message, InetSocketAddress destination) throws IOException {
        byte[] data = message.getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, destination.getAddress(), destination.getPort());
        socket.send(packet);
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Uso: java Servidor <porta>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        Servidor servidor = new Servidor(port);
        servidor.start();
    }
}