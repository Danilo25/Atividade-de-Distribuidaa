package udp;

import java.io.IOException;
import java.net.*;

public class ServidorUDP {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Uso: java ServidorUDP <porta>");
            return;
        }

        int porta = Integer.parseInt(args[0]);
        DatagramSocket socket = new DatagramSocket(porta);
        String servidorId = "Servidor_" + porta;

        // Endereço do Gateway (localhost)
        InetAddress apiAddress = InetAddress.getByName("localhost");
        int apiPorta = 4000;

        // Envia mensagem de registro para o API Gateway
        String ipLocal = InetAddress.getLocalHost().getHostAddress();  // Pode ser 'localhost' ou '127.0.0.1'
        String msgRegistro = "Servidor Requisição " + servidorId + " " + ipLocal + " " + porta;
        enviarMensagem(socket, msgRegistro, apiAddress, apiPorta);

        System.out.println("Servidor UDP iniciado na porta " + porta + " com ID: " + servidorId);

        byte[] buffer = new byte[1024];

        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);

            String mensagem = new String(packet.getData(), 0, packet.getLength());
            InetAddress remetenteAddr = packet.getAddress();
            int remetentePort = packet.getPort();

            System.out.println("Recebido da API: " + mensagem);

            if (mensagem.equalsIgnoreCase("ping")) {
                // Imprime o endereço do remetente que enviou o ping
                System.out.println("[INFO] Ping recebido de: " + remetenteAddr + ":" + remetentePort);

                System.out.println("[INFO] Enviando resposta pong para: " + remetenteAddr + ":" + remetentePort);
                enviarMensagem(socket, "pong", remetenteAddr, remetentePort);
            } else if (mensagem.startsWith("API Requisicao")) {
                // Imprime o conteúdo
                System.out.println("[INFO] Mensagem da API: " + mensagem);

                // Responde "OK"
                enviarMensagem(socket, "OK", remetenteAddr, remetentePort);
            }
        }
    }

    private static void enviarMensagem(DatagramSocket socket, String mensagem, InetAddress destino, int porta) {
        byte[] dados = mensagem.getBytes();
        DatagramPacket packet = new DatagramPacket(dados, dados.length, destino, porta);
        try {
            socket.send(packet);
        } catch (IOException e) {
            System.out.println("Erro ao enviar mensagem: " + e.getMessage());
        }
    }
}