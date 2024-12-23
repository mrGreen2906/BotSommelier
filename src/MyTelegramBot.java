import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Location;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public class MyTelegramBot extends TelegramLongPollingBot {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/botvino_telegram";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "";

    private final Random random = new Random();
    private boolean awaitingConfirmation = false;
    private String suggestedDish = "";
    private final Map<String, String> filters = new HashMap<>(); // Filtri per la query SQL
    private LocalDate lastCrawlDate = LocalDate.now().minusWeeks(2);
    private double userLatitude = 0;
    private double userLongitude = 0;


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
            long chatId = update.getMessage().getChatId();
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);

            if(awaitingConfirmation){
                messageText = update.getMessage().getText();
                handleDishConfirmation(messageText, sendMessage);
            }
           else if (messageText.equalsIgnoreCase("/start")) {
                //inizia il processo di crawling e inserimento nel database

                if (LocalDate.now().isAfter(lastCrawlDate.plusWeeks(2))) {
                    DatabaseManager dbManager = new DatabaseManager();
                    WineCrawler crawler = new WineCrawler();
                    DatabaseInserter inserter = new DatabaseInserter(crawler.wineQueue, dbManager, crawler);

// Avvia il thread di inserimento nel DB
                    inserter.start();

// Esegui il crawling
                    crawler.crawlWineData();


                    sendMessage.setText("Inizio il processo di crawling e inserimento dati nel database.");
                    sendResponse(sendMessage);

                    lastCrawlDate = LocalDate.now();
                    sendMessage.setText("I dati sui vini sono stati aggiornati.");
                    sendResponse(sendMessage);
                } else {
                    sendMessage.setText("I dati sui vini sono già aggiornati.");
                    sendResponse(sendMessage);
                }




                sendOptionsMessage(chatId); //mostro i pulsanti iniziali per "Ricerca Enoteche" e "Abbina Piatto"

            } else if (messageText.startsWith("/filtro_prezzo")) {
                // Filtro per prezzo
                handlePriceFilter(messageText, sendMessage);
            } else if (messageText.startsWith("/filtro_regione")) {
                // Filtro per regione
                handleRegionFilter(messageText, sendMessage);
            } else if (messageText.startsWith("/filtro_cantina")) {
                // Filtro per cantina
                handleWineryFilter(messageText, sendMessage);
            } else if (messageText.equalsIgnoreCase("/risultati")) {
                // Mostra i risultati in base ai filtri applicati
                showResults(sendMessage);
            } else {
                // Comando non riconosciuto
                sendMessage.setText("Comando non riconosciuto. Usa il seguente comando per iniziare una conversazione:\n" +
                        "/start");
                sendResponse(sendMessage);
            }
        }

        // Gestisce i callback dei pulsanti
        if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);

            switch (callbackData) {
                case "ricerca_enoteche":
                    // Quando l'utente sceglie "Ricerca Enoteche"
                    sendTextMessage(chatId, "Per favore, inviami la tua posizione per trovare le enoteche più vicine o meglio valutate.");
                    //awaitingConfirmation = true; // Attende la posizione
                    break;

                case "abbina_piatto":
                    // Quando l'utente sceglie "Abbina Piatto"
                    sendMessage.setText("Per favore, carica una foto del piatto che desideri abbinare.");
                    sendResponse(sendMessage);
                    awaitingConfirmation = true; // Attende una foto
                    break;

                case "filtro_vicinanza":
                    // Quando l'utente sceglie di ordinare per vicinanza
                    handleVicinanza(chatId);  // Gestisce la ricerca in base alla vicinanza
                    break;

                case "filtro_valutazione":
                    // Quando l'utente sceglie di ordinare per valutazione
                    handleValutazione(chatId);  // Gestisce la ricerca in base alla valutazione
                    break;

                default:
                    sendUnknownCallbackResponse(chatId);
                    break;
            }
        }

        // Gestisce la posizione dell'utente
        if (update.hasMessage() && update.getMessage().hasLocation()) {
            Location location = update.getMessage().getLocation();
            userLatitude = location.getLatitude();
            userLongitude = location.getLongitude();

            sendTextMessage(update.getMessage().getChatId(),
                    "Posizione ricevuta! Ora puoi scegliere come ordinare le enoteche (Vicinanza o Valutazione).");

            sendFilterOptions(update.getMessage().getChatId(),0); // Mostra le opzioni "Vicinanza" e "Valutazione"
        }

        // Gestisce la foto del piatto
        if (update.hasMessage() && update.getMessage().hasPhoto()) {
            long chatId = update.getMessage().getChatId(); // Ottieni l'ID della chat

            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId); // Imposta il chatId
            sendMessage.setText("Foto ricevuta! Provo ad indovinare il piatto.");
            sendResponse(sendMessage); // Invia il messaggio di conferma

            // Simula il suggerimento casuale di un piatto
            suggestRandomDish(sendMessage); // Funzione che suggerisce un piatto casuale
            if(awaitingConfirmation){
                String messageText = update.getMessage().getText();
                handleDishConfirmation(messageText, sendMessage);
            }

            System.out.println("btooo");
            // Invita l'utente ad applicare i filtri con comandi
            sendMessage.setText("Ora puoi applicare i filtri per la tua ricerca. Usa i seguenti comandi:\n" +
                    "/filtro_prezzo <min>-<max>\n/filtro_regione <nome regione>\n/filtro_cantina <nome cantina>\n/risultati");
            sendResponse(sendMessage); // Invia il messaggio con le istruzioni sui filtri
        }


    }



    // Metodo per suggerire un piatto casuale
    private void suggestRandomDish(SendMessage sendMessage) {
        String[] dishes = {"Red meats", "Cured meats"};
        suggestedDish = dishes[random.nextInt(dishes.length)];
        sendMessage.setText("Ti propongo questo piatto: " + suggestedDish + ".\nSe ti piace, rispondi 'sì', altrimenti scrivi il piatto che preferisci.");
        awaitingConfirmation = true;
        sendResponse(sendMessage);
    }


    // Mostra i pulsanti iniziali per "Ricerca Enoteche" e "Abbina Piatto"
    private void sendOptionsMessage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Scegli un'opzione:");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Pulsante "Ricerca Enoteche"
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton button1 = new InlineKeyboardButton();
        button1.setText("Ricerca Enoteche");
        button1.setCallbackData("ricerca_enoteche");  // Impostiamo il callbackData per "Ricerca Enoteche"
        row1.add(button1);

        // Pulsante "Abbina Piatto"
        InlineKeyboardButton button2 = new InlineKeyboardButton();
        button2.setText("Abbina Piatto");
        button2.setCallbackData("abbina_piatto");  // Impostiamo il callbackData per "Abbina Piatto"
        row1.add(button2);

        rows.add(row1);
        markup.setKeyboard(rows);

        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }


    private void sendFilterOptions(long chatId, int filterType) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);

        if (filterType == 0) {
            message.setText("Scegli un filtro per le enoteche (vicinanza o valutazione):");

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            // Pulsante "Filtra per Vicinanza"
            List<InlineKeyboardButton> row1 = new ArrayList<>();
            InlineKeyboardButton button1 = new InlineKeyboardButton();
            button1.setText("Filtra per Vicinanza");
            button1.setCallbackData("filtro_vicinanza");
            row1.add(button1);

            // Pulsante "Filtra per Valutazione"
            InlineKeyboardButton button2 = new InlineKeyboardButton();
            button2.setText("Filtra per Valutazione");
            button2.setCallbackData("filtro_valutazione");
            row1.add(button2);

            rows.add(row1);
            markup.setKeyboard(rows);

            message.setReplyMarkup(markup);
        } else if (filterType == 1) {
            message.setText("Scegli un filtro per il piatto:");

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            // Pulsante "Filtra per Prezzo"
            List<InlineKeyboardButton> row1 = new ArrayList<>();
            InlineKeyboardButton button1 = new InlineKeyboardButton();
            button1.setText("Filtra per Prezzo");
            button1.setCallbackData("filtro_prezzo");
            row1.add(button1);

            // Pulsante "Filtra per Regione"
            InlineKeyboardButton button2 = new InlineKeyboardButton();
            button2.setText("Filtra per Regione");
            button2.setCallbackData("filtro_regione");
            row1.add(button2);

            // Pulsante "Filtra per Cantina"
            InlineKeyboardButton button3 = new InlineKeyboardButton();
            button3.setText("Filtra per Cantina");
            button3.setCallbackData("filtro_cantina");
            row1.add(button3);

            rows.add(row1);
            markup.setKeyboard(rows);

            message.setReplyMarkup(markup);
        }

        try {
            execute(message); // Questo invia i pulsanti
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }


    // Gestisce la vicinanza per ordinare le enoteche
    private void handleVicinanza(long chatId) {
        if (userLatitude != 0 && userLongitude != 0) {
            String nearestEnoteca = EnotecaFind.findNearestEnoteca(userLatitude, userLongitude);
            sendTextMessage(chatId, "L'enoteca più vicina è: " + nearestEnoteca);
        } else {
            sendTextMessage(chatId, "Per favore, invia la tua posizione per trovare le enoteche più vicine.");
        }
    }

    // Gestisce la valutazione per ordinare le enoteche
    private void handleValutazione(long chatId) {
        if (userLatitude != 0 && userLongitude != 0) {
            String enotecaByRating = EnotecaFind.findAndSortEnotecasByRating(userLatitude, userLongitude);
            sendTextMessage(chatId, "Le enoteche ordinate per valutazione sono:\n" + enotecaByRating);
        } else {
            sendTextMessage(chatId, "Per favore, invia la tua posizione per trovare le enoteche meglio valutate.");
        }
    }

    // Gestisce la risposta sconosciuta per i callback
    private void sendUnknownCallbackResponse(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Callback non riconosciuto. Riprova.");
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // Risponde con un messaggio di testo generico
    private void sendTextMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
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

    private void sendResponse(Update update, String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(update.getMessage().getChatId()));
        sendMessage.setText(text);
        sendResponse(sendMessage); // Richiama il metodo che accetta SendMessage
    }


}