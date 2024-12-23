//Avvio di un bot Telegram
// La logica del bot è contenuta nella classe MyTelegramBot

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {
    public static void main(String[] args) {
        try {
            // Creazione di un'istanza di TelegramBotsApi utilizzando la sessione predefinita
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            // Registrazione del bot Telegram: 'MyTelegramBot' è la classe che definisce la logica del bot
            botsApi.registerBot(new MyTelegramBot());
        } catch (TelegramApiException e) {
            System.out.println("Attenzione ERRORE durante la registrazione del bot");;
        }

    }
}
