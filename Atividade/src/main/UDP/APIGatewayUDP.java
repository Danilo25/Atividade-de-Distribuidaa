package main.UDP;

import java.io.IOException;
import java.net.*;

public class APIGatewayUDP {
    private static final int GATEWAY_PORT = 4000;
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5005;
    private static final int TIMEOUT_MS = 3000; // Aumentado para evitar timeouts do JMeter

    public static void main(String[] args) {
        try (DatagramSocket gatewaySocket = new DatagramSocket(GATEWAY_PORT)) {
            System.out.println("API Gateway rodando na porta " + GATEWAY_PORT);

            while (true) {
                byte[] receiveBuffer = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                gatewaySocket.receive(receivePacket);

                String request = new String(receivePacket.getData(), 0, receivePacket.getLength());
                System.out.println("📩 Requisição recebida do JMeter: " + request);

                if (!isValidRequest(request)) {
                    String errorMsg = "Erro: Formato inválido. Use store;cidade;temp;versão ou retrieve;cidade[;versão]";
                    sendResponse(gatewaySocket, receivePacket.getAddress(), receivePacket.getPort(), errorMsg);
                    continue;
                }

                String response = forwardRequestToServer(request);
                sendResponse(gatewaySocket, receivePacket.getAddress(), receivePacket.getPort(), response);
            }
        } catch (IOException e) {
            System.err.println("❌ Erro no API Gateway: " + e.getMessage());
        }
    }

    private static String forwardRequestToServer(String request) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(TIMEOUT_MS);

            byte[] sendBuffer = request.getBytes();
            InetAddress serverAddress = InetAddress.getByName(SERVER_ADDRESS);
            DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, serverAddress, SERVER_PORT);
            socket.send(sendPacket);

            byte[] receiveBuffer = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);

            socket.receive(receivePacket);
            String response = new String(receivePacket.getData(), 0, receivePacket.getLength());

            if (response.isEmpty()) {
                return "Erro: Servidor retornou uma resposta vazia.";
            }

            return response;
        } catch (SocketTimeoutException e) {
            return "Erro: Servidor de Registro não respondeu dentro do tempo limite.";
        } catch (IOException e) {
            return "Erro ao comunicar com o servidor.";
        }
    }

    private static void sendResponse(DatagramSocket socket, InetAddress address, int port, String message) throws IOException {
        byte[] sendBuffer = message.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, address, port);
        socket.send(sendPacket);
        System.out.println("📤 Resposta enviada ao JMeter: " + message);
    }

    private static boolean isValidRequest(String request) {
        String[] parts = request.split(";");
        if (parts.length < 2) return false;

        String action = parts[0];
        if (action.equals("store") && parts.length == 4) return true;
        if (action.equals("retrieve") && (parts.length == 2 || parts.length == 3)) return true;

        return false;
    }
}