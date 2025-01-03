//La classe WineCrawler è responsabile del processo di crawling
// (raccoglimento in una coda) dei dati sui vini da un sito web.

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;
import org.jsoup.Jsoup;          // Per l'analisi del DOM HTML
import org.jsoup.nodes.Document; // Per rappresentare la pagina HTML
import org.jsoup.nodes.Element;  // Per manipolare i singoli elementi HTML
import org.jsoup.select.Elements; // Per selezionare più elementi
import java.io.IOException;     // Per gestire le eccezioni di I/O (per esempio, se il sito non è raggiungibile)


public class WineCrawler {
    private volatile boolean isRunning = true; // Flag per indicare se il crawling è in esecuzione
    private static final String BASE_URL = "https://www.signorvino.com"; // URL di base del sito da cui fare il crawling
    private static final String WINE_LIST_URL = BASE_URL + "/it/vini/"; // URL della lista dei vini
    private DatabaseManager dbManager; // Oggetto per gestire il database
    BlockingQueue<WineData> wineQueue; // Coda per la gestione dei dati dei vini da processare
    private final ReentrantLock lock = new ReentrantLock();// Lock per garantire accesso sicuro ai dati condivisi

    // Costruttore della classe, inizializza il database e la coda
    public WineCrawler() {
        this.dbManager = new DatabaseManager();
        this.wineQueue = new LinkedBlockingQueue<>();
    }

    //funzione principale per eseguire il crawling dei vini
    public void crawlWineData() {
        int maxPages = MaxPagina();  // Numero massimo di pagine da analizzare
        for (int page = 1; page <= maxPages; page++) {
            try {
                // Costruisce l'URL per la pagina corrente
                String url = WINE_LIST_URL + "?start=" + (page - 1) * 20;
                System.out.println("Analisi della pagina: " + url);
                // Analizza il DOM della pagina corrente
                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/85.0.4183.121 Safari/537.36")
                        .get();

                // Seleziona i contenitori dei vini dalla pagina
                Elements wineContainers = doc.select("div.image-container > div.analytics.productListingItem");
                System.out.println("Trovati " + wineContainers.size() + " vini.");

                for (Element container : wineContainers) {
                    String category = container.attr("data-item_category");
                    String name = container.attr("data-item_name");
                    if (category.equals("sgv_wines_beers") || category.equals("sgv_wines_accessories") || name.toLowerCase().contains("box")) {
                        // Se la categoria è birra o accessori o contiene "box", salta questo elemento
                        continue;
                    }
                    // Estrai il link ai dettagli del vino
                    String detailLink = BASE_URL + container.parent().selectFirst("a[itemprop=url]").attr("href");
                    System.out.println("Categoria trovata: " + category);

                    // Estrai i dettagli del vino dalla pagina di dettaglio
                    WineData wineData = extractWineDetails(detailLink);
                    if (wineData != null) {
                        try {
                            lock.lock(); // Acquisisci il lock
                            // Aggiungi il vino alla coda
                            wineQueue.put(wineData);
                            System.out.println("Vino aggiunto alla coda: " + wineData.nome);
                        } catch (InterruptedException e) {
                            System.err.println("Errore durante l'inserimento nella coda: " + e.getMessage());
                            Thread.currentThread().interrupt();
                        } finally {
                            lock.unlock(); // Rilascia il lock
                        }
                    }
                }

            } catch (IOException e) {
                System.err.println("Errore durante il crawling: " + e.getMessage());
            }
        }
        stopCrawling();
        System.out.println("Crawling completato. Nessun'altra pagina da analizzare.");
    }

    // Funzione per estrarre i dettagli del vino dalla pagina di dettaglio
    private WineData extractWineDetails(String detailLink) {
        try {
            // Analizza il DOM della pagina di dettaglio del vino
            Document doc = Jsoup.connect(detailLink).get();

            Element productElement = doc.selectFirst("div.analytics.product.hidden");
            if (productElement != null) {
                // Estrai i dettagli dal prodotto
                String id = productElement.attr("data-item_id");
                String nome = productElement.attr("data-item_name");
                double prezzoBase = Double.parseDouble(productElement.attr("data-price"));
                double sconto = Double.parseDouble(productElement.attr("data-discount"));
                String categoria = productElement.attr("data-item_category");
                String annata = productElement.attr("data-item_category2");

                String regione = productElement.attr("data-item_category4");
                String cantina = productElement.attr("data-item_category5");
                String denominazione = productElement.attr("data-item_variant");
                double quantità = Double.parseDouble(productElement.attr("data-quantity"));
                String abbinamento = productElement.attr("data-item_suggested_food");
                String uve = productElement.attr("data-item_vine_variety");
                String origine = productElement.attr("data-item_origin");

                double prezzoEffettivo = prezzoBase - (prezzoBase * sconto / 100.0);

                if (quantità >= 1.0) {
                    // Restituisci un oggetto WineData con i dettagli del vino se la quantità è valida
                    return new WineData(id, nome, prezzoEffettivo, categoria, annata, regione, cantina, denominazione, abbinamento, uve, origine);
                } else {
                    System.out.println("Prodotto non valido: quantità insufficiente.");
                }
            } else {
                System.out.println("Elemento prodotto non trovato.");
            }

        } catch (IOException e) {
            System.err.println("Errore durante il caricamento dei dettagli: " + e.getMessage());
        }
        return null; // Se i dettagli non possono essere estratti, restituisce null
    }

    //Funzione per ottenere il numero massimo di pagine da analizzare
    public int MaxPagina(){
        int pagina = 0;
        try {
            // URL del sito
            String url = "https://www.signorvino.com/it/vini/";

            // Effettua il parsing della pagina web
            Document doc = Jsoup.connect(url).get();

            // Seleziona il div con la classe "plpPage-pagination-text"
            Element paginationDiv = doc.selectFirst("div.plpPage-pagination-text");

            // Seleziona tutti i link all'interno del div con classe "paginationLink"
            Elements paginationLinks = paginationDiv.select("a.paginationLink");

            // Estrai il numero visibile dell'ultimo link
            Element lastLink = paginationLinks.last();
            String visibleNumber = lastLink.text();

            // Stampa il risultato
            System.out.println("Ultimo numero visibile: " + visibleNumber);
            pagina = Integer.parseInt(visibleNumber);
        } catch (Exception e) {
            System.out.println("Attenzione errore nella ricerca dell'ultima pagina del sito signorvino");;
        }
        return pagina;
    }

    // Funzione per fermare il crawling
    public void stopCrawling() {
        isRunning = false;
    }

    // Funzione per verificare se il crawling è ancora in corso
    public boolean isRunning() {
        return isRunning;
    }

}
