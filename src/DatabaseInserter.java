import org.jsoup.Jsoup;          // Per l'analisi del DOM HTML
import org.jsoup.nodes.Document; // Per rappresentare la pagina HTML
import org.jsoup.nodes.Element;  // Per manipolare i singoli elementi HTML
import org.jsoup.select.Elements; // Per selezionare più elementi
import java.io.IOException;     // Per gestire le eccezioni di I/O (per esempio, se il sito non è raggiungibile)
import java.util.concurrent.BlockingQueue;


public class DatabaseInserter extends Thread {
    private final BlockingQueue<WineData> wineQueue;
    private final DatabaseManager dbManager;

    public DatabaseInserter(BlockingQueue<WineData> wineQueue, DatabaseManager dbManager) {
        this.wineQueue = wineQueue;
        this.dbManager = dbManager;
    }

    @Override
    public void run() {
        while (true) {
            try {
                // Prende il vino dalla coda
                WineData wineData = wineQueue.take();

                // Inserisci il vino nel database
                dbManager.SaveInfo(wineData.id, wineData.nome, wineData.prezzoEffettivo, wineData.categoria,
                        wineData.annata, wineData.regione, wineData.cantina, wineData.denominazione,
                        wineData.abbinamento, wineData.uve, wineData.origine);

                System.out.println("Vino " + wineData.nome + " inserito nel database.");
            } catch (InterruptedException e) {
                System.err.println("Errore durante l'estrazione dalla coda: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }
}
