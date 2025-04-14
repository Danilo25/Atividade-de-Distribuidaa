package tcp;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ApiGatewayTCP {
    private static final int PORT = 4000;
    private final ExecutorService clientHandlerPool = Executors.newCachedThreadPool();
    private final List<Socket> servers = Collections.synchronizedList(new ArrayList<>());
    private Socket dbSocket = null;

    public static void main(String[] args) throws IOException {
        new ApiGatewayTCP().start();
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("ApiGatewayTCP iniciado na porta " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                clientHandlerPool.execute(() -> handleConnection(clientSocket));
            }
        }
    }

    private void handleConnection(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String msg = in.readLine();
            if (msg.equals("DB")) {
                dbSocket = socket;
                System.out.println("DB registrado: " + socket);
                synchronized (servers) {
                    for (Socket s : servers) {
                        new PrintWriter(s.getOutputStream(), true).println("DB_OK");
                    }
                }
            } else if (msg.equals("SERVER")) {
                servers.add(socket);
                System.out.println("Servidor registrado: " + socket);
                if (dbSocket != null) {
                    out.println("DB_OK");
                }
            } else if (msg.startsWith("Cliente:")) {
            	System.out.println("Cliente mandou mensagem " + msg);
                handleClientMessage(msg.substring(8), socket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleClientMessage(String message, Socket clientSocket) {
        if (servers.isEmpty()) {
            try {
                new PrintWriter(clientSocket.getOutputStream(), true).println("Nenhum servidor disponivel.");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        Socket serverSocket = servers.get(0);

        try {
            PrintWriter out = new PrintWriter(serverSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));

            out.println(message);
            String resposta = in.readLine();
            PrintWriter clientOut = new PrintWriter(clientSocket.getOutputStream(), true);
            clientOut.println("Resposta ao Cliente: " + resposta);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}