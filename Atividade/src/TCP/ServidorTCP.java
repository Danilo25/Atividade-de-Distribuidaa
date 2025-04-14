package TCP;

import java.io.*;
import java.net.*;

public class ServidorTCP {

    private final int port;
    private Socket dbSocket = null;

    public ServidorTCP(int port) {
        this.port = port;
    }

    public void start() {
        try (
            Socket gateway = new Socket("localhost", 4000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(gateway.getInputStream()));
            PrintWriter writer = new PrintWriter(gateway.getOutputStream(), true);
            ServerSocket serverSocket = new ServerSocket(port)
        ) {
            writer.println("SERVER");
            System.out.println("Servidor iniciado na porta " + port);

            while (true) {
                String msg = reader.readLine();
                if (msg == null) continue;

                System.out.println("Recebido do ApiGateway: " + msg);

                if (msg.equals("PING")) {
                    writer.println("PONG");
                } else if (msg.equals("DB_OK")) {
                    dbSocket = new Socket("localhost", 4003);
                    System.out.println("Conectado ao DB.");
                } else if (dbSocket != null) {
                    PrintWriter dbWriter = new PrintWriter(dbSocket.getOutputStream(), true);
                    BufferedReader dbReader = new BufferedReader(new InputStreamReader(dbSocket.getInputStream()));

                    dbWriter.println(msg);
                    String resposta = dbReader.readLine();

                    writer.println(resposta);
                } else {
                    System.out.println("DB não definido.");
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Uso: java Servidor <porta>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        new ServidorTCP(port).start();
    }
}