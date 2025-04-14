package TCP;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ApiGatewayTCP {

    private final int port = 4000;
    private final Map<Socket, PrintWriter> connectedServers = new ConcurrentHashMap<>();
    private volatile Socket dbSocket = null;
    private final List<Socket> pendingServers = new CopyOnWriteArrayList<>();

    public void start() {
        startHealthCheck();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("ApiGateway TCP iniciado na porta " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleClient(Socket socket) {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String message;
            while ((message = reader.readLine()) != null) {
                System.out.println("Recebido: " + message + " de " + socket.getRemoteSocketAddress());

                if (message.equals("PONG")) continue;

                if (message.startsWith("DB")) {
                    dbSocket = socket;
                    System.out.println("DB registrado: " + socket.getRemoteSocketAddress());
                    for (Socket s : pendingServers) {
                        PrintWriter pw = connectedServers.get(s);
                        if (pw != null) pw.println("DB_OK");
                    }
                    pendingServers.clear();

                } else if (message.startsWith("SERVER")) {
                    connectedServers.put(socket, writer);
                    System.out.println("Servidor registrado: " + socket.getRemoteSocketAddress());
                    if (dbSocket != null) {
                        writer.println("DB_OK");
                    } else {
                        pendingServers.add(socket);
                    }

                } else {
                    if (connectedServers.isEmpty()) {
                        writer.println("Nenhum servidor disponível.");
                    } else {
                        Socket server = connectedServers.keySet().iterator().next();
                        PrintWriter serverWriter = connectedServers.get(server);
                        BufferedReader serverReader = new BufferedReader(new InputStreamReader(server.getInputStream()));
                        serverWriter.println(message);
                        String response = serverReader.readLine();
                        writer.println(response);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Erro com cliente " + socket.getRemoteSocketAddress() + ": " + e.getMessage());
        }
    }

    private void startHealthCheck() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            List<Socket> toRemove = new ArrayList<>();

            for (Socket socket : connectedServers.keySet()) {
                try {
                    PrintWriter out = connectedServers.get(socket);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    out.println("PING");
                    socket.setSoTimeout(2000);
                    String resp = in.readLine();
                    if (!"PONG".equals(resp)) throw new IOException("Sem resposta");
                } catch (Exception e) {
                    System.out.println("Removendo servidor inativo: " + socket.getRemoteSocketAddress());
                    toRemove.add(socket);
                }
            }
            toRemove.forEach(connectedServers::remove);

            if (dbSocket != null) {
                try {
                    PrintWriter out = new PrintWriter(dbSocket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(dbSocket.getInputStream()));
                    out.println("PING");
                    String resp = in.readLine();
                    if (!"PONG".equals(resp)) throw new IOException("DB sem resposta");
                } catch (Exception e) {
                    System.out.println("Removendo DB inativo: " + dbSocket.getRemoteSocketAddress());
                    dbSocket = null;
                }
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    public static void main(String[] args) {
        new ApiGatewayTCP().start();
    }
}