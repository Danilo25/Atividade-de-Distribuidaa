package main;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ApiGateway {

    private final int port = 4000;
    private final Map<InetSocketAddress, Boolean> connectedServers = new ConcurrentHashMap<>();
    private volatile InetSocketAddress DBAddress = null;
    private final List<InetSocketAddress> pendingServers = new CopyOnWriteArrayList<>();

    private final AtomicInteger requestCounter = new AtomicInteger(0);
    private final Map<Integer, InetSocketAddress> requestMap = new ConcurrentHashMap<>();
    private final Map<Integer, BlockingQueue<String>> responseQueues = new ConcurrentHashMap<>();
    private final ExecutorService clientHandlerPool = Executors.newCachedThreadPool();

    public void start() {
        startHealthCheck();

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
        	//System.out.println("Recebeu o pong" + message);
            return;
        }

        if (message.startsWith("Resposta")) {
        	//System.out.println("Resposta: " + message);
            String[] parts = message.split(" ", 3);
            if (parts.length >= 3) {
                try {
                    int id = Integer.parseInt(parts[1]);
                    BlockingQueue<String> queue = responseQueues.get(id);
                    if (queue != null) {
                        queue.offer("Requisicao " + id + " " + parts[2]);
                    }
                } catch (NumberFormatException ignored) {}
            }
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
                System.out.println("Servidor adicionado a fila de espera pelo DB.");
            }

        } else if (message.startsWith("Cliente:")) {
            clientHandlerPool.submit(() -> handleClientMessage(message.substring(8), sender, socket));

        } else {
            sendMessage(socket, "Formato de mensagem invalido.", sender);
        }
    }

    private void handleClientMessage(String message, InetSocketAddress sender, DatagramSocket socket) {
        int requestId = requestCounter.incrementAndGet();
        requestMap.put(requestId, sender);
        BlockingQueue<String> responseQueue = new ArrayBlockingQueue<>(1);
        responseQueues.put(requestId, responseQueue);

        try {
            if (connectedServers.isEmpty()) {
                sendMessage(socket, "Nenhum servidor disponivel.", sender);
                return;
            }

            InetSocketAddress server = connectedServers.keySet().iterator().next();
            String formattedMessage = "Requisicao " + requestId + " " + message;
            sendMessage(socket, formattedMessage, server);

            String resposta = responseQueue.poll(5, TimeUnit.SECONDS);
            if (resposta != null) {
                //System.out.println("Resposta para o Cliente: " + resposta);
                sendMessage(socket, "Resposta ao Cliente: " + resposta, sender);  // <-- AQUI
            } else {
                sendMessage(socket, "Timeout na resposta do servidor.", sender);
            }

        } catch (Exception e) {
            try {
                sendMessage(socket, "Erro ao processar requisicao: " + e.getMessage(), sender);
            } catch (IOException ignored) {}

        } finally {
            requestMap.remove(requestId);
            responseQueues.remove(requestId);
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
                tempSocket.setSoTimeout(2000);

                for (InetSocketAddress server : new ArrayList<>(connectedServers.keySet())) {
                    try {
                        sendMessage(tempSocket, "PING", server);

                        byte[] buf = new byte[1024];
                        DatagramPacket resposta = new DatagramPacket(buf, buf.length);
                        tempSocket.receive(resposta);

                        String respostaStr = new String(resposta.getData(), 0, resposta.getLength());
                        if (!respostaStr.equals("PONG")) {
                            throw new IOException("Resposta invalida");
                        }

                    } catch (IOException e) {
                        System.out.println("Servidor removido (sem resposta): " + server);
                        connectedServers.remove(server);
                    }
                }

                if (DBAddress != null) {
                    try {
                        sendMessage(tempSocket, "PING", DBAddress);

                        byte[] buf = new byte[1024];
                        DatagramPacket resposta = new DatagramPacket(buf, buf.length);
                        tempSocket.receive(resposta);

                        String respostaStr = new String(resposta.getData(), 0, resposta.getLength());
                        if (!respostaStr.equals("PONG")) {
                            throw new IOException("Resposta invalida");
                        }

                    } catch (IOException e) {
                        System.out.println("DB removido (sem resposta): " + DBAddress);
                        DBAddress = null;
                    }
                }

            } catch (IOException e) {
                System.out.println("Erro no health check: " + e.getMessage());
            }
        }, 15, 15, TimeUnit.SECONDS);
    }

    public static void main(String[] args) {
        new ApiGateway().start();
    }
}