package TCP;

import java.io.*;
import java.net.*;
import java.util.*;

public class ProjetoDatabaseTCP {
    static final Map<String, List<String>> banco = new HashMap<>();
    static final int porta = 4003;

    public static void main(String[] args) throws Exception {
        ServerSocket serverSocket = new ServerSocket(porta);
        System.out.println("[DB] Escutando na porta " + porta);

        // Conectar-se ao ApiGateway
        Socket gatewaySocket = new Socket("localhost", 4000);
        PrintWriter gatewayOut = new PrintWriter(gatewaySocket.getOutputStream(), true);
        BufferedReader gatewayIn = new BufferedReader(new InputStreamReader(gatewaySocket.getInputStream()));
        gatewayOut.println("DB");

        // Thread para lidar com solicitações do ApiGateway
        new Thread(() -> {
            try {
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(() -> handleClient(clientSocket)).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        while (true) {
            String msg = gatewayIn.readLine();
            if (msg == null) continue;

            if (msg.equals("PING")) {
                gatewayOut.println("PONG");
            }
        }
    }

    private static void handleClient(Socket socket) {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String msg;
            while ((msg = reader.readLine()) != null) {
                System.out.println("[DB] Mensagem recebida: " + msg);

                String[] partes = msg.split("\\|");
                String comando = partes[0].trim().toLowerCase();
                String nome = partes.length > 1 ? partes[1].trim() : "";
                String resposta = "";

                switch (comando) {
                    case "criar":
                        banco.putIfAbsent(nome, new ArrayList<>());
                        banco.get(nome).add(partes[2].trim());
                        resposta = "Projeto " + nome + " criado com sucesso";
                        break;
                    case "atualizar":
                        banco.putIfAbsent(nome, new ArrayList<>());
                        banco.get(nome).add(partes[2].trim());
                        resposta = "Projeto " + nome + " atualizado com sucesso";
                        break;
                    case "verificar versoes":
                        resposta = banco.containsKey(nome)
                            ? "Versoes: " + banco.get(nome)
                            : "Nenhuma versão para " + nome;
                        break;
                    case "recuperar versao":
                        if (banco.containsKey(nome)) {
                            int index = Integer.parseInt(partes[2].trim()) - 1;
                            List<String> versoes = banco.get(nome);
                            if (index >= 0 && index < versoes.size()) {
                                resposta = versoes.get(index);
                            } else {
                                resposta = "Versão inválida.";
                            }
                        } else {
                            resposta = "Projeto não encontrado.";
                        }
                        break;
                    default:
                        resposta = "Comando desconhecido.";
                }

                writer.println(resposta);
            }

        } catch (IOException e) {
            System.out.println("[DB] Erro no cliente: " + e.getMessage());
        }
    }
}