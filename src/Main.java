public class Main {
    public static void main(String[] args) {
        DatabaseManager dbManager = new DatabaseManager();
        WineCrawler crawler = new WineCrawler();
        DatabaseInserter inserter = new DatabaseInserter(crawler.wineQueue, dbManager);

        // Avvia il thread di inserimento nel DB
        inserter.start();

        // Esegui il crawling
        crawler.crawlWineData();
    }
}
