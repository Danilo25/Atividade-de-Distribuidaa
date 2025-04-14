package udp;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class APIGatewayUDP {
    private final int port = 4000;
    private DatagramSocket socket;

    private final Map<String, InetSocketAddress> servidores = new ConcurrentHashMap<>();
    private InetSocketAddress bd = null;
    private String servidorPrincipalId = null;

    private final ExecutorService requestProcessor = Executors.newFixedThreadPool(2);
    private final ScheduledExecutorService pingScheduler = Executors.newSingleThreadScheduledExecutor();

    public void start() throws IOException {
        socket = new DatagramSocket(port);
        System.out.println("API Gateway iniciado na porta " + port);

        // Inicia verificação periódica dos servidores e BD
        iniciarVerificacaoPeriodica();

        byte[] buffer = new byte[1024];

        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);

            byte[] receivedData = Arrays.copyOf(packet.getData(), packet.getLength());
            InetAddress clientAddress = packet.getAddress();
            int clientPort = packet.getPort();

            requestProcessor.submit(() -> processarMensagem(receivedData, clientAddress, clientPort));
        }
    }

    private void processarMensagem(byte[] data, InetAddress address, int port) {
        String message = new String(data);
        InetSocketAddress sender = new InetSocketAddress(address, port);

        System.out.println("Recebido: " + message + " de " + sender);
        System.out.println("Thread: " + Thread.currentThread().getId() + " executando a requisição");

        String[] partes = message.split(" ");

        if (partes.length >= 2) {
            String origem = partes[0];

            if (origem.equalsIgnoreCase("Servidor")) {
                if (partes.length >= 5 && partes[1].equalsIgnoreCase("Requisição")) {
                    String id = partes[2];
                    String ip = partes[3];
                    int porta = Integer.parseInt(partes[4]);

                    InetSocketAddress servidorEndereco = new InetSocketAddress(ip, porta);
                    servidores.put(id, servidorEndereco);

                    if (servidorPrincipalId == null) {
                        servidorPrincipalId = id;
                        System.out.println(">> Servidor PRINCIPAL definido: " + id);
                    }

                    System.out.println("[Servidor] Registrado ID: " + id + " => " + servidorEndereco);

                    if (bd != null) {
                        enviarMensagem("API Requisicao Database " + bd.getHostString() + ":" + bd.getPort(), servidorEndereco);
                        System.out.println("[INFO] BD enviado para novo servidor: " + id);
                    }
                }

            } else if (origem.equalsIgnoreCase("BD")) {
                if (partes.length >= 4 && partes[1].equalsIgnoreCase("Requisição")) {
                    String ip = partes[2];
                    int porta = Integer.parseInt(partes[3]);

                    bd = new InetSocketAddress(ip, porta);
                    System.out.println("[BD] Registrado endereço do banco de dados: " + bd);
                    notificarServidoresBD();
                }

            } else if (origem.equalsIgnoreCase("pong")) {
                System.out.println("Recebido PONG de: " + sender);

            } else {
                String mensagemOriginal = new String(data);
                String msgFormatada = "API Requisicao Cliente " + address.getHostAddress() + ":" + port + " Mensagem: " + mensagemOriginal;

                System.out.println("[CLIENTE] Requisição recebida: " + mensagemOriginal);
                System.out.println("[CLIENTE] Formatando e repassando para o servidor principal: " + msgFormatada);

                if (servidorPrincipalId != null) {
                    InetSocketAddress servidorPrincipal = servidores.get(servidorPrincipalId);
                    if (servidorPrincipal != null) {
                        enviarMensagem(msgFormatada, servidorPrincipal);
                    } else {
                        System.out.println("Servidor principal não encontrado.");
                    }
                }
            }
        }

        String resposta = "Sucesso: A requisição foi recebida e processada com sucesso!";
        byte[] respostaBytes = resposta.getBytes();
        DatagramPacket reply = new DatagramPacket(respostaBytes, respostaBytes.length, address, port);

        try {
            socket.send(reply);
        } catch (IOException e) {
            System.out.println("Erro ao enviar resposta para " + sender + ": " + e.getMessage());
        }
    }

    private void enviarMensagem(String mensagem, InetSocketAddress destino) {
        byte[] data = mensagem.getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, destino);
        try {
            socket.send(packet);
        } catch (IOException e) {
            System.out.println("Erro ao enviar mensagem para " + destino + ": " + e.getMessage());
        }
    }

    private void notificarServidoresBD() {
        if (bd != null) {
            String msg = "API Requisicao Database " + bd.getHostString() + ":" + bd.getPort();
            for (InetSocketAddress servidor : servidores.values()) {
                enviarMensagem(msg, servidor);
            }
        }
    }

    private void notificarQuedaBD() {
        String msg = "API Requisicao Database queda";
        for (InetSocketAddress servidor : servidores.values()) {
            enviarMensagem(msg, servidor);
        }
    }

    private void iniciarVerificacaoPeriodica() {
        pingScheduler.scheduleAtFixedRate(() -> {
            // Verificar banco de dados
            if (bd != null) {
                enviarMensagem("ping", bd);
                System.out.println("[PING] Enviado para BD: " + bd);
            }

            // Verificar todos os servidores
            for (Map.Entry<String, InetSocketAddress> entry : servidores.entrySet()) {
                enviarMensagem("ping", entry.getValue());
                System.out.println("[PING] Enviado para Servidor " + entry.getKey() + ": " + entry.getValue());
            }

        }, 0, 5, TimeUnit.SECONDS); // a cada 5 segundos
    }

    public static void main(String[] args) throws IOException {
        new APIGatewayUDP().start();
    }
}