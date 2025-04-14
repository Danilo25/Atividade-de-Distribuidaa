package tcp;

import java.io.*;
import java.net.*;

public class ServidorTCP {
    private final int port;
    private Socket dbSocket;

    public ServidorTCP(int port) {
        this.port = port;
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("Uso: java ServidorTCP <porta>");
            return;
        }
        new ServidorTCP(Integer.parseInt(args[0])).start();
    }

    public void start() throws IOException {
        Socket apiSocket = new Socket("localhost", 4000);
        PrintWriter out = new PrintWriter(apiSocket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(apiSocket.getInputStream()));

        out.println("SERVER");
        String dbResponse = in.readLine();
        if (dbResponse.equals("DB_OK")) {
            dbSocket = new Socket("localhost", 4003);
        }

        while (true) {
            String requisicao = in.readLine();
            if (requisicao == null) continue;

            outToDB(requisicao);
            String resposta = inFromDB();

            out.println(resposta);
        }
    }

    private void outToDB(String msg) throws IOException {
        PrintWriter dbOut = new PrintWriter(dbSocket.getOutputStream(), true);
        dbOut.println(msg);
    }

    private String inFromDB() throws IOException {
        BufferedReader dbIn = new BufferedReader(new InputStreamReader(dbSocket.getInputStream()));
        return dbIn.readLine();
    }
}