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
            } else {
            	//System.out.println("[DB] Mensagem recebida: " + msg);
            }


            String[] partes = msg.split("\\|");
            String comando = partes[0].trim().toLowerCase();
            String nome = partes.length > 1 ? partes[1].trim() : "";
            String resposta = "";

            switch (comando) {
                case "criar":
                    if (!banco.containsKey(nome)) {
                        banco.put(nome, new ArrayList<>());
                        banco.get(nome).add(partes[2].trim());
                        resposta = "Projeto " + nome + " criado com sucesso";
                    } else {
                    	resposta = "Projeto " + nome + " já existe";
                    }
                    break;

                case "atualizar":
                    if (!banco.containsKey(nome)) {
                        banco.put(nome, new ArrayList<>());
                    }

                    String novaVersao = partes[2].trim();
                    List<String> versoesExistentes = banco.get(nome);

                    if (versoesExistentes.contains(novaVersao)) {
                        resposta = "Atualizacao ignorada. A descricao ja existe em uma versao anterior de " + nome;
                    } else {
                        versoesExistentes.add(novaVersao);
                        resposta = "O usuario 1 atualizou " + nome;
                    }
                    break;

                case "verificar versoes":
                    if (!banco.containsKey(nome)) {
                        resposta = "Nenhuma versão encontrada para " + nome;
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
                        resposta = "Nenhuma versão encontrada para " + nome;
                    } else {
                        List<String> versoes = banco.get(nome);
                        if (partes.length == 3) {
                            int v = Integer.parseInt(partes[2].trim());
                            if (v >= 1 && v <= versoes.size()) {
                                resposta = "Versão " + v + " de " + nome + ": \"" + versoes.get(v - 1) + "\"";
                            } else {
                                resposta = "Versão " + v + " não encontrada para " + nome;
                            }
                        } else {
                            int v = versoes.size();
                            resposta = "Versão " + v + " de " + nome + ": \"" + versoes.get(v - 1) + "\"";
                        }
                    }
                    break;

                default:
                    resposta = "Comando desconhecido: " + comando;
                    break;
            }

            // Enviar resposta para quem enviou o comando
            byte[] respostaBytes = ("respostaDB|" + resposta).getBytes();
            DatagramPacket respostaPacote = new DatagramPacket(respostaBytes, respostaBytes.length,
                    remetente.getAddress(), remetente.getPort());
            socket.send(respostaPacote);
        }
    }
}