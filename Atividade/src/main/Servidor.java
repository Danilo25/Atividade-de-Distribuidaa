package main;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Servidor {

    private static InetSocketAddress apiAddress = new InetSocketAddress("localhost", 4000);
    private static InetSocketAddress dbAddress = null; // Definido dinamicamente

    private final int port;
    private final AtomicInteger threadCounter = new AtomicInteger(1);

    // Mapa para armazenar respostas do DB associadas ao reqId
    private final ConcurrentHashMap<String, String> respostasDB = new ConcurrentHashMap<>();

    public Servidor(int port) {
        this.port = port;
    }

    public void start() {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("Servidor iniciado na porta " + port);
            sendMessage(socket, "SERVER", apiAddress); // Registro com a API

            byte[] buffer = new byte[1024];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String received = new String(packet.getData(), 0, packet.getLength()).trim();
                InetSocketAddress sender = new InetSocketAddress(packet.getAddress(), packet.getPort());

                if (received.equals("PING")) {
                    sendMessage(socket, "PONG", sender);
                    continue;
                }

                if (received.startsWith("DB_PORT:")) {
                    int dbPort = Integer.parseInt(received.split(":")[1]);
                    dbAddress = new InetSocketAddress("localhost", dbPort);
                    System.out.println("Endereço do DB registrado: " + dbAddress);
                    continue;
                }

                if (received.startsWith("RespostaBD ")) {
                    //System.out.println("Mensagem resposta: " + received);
                    String[] respostaParts = received.split(" ", 3); // RespostaBD <reqId> <mensagem>
                    if (respostaParts.length >= 3) {
                        String reqId = respostaParts[1];
                        String mensagem = respostaParts[2];
                        respostasDB.put(reqId, mensagem);
                    }
                    continue;
                }

                if (received.startsWith("Requisicao")) {
                    //System.out.println("Requisição: " + received);
                    int threadId = threadCounter.getAndIncrement();

                    new Thread(() -> {
                        try {
                            String[] parts = received.split(" ", 3);
                            if (parts.length < 3) return;

                            String reqId = parts[1];
                            String conteudo = parts[2];

                            if (dbAddress == null) {
                                System.out.println("DB não registrado ainda.");
                                return;
                            }

                            // Envia a mensagem para o DB
                            String mensagemParaDB = reqId + "|" + conteudo;
                            byte[] dbData = mensagemParaDB.getBytes();
                            //System.out.println("Enviando ao DB: " + mensagemParaDB);
                            DatagramPacket dbPacket = new DatagramPacket(
                                    dbData, dbData.length,
                                    dbAddress.getAddress(), dbAddress.getPort()
                            );

                            synchronized (this) {
                                socket.send(dbPacket);
                            }

                            // Aguarda a resposta no mapa por até 5 segundos (tentativas de 500ms)
                            String resposta = null;
                            int tentativas = 10;

                            for (int i = 0; i < tentativas; i++) {
                                resposta = respostasDB.remove(reqId);
                                if (resposta != null) break;
                                Thread.sleep(500);
                            }

                            if (resposta == null) {
                                resposta = "ERRO: Sem resposta do DB.";
                            }

                            // Envia resposta para a API
                            String respostaFinal = "Resposta " + reqId + " " + resposta;
                            //System.out.println("Resposta para a API: " + respostaFinal);
                            sendMessage(socket, respostaFinal, apiAddress);

                            //System.out.println("Thread " + threadId + " respondeu: " + respostaFinal);

                        } catch (IOException | InterruptedException e) {
                            System.out.println("Erro na thread " + threadId + ": " + e.getMessage());
                        }
                    }).start();

                } else {
                    System.out.println("Mensagem desconhecida: " + received);
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
        new Servidor(port).start();
    }
}