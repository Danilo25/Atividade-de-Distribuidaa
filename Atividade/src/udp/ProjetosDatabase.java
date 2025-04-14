package udp;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProjetosDatabase {
    private static ProjetosDatabase instance;
    private final HashMap<String, List<String>> database = new HashMap<>();
    private final int port;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private ProjetosDatabase(int port) {
        this.port = port;
    }

    public static synchronized ProjetosDatabase getInstance(int port) {
        if (instance == null) {
            instance = new ProjetosDatabase(port);
        }
        return instance;
    }

    public void start() throws IOException {
        DatagramSocket socket = new DatagramSocket(port);
        System.out.println("ProjetosDatabase iniciado na porta " + port);

        sendToApi("Database: Estou vivo - Porta: " + port);

        byte[] buffer = new byte[1024];
        while (running.get()) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);

            String message = new String(packet.getData(), 0, packet.getLength());
            System.out.println("Nova mensagem: " + message);

            String response = processMessage(message.trim());

            DatagramPacket reply = new DatagramPacket(
                response.getBytes(),
                response.length(),
                packet.getAddress(),
                packet.getPort()
            );
            socket.send(reply);
        }
        socket.close();
    }

    private String processMessage(String message) {
        String[] parts = message.split("\\|");
        if (parts.length < 2) return "DataBaseResposta: Formato inválido. Esperado: Comando|Projeto|[Descrição ou Versão]";

        String comando = parts[0].trim().toLowerCase();
        String projeto = parts[1].trim();

        switch (comando) {
            case "criar":
                if (parts.length < 3) return "DataBaseResposta: Erro: Descrição obrigatória para criar um projeto.";
                if (database.containsKey(projeto)) return "DataBaseResposta: Erro: Projeto '" + projeto + "' já existe.";
                List<String> descricoes = new ArrayList<>();
                descricoes.add(parts[2].trim());
                database.put(projeto, descricoes);
                return "DataBaseResposta: Projeto '" + projeto + "' criado com sucesso.";

            case "atualizar":
                if (parts.length < 3) return "DataBaseResposta: Erro: Descrição obrigatória para atualizar o projeto.";
                if (!database.containsKey(projeto)) return "DataBaseResposta: Erro: Projeto '" + projeto + "' não encontrado.";
                database.get(projeto).add(parts[2].trim());
                return "DataBaseResposta: Projeto '" + projeto + "' atualizado com nova descrição.";

            case "verificarversoes":
                if (!database.containsKey(projeto)) return "DataBaseResposta: Erro: Projeto '" + projeto + "' não encontrado.";
                List<String> versoes = database.get(projeto);
                StringBuilder sb = new StringBuilder("DataBaseResposta: Versões do projeto '" + projeto + "':\n");
                for (int i = 0; i < versoes.size(); i++) {
                    sb.append("v").append(i + 1).append(": ").append(versoes.get(i)).append("\n");
                }
                return sb.toString();

            case "recuperarversao":
                if (!database.containsKey(projeto)) return "DataBaseResposta: Erro: Projeto '" + projeto + "' não encontrado.";
                List<String> lista = database.get(projeto);
                if (parts.length == 3) {
                    try {
                        int index = Integer.parseInt(parts[2].trim()) - 1;
                        if (index < 0 || index >= lista.size())
                            return "DataBaseResposta: Erro: Versão " + (index + 1) + " não existe.";
                        return "DataBaseResposta: Versão v" + (index + 1) + ": " + lista.get(index);
                    } catch (NumberFormatException e) {
                        return "DataBaseResposta: Erro: Número da versão inválido.";
                    }
                } else {
                    return "DataBaseResposta: Versão mais recente (v" + lista.size() + "): " + lista.get(lista.size() - 1);
                }

            default:
                return "DataBaseResposta: Erro: Comando '" + comando + "' não reconhecido.";
        }
    }

    private void sendToApi(String msg) throws IOException {
        DatagramSocket socket = new DatagramSocket();
        byte[] data = msg.getBytes();
        InetAddress address = InetAddress.getByName("localhost");
        DatagramPacket packet = new DatagramPacket(data, data.length, address, 4000);
        socket.send(packet);
        socket.close();
    }

    public void stop() {
        running.set(false);
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Uso: java ProjetosDatabase <porta>");
            return;
        }
        int porta;
        try {
            porta = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.out.println("Porta inválida: " + args[0]);
            return;
        }

        ProjetosDatabase db = ProjetosDatabase.getInstance(porta);
        try {
            db.start();
        } catch (IOException e) {
            System.err.println("Erro ao iniciar o ProjetosDatabase: " + e.getMessage());
        }
    }
}