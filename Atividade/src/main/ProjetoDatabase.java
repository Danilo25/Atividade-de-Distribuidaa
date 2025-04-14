package main;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;

public class ProjetoDatabase {
    static final Map<String, List<String>> banco = new HashMap<>();
    static final int porta = 4003; // Porta fixa do DB

    public static void main(String[] args) throws Exception {
        DatagramSocket socket = new DatagramSocket(porta);
        System.out.println("[DB] Rodando na porta " + porta);

        // Enviar registro ao ApiGateway usando o MESMO socket (porta 4003)
        String mensagem = "DB";
        byte[] dados = mensagem.getBytes();
        DatagramPacket pacote = new DatagramPacket(dados, dados.length, InetAddress.getByName("localhost"), 4000);
        socket.send(pacote);

        byte[] buffer = new byte[1024];

        while (true) {
            // Receber pacote
            DatagramPacket recebido = new DatagramPacket(buffer, buffer.length);
            socket.receive(recebido);

            String msg = new String(recebido.getData(), 0, recebido.getLength());
            InetSocketAddress remetente = new InetSocketAddress(recebido.getAddress(), recebido.getPort());

            
            
            if (msg.equals("PING")) {
                byte[] resposta = "PONG".getBytes();
                DatagramPacket pongPacket = new DatagramPacket(resposta, resposta.length,
                        remetente.getAddress(), remetente.getPort());
                socket.send(pongPacket);
                continue;
            }else {
            	//System.out.println("[DB] Mensagem recebida: " + msg);
            }


            String[] partes = msg.split("\\|");
            String reqId = partes[0].trim();  // Novo: ID da requisição
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
                    if (partes.length == 4) {
                        try {
                            int v = Integer.parseInt(partes[3].trim());
                            if (v >= 1 && v <= versoes.size()) {
                                resposta = "Versao " + v + " de " + nome + ": \"" + versoes.get(v - 1) + "\"";
                            } else {
                                resposta = "Versao " + v + " nao encontrada para " + nome;
                            }
                        } catch (NumberFormatException e) {
                            resposta = "Formato de versao invalido para " + nome;
                        }
                    } else {
                        int v = versoes.size();
                        resposta = "Versao " + v + " de " + nome + ": \"" + versoes.get(v - 1) + "\"";
                    }
                }
                break;

            default:
                resposta = "Comando desconhecido: " + comando;
                break;
        }

            // Enviar resposta para quem enviou o comando
            String respostaFinal = "RespostaBD " + reqId + " " + resposta;
            byte[] respostaBytes = respostaFinal.getBytes();
            DatagramPacket respostaPacote = new DatagramPacket(respostaBytes, respostaBytes.length,
                    remetente.getAddress(), remetente.getPort());
            socket.send(respostaPacote);
        }
    }
}