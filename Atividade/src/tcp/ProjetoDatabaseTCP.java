package tcp;

import java.io.*;
import java.net.*;
import java.util.*;

public class ProjetoDatabaseTCP {
    private static final int PORT = 4003;
    private static final Map<String, List<String>> banco = new HashMap<>();

    public static void main(String[] args) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[DB TCP] Rodando na porta " + PORT);
            Socket apiSocket = new Socket("localhost", 4000);
            new PrintWriter(apiSocket.getOutputStream(), true).println("DB");

            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(() -> handleClient(socket)).start();
            }
        }
    }

    private static void handleClient(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String msg;
            while ((msg = in.readLine()) != null) {
                String[] partes = msg.split("\\|");
                String comando = partes[1].trim().toLowerCase();
                String nome = partes.length > 2 ? partes[2].trim() : "";
                String resposta = "";

                switch (comando) {
                    case "criar":
                        if (banco.containsKey(nome)) {
                            resposta = "Projeto " + nome + " ja existe";
                        } else {
                            banco.put(nome, new ArrayList<>());
                            banco.get(nome).add(partes[3].trim());
                            resposta = "Projeto " + nome + " criado com sucesso";
                        }
                        break;
                    case "atualizar":
                        if (!banco.containsKey(nome)) {
                            resposta = "Projeto " + nome + " nao encontrado";
                        } else {
                            String descricao = partes[3].trim();
                            List<String> versoes = banco.get(nome);
                            int index = versoes.indexOf(descricao);
                            if (index != -1) {
                                resposta = "Descricao ja existe na versao v" + (index + 1) + " de " + nome;
                            } else {
                                versoes.add(descricao);
                                resposta = "O usuario 1 atualizou " + nome;
                            }
                        }
                        break;
                    case "verificar versoes":
                        if (!banco.containsKey(nome)) {
                            resposta = "Nenhuma versao encontrada para " + nome;
                        } else {
                            List<String> versoes = banco.get(nome);
                            StringBuilder sb = new StringBuilder("Versoes de " + nome + ":[");
                            for (int i = 0; i < versoes.size(); i++) {
                                sb.append("v").append(i + 1);
                                if (i < versoes.size() - 1) sb.append(",");
                            }
                            sb.append("]");
                            resposta = sb.toString();
                        }
                        break;
                    case "recuperar versao":
                        if (!banco.containsKey(nome)) {
                            resposta = "Nenhuma versao encontrada para " + nome;
                        } else {
                            List<String> versoes = banco.get(nome);
                            int v = partes.length == 4 ? Integer.parseInt(partes[3].trim()) : versoes.size();
                            if (v >= 1 && v <= versoes.size()) {
                                resposta = "Versao " + v + " de " + nome + ": \"" + versoes.get(v - 1) + "\"";
                            } else {
                                resposta = "Versao " + v + " nao encontrada para " + nome;
                            }
                        }
                        break;
                    default:
                        resposta = "Comando desconhecido: " + comando;
                }
                out.println(resposta);
            }
        } catch (IOException | NumberFormatException e) {
            System.out.println("[DB TCP] Erro: " + e.getMessage());
        }
    }
}
