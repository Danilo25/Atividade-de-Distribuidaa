package main;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.*;
import java.util.*;

public class Servidor {

    private static InetSocketAddress apiAddress = new InetSocketAddress("localhost", 4000);
    private static InetSocketAddress DBAddress = null;

    private final int port;
    private final BlockingQueue<MessageTask> pingQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<MessageTask> messageQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> dbResponses = new LinkedBlockingQueue<>();
    private final List<String> pseudoWAL = new ArrayList<>();

    public Servidor(int port) {
        this.port = port;
    }

    public void start() {
        try {
            DatagramSocket socket = new DatagramSocket(port);
            System.out.println("Servidor iniciado na porta " + port);

            // Envia SERVER ao ApiGateway para se registrar
            sendMessage(socket, "SERVER", apiAddress);

            // Inicia threads
            new Thread(() -> listen(socket)).start();
            new Thread(() -> handlePings(socket)).start();
            new Thread(() -> handleMessages(socket)).start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void listen(DatagramSocket socket) {
        byte[] buffer = new byte[1024];

        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength()).trim();
                InetSocketAddress sender = new InetSocketAddress(packet.getAddress(), packet.getPort());

                if (message.equals("PING")) {
                    pingQueue.offer(new MessageTask(message, sender));
                } else if (message.startsWith("respostaDB|")) {
                    dbResponses.offer(message.substring("respostaDB|".length()));
                } else {
                    messageQueue.offer(new MessageTask(message, sender));
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handlePings(DatagramSocket socket) {
        while (true) {
            try {
                MessageTask task = pingQueue.take();
                System.out.println("Respondendo o PONG");
                sendMessage(socket, "PONG", task.sender);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void handleMessages(DatagramSocket socket) {
        while (true) {
            try {
                MessageTask task = messageQueue.take();
                String message = task.message;

                if (message.startsWith("DB_PORT:")) {
                    int DBPort = Integer.parseInt(message.split(":")[1]);
                    DBAddress = new InetSocketAddress("localhost", DBPort);
                    System.out.println("Porta da DB recebida: " + DBPort);

                } else if (DBAddress != null) {
                    pseudoWAL.add(message);
                    sendMessage(socket, message, DBAddress);
                    System.out.println("Comando enviado para DB: " + message);

                    // Aguarda resposta da DB (com timeout para evitar travar pra sempre)
                    String resposta = dbResponses.poll(3, TimeUnit.SECONDS);
                    if (resposta != null) {
                        System.out.println("Resposta da DB: " + resposta);
                        sendMessage(socket, "respostaServer|" + resposta, apiAddress);
                    } else {
                        System.out.println("DB não respondeu a tempo para comando: " + message);
                    }

                } else {
                    System.out.println("DB ainda não definida. Ignorando: " + message);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
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
        new Servidor(port).start();
    }

    private static class MessageTask {
        String message;
        InetSocketAddress sender;

        MessageTask(String message, InetSocketAddress sender) {
            this.message = message;
            this.sender = sender;
        }
    }
}