import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.sql.*;
import java.util.*;

public class MyTelegramBot extends TelegramLongPollingBot {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/botvino_telegram";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "";

    private final Random random = new Random();
    private boolean awaitingConfirmation = false;
    private String suggestedDish = "";
    private final Map<String, String> filters = new HashMap<>(); // Filtri per la query SQL

    @Override
    public String getBotUsername() {
        return "sommerlier_24Bot";
    }

    @Override
    public String getBotToken() {
        return "7682384244:AAEGJoNMWs79EneHKA32D_er8APdpVT0vig";
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            Message message = update.getMessage();
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(String.valueOf(message.getChatId()));

            if (awaitingConfirmation) {
                handleDishConfirmation(messageText, sendMessage);
            } else if (messageText.equalsIgnoreCase("/start")) {
                suggestRandomDish(sendMessage);
            } else if (messageText.startsWith("/filtro_prezzo")) {
                handlePriceFilter(messageText, sendMessage);
            } else if (messageText.startsWith("/filtro_regione")) {
                handleRegionFilter(messageText, sendMessage);
            } else if (messageText.startsWith("/filtro_cantina")) {
                handleWineryFilter(messageText, sendMessage);
            } else if (messageText.equalsIgnoreCase("/risultati")) {
                showResults(sendMessage);
            } else {
                sendMessage.setText("Comando non riconosciuto. Usa uno dei seguenti comandi:\n" +
                        "/filtro_prezzo <min>-<max>\n/filtro_regione <nome regione>\n/filtro_cantina <nome cantina>\n/risultati");
                sendResponse(sendMessage);
            }
        }
    }

    private void suggestRandomDish(SendMessage sendMessage) {
        String[] dishes = {"Red meats", "Cured meats"};
        suggestedDish = dishes[random.nextInt(dishes.length)];
        sendMessage.setText("Ti propongo questo piatto: " + suggestedDish + ".\nSe ti piace, rispondi 'sì', altrimenti scrivi il piatto che preferisci.");
        awaitingConfirmation = true;
        sendResponse(sendMessage);
    }

    private void handleDishConfirmation(String messageText, SendMessage sendMessage) {
        if (messageText.equalsIgnoreCase("sì")) {
            sendMessage.setText("Perfetto! Ora puoi usare i comandi per filtrare la tua ricerca:\n" +
                    "/filtro_prezzo <min>-<max>\n/filtro_regione <nome regione>\n/filtro_cantina <nome cantina>\n/risultati");
            awaitingConfirmation = false;
        } else {
            suggestedDish = messageText;
            sendMessage.setText("Grazie! Userò il piatto: " + suggestedDish + ". Ora puoi usare i comandi per filtrare la tua ricerca:\n" +
                    "/filtro_prezzo <min>-<max>\n/filtro_regione <nome regione>\n/filtro_cantina <nome cantina>\n/risultati");
            awaitingConfirmation = false;
        }
        sendResponse(sendMessage);
    }

    private void handlePriceFilter(String messageText, SendMessage sendMessage) {
        String[] parts = messageText.split(" ");
        if (parts.length == 2 && parts[1].contains("-")) {
            String[] range = parts[1].split("-");
            if (range.length == 2) {
                filters.put("prezzo", "Vino.prezzo BETWEEN " + range[0] + " AND " + range[1]);
                sendMessage.setText("Filtro per prezzo applicato: " + parts[1]);
            } else {
                sendMessage.setText("Formato del comando non valido. Usa: /filtro_prezzo <min>-<max>");
            }
        } else {
            sendMessage.setText("Formato del comando non valido. Usa: /filtro_prezzo <min>-<max>");
        }
        sendResponse(sendMessage);
    }

    private void handleRegionFilter(String messageText, SendMessage sendMessage) {
        String[] parts = messageText.split(" ", 2);
        if (parts.length == 2) {
            filters.put("regione", "Regione.nome = '" + parts[1] + "'");
            sendMessage.setText("Filtro per regione applicato: " + parts[1]);
        } else {
            sendMessage.setText("Formato del comando non valido. Usa: /filtro_regione <nome regione>");
        }
        sendResponse(sendMessage);
    }

    private void handleWineryFilter(String messageText, SendMessage sendMessage) {
        String[] parts = messageText.split(" ", 2);
        if (parts.length == 2) {
            filters.put("cantina", "Cantina.nome = '" + parts[1] + "'");
            sendMessage.setText("Filtro per cantina applicato: " + parts[1]);
        } else {
            sendMessage.setText("Formato del comando non valido. Usa: /filtro_cantina <nome cantina>");
        }
        sendResponse(sendMessage);
    }

    private void showResults(SendMessage sendMessage) {
        StringBuilder query = new StringBuilder("SELECT Vino.nome, Vino.prezzo, Vino.annata, Cantina.nome AS cantina " +
                "FROM Vino " +
                "INNER JOIN Cantina ON Vino.id_cantina = Cantina.id_cantina " +
                "INNER JOIN Regione ON Cantina.id_regione = Regione.id_regione");

        if (!filters.isEmpty()) {
            query.append(" WHERE ").append(String.join(" AND ", filters.values()));
        }
        query.append(" LIMIT 3");

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query.toString())) {

            if (!rs.isBeforeFirst()) {
                sendMessage.setText("Nessun vino trovato con i filtri selezionati.");
            } else {
                StringBuilder results = new StringBuilder("Ecco i primi 3 vini trovati:\n");
                while (rs.next()) {
                    results.append(String.format("Vino: %s\nPrezzo: %.2f\nAnnata: %d\nCantina: %s\n\n",
                            rs.getString("nome"),
                            rs.getDouble("prezzo"),
                            rs.getInt("annata"),
                            rs.getString("cantina")));
                }
                sendMessage.setText(results.toString());
            }
        } catch (SQLException e) {
            e.printStackTrace();
            sendMessage.setText("Errore nel recupero dei dati dal database.");
        }
        sendResponse(sendMessage);
    }

    private void sendResponse(SendMessage sendMessage) {
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

}