package main;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ApiGateway {

    private final int port = 4000;
    private final Map<InetSocketAddress, Boolean> connectedServers = new ConcurrentHashMap<>();
    private volatile InetSocketAddress DBAddress = null;
    private final List<InetSocketAddress> pendingServers = new CopyOnWriteArrayList<>();

    public void start() {
        startHealthCheck(); // Inicia verificação periódica de ping

        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("ApiGateway iniciado na porta " + port);

            byte[] buffer = new byte[1024];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String received = new String(packet.getData(), 0, packet.getLength()).trim();
                InetSocketAddress senderAddress = new InetSocketAddress(packet.getAddress(), packet.getPort());

                handleMessage(received, senderAddress, socket);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleMessage(String message, InetSocketAddress sender, DatagramSocket socket) throws IOException {
        if (message.equals("PONG")) {
            // Ignora respostas de PONG
            return;
        }

        //System.out.println("Recebido de " + sender + ": " + message);

        if (message.startsWith("DB")) {
            DBAddress = sender;
            System.out.println("Servidor DB registrado em: " + DBAddress);

            for (InetSocketAddress server : pendingServers) {
                sendMessage(socket, "DB_PORT:" + DBAddress.getPort(), server);
            }
            pendingServers.clear();

        } else if (message.startsWith("SERVER")) {
            connectedServers.put(sender, true);
            System.out.println("Servidor registrado: " + sender);

            if (DBAddress != null) {
                sendMessage(socket, "DB_PORT:" + DBAddress.getPort(), sender);
            } else {
                pendingServers.add(sender);
                System.out.println("Servidor adicionado à fila de espera pelo DB.");
            }

        } else {
            if (connectedServers.isEmpty()) {
                sendMessage(socket, "Nenhum servidor disponível para processar a requisição.", sender);
            } else {
                InetSocketAddress server = connectedServers.keySet().iterator().next(); // Pega o primeiro servidor

                // Envia a mensagem para o servidor
                if(!message.startsWith("respostaServer|")) {
                	
                	sendMessage(socket, message, server);

                	// Aguarda a resposta do servidor
                	byte[] buf = new byte[1024];
                	DatagramPacket respostaPacote = new DatagramPacket(buf, buf.length);
                	socket.receive(respostaPacote);

                	String resposta = new String(respostaPacote.getData(), 0, respostaPacote.getLength());
                	/*if (resposta.startsWith("respostaServer|")) {
                        resposta = resposta.substring("respostaServer|".length());
                    }*/
                	//System.out.println("Respondendo de " + sender + ": " + resposta);

                	// Repassa a resposta de volta ao remetente original
                	sendMessage(socket, resposta, sender);
                }
            }
        }
    }

    private void sendMessage(DatagramSocket socket, String message, InetSocketAddress destination) throws IOException {
        byte[] data = message.getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, destination.getAddress(), destination.getPort());
        socket.send(packet);
    }

    private void startHealthCheck() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            try (DatagramSocket tempSocket = new DatagramSocket()) {
                tempSocket.setSoTimeout(2000); // Timeout de 2 segundos

                // Verifica servidores
                for (InetSocketAddress server : new ArrayList<>(connectedServers.keySet())) {
                    try {
                        sendMessage(tempSocket, "PING", server);

                        byte[] buf = new byte[1024];
                        DatagramPacket resposta = new DatagramPacket(buf, buf.length);
                        tempSocket.receive(resposta);

                        String respostaStr = new String(resposta.getData(), 0, resposta.getLength());
                        if (!respostaStr.equals("PONG")) {
                            throw new IOException("Resposta inválida");
                        }

                    } catch (IOException e) {
                        System.out.println("Servidor removido (sem resposta): " + server);
                        connectedServers.remove(server);
                    }
                }

                // Verifica o DB
                if (DBAddress != null) {
                    try {
                        sendMessage(tempSocket, "PING", DBAddress);

                        byte[] buf = new byte[1024];
                        DatagramPacket resposta = new DatagramPacket(buf, buf.length);
                        tempSocket.receive(resposta);

                        String respostaStr = new String(resposta.getData(), 0, resposta.getLength());
                        if (!respostaStr.equals("PONG")) {
                            throw new IOException("Resposta inválida");
                        }

                    } catch (IOException e) {
                        System.out.println("DB removido (sem resposta): " + DBAddress);
                        DBAddress = null;
                    }
                }

            } catch (IOException e) {
                System.out.println("Erro no health check: " + e.getMessage());
            }
        }, 5, 5, TimeUnit.SECONDS); // Executa a cada 5 segundos
    }

    public static void main(String[] args) {
        new ApiGateway().start();
    }
}