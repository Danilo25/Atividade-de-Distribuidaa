package main.UDP;

import java.io.*;
import java.net.*;
import java.util.*;

public class UDPTemperatureServer {
    private static final int SERVER_PORT = 5005;
    private static final String LOG_FILE = "log.txt";
    private static Map<String, List<VersionedValue>> temperatureDatabase = new HashMap<>();

    public static void main(String[] args) {
        // Restaurar os dados do log ao iniciar
        loadFromLog();

        try (DatagramSocket serverSocket = new DatagramSocket(SERVER_PORT)) {
            System.out.println("Servidor de Temperaturas rodando na porta " + SERVER_PORT);

            while (true) {
                byte[] receiveBuffer = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                serverSocket.receive(receivePacket);

                String receivedData = new String(receivePacket.getData(), 0, receivePacket.getLength());
                InetAddress clientAddress = receivePacket.getAddress();
                int clientPort = receivePacket.getPort();

                String response = processRequest(receivedData);
                byte[] sendBuffer = response.getBytes();

                DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, clientAddress, clientPort);
                serverSocket.send(sendPacket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String processRequest(String request) {
        String[] parts = request.split(";");
        if (parts.length < 2) return "Erro: Formato inválido";

        String action = parts[0];
        String city = parts[1];

        if (action.equals("store") && parts.length == 4) {
            String temperature = parts[2];
            long version = Long.parseLong(parts[3]);
            return storeTemperature(city, temperature, version);
        } else if (action.equals("retrieve")) {
            return retrieveTemperature(city, parts.length == 3 ? parts[2] : null);
        }
        return "Erro: Ação inválida";
    }

    private static String storeTemperature(String city, String temperature, long version) {
        temperatureDatabase.putIfAbsent(city, new ArrayList<>());
        temperatureDatabase.get(city).add(new VersionedValue(version, temperature));

        // 🔹 Escreve a operação no log antes de aplicá-la
        appendToLog("store;" + city + ";" + temperature + ";" + version);

        return "Temperatura registrada: " + temperature + " (versão " + version + ")";
    }

    private static String retrieveTemperature(String city, String version) {
        List<VersionedValue> values = temperatureDatabase.getOrDefault(city, new ArrayList<>());
        if (values.isEmpty()) return "Nenhum dado encontrado para " + city;

        if (version == null) {
            return values.toString(); // Retorna todas as versões
        }

        try {
            long requestedVersion = Long.parseLong(version);
            for (VersionedValue v : values) {
                if (v.version == requestedVersion) {
                    return v.toString(); // Retorna versão específica
                }
            }
        } catch (NumberFormatException e) {
            return "Erro: Formato de versão inválido.";
        }

        return "Versão não encontrada para " + city;
    }

    /** 
     * 🔹 Registra a operação no log para recuperação futura 
     */
    private static void appendToLog(String logEntry) {
        try (FileWriter writer = new FileWriter(LOG_FILE, true);
             BufferedWriter bw = new BufferedWriter(writer)) {
            bw.write(logEntry);
            bw.newLine();
        } catch (IOException e) {
            System.err.println("Erro ao escrever no log: " + e.getMessage());
        }
    }

    /** 
     * 🔹 Restaura o banco de dados a partir do log ao iniciar 
     */
    private static void loadFromLog() {
        File file = new File(LOG_FILE);
        if (!file.exists()) return; // Nenhum log para processar

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                processRequest(line); // Reexecuta os comandos do log
            }
            System.out.println("📄 Dados restaurados do log.");
        } catch (IOException e) {
            System.err.println("Erro ao ler o log: " + e.getMessage());
        }
    }

    static class VersionedValue {
        long version;
        String temperature;

        public VersionedValue(long version, String temperature) {
            this.version = version;
            this.temperature = temperature;
        }

        @Override
        public String toString() {
            return "{versão: " + version + ", temperatura: '" + temperature + "'}";
        }
    }
}