//questa classe è un thread separato che aspetta i dati nella coda e,
// una volta che i dati sono disponibili, li inserisce nel database.

// Importazione delle librerie necessarie per la gestione della coda
import java.util.concurrent.BlockingQueue;


public class DatabaseInserter extends Thread {
    private final BlockingQueue<WineData> wineQueue; // Coda condivisa contenente i dati dei vini da inserire nel database
    private final DatabaseManager dbManager; // Gestore del database, per eseguire le operazioni di salvataggio
    private final WineCrawler crawler; // Riferimento al crawler per verificare se il processo di crawling è ancora attivo

    public DatabaseInserter(BlockingQueue<WineData> wineQueue, DatabaseManager dbManager, WineCrawler crawler) {
        this.wineQueue = wineQueue;
        this.dbManager = dbManager;
        this.crawler = crawler;
    }

    // Override del metodo run() che definisce il comportamento del thread
    @Override
    public void run() {
        // Continuare finché il crawler è in esecuzione o la coda non è vuota
        while (crawler.isRunning() || !wineQueue.isEmpty()) {
            try {
                // Prende il vino dalla coda
                WineData wineData = wineQueue.take();

                // Inserisci il vino nel database
                dbManager.SaveInfo(wineData.id, wineData.nome, wineData.prezzoEffettivo, wineData.categoria,
                        wineData.annata, wineData.regione, wineData.cantina, wineData.denominazione,
                        wineData.abbinamento, wineData.uve, wineData.origine);

                System.out.println("Vino " + wineData.nome + " inserito nel database.");
            } catch (InterruptedException e) {
                // Gestisce le interruzioni del thread. Se il thread è interrotto, mostra un messaggio di errore e lo interrompe
                System.err.println("Errore durante l'estrazione dalla coda: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }
}
