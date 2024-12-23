//classe utilizzata per trovare le enoteche in base alla posizione
//presenta 2 metodi principali: trovare le enoteche + vicine oppure
//trovare le enoteche con una valutazione + alta su google

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class EnotecaFind {

    public static String findNearestEnoteca(double latitude, double longitude) {
        try {
            //sostituisco la virgola con il punto necessario per elaborare il link per overpass API
            String latStr = String.valueOf(latitude).replace(",", ".");
            String lonStr = String.valueOf(longitude).replace(",", ".");
            //parto da un raggio di 5km
            int radius = 5000;
            HttpClient client = HttpClient.newHttpClient();
            JSONArray elements = new JSONArray();
            List<String> enotecaResults = new ArrayList<>();

            while (radius <= 50000 && enotecaResults.size() < 3) { //continua finché non trovi 3 enoteche e il raggio non deve superare i 50 km
                //elaboro il link per overpassApi: shop=wine -> parametro per ricercare le enoteche
                String query = String.format(
                        "[out:json];node[\"shop\"=\"wine\"](around:%d,%s,%s);out;",
                        radius, latStr, lonStr);
                String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
                String url = "https://overpass-api.de/api/interpreter?data=" + encodedQuery;
                //richiesta HTTP con il link prima elaborato
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                JSONObject jsonObject = new JSONObject(response.body());
                elements = jsonObject.getJSONArray("elements");

                // Aggiungi i risultati finché non raggiungi 3-> se ci sono 10 enoteche nel raggio di 5000km prendo solo le prime 3
                for (int i = 0; i < elements.length() && enotecaResults.size() < 3; i++) {
                    JSONObject enotecaJson = elements.getJSONObject(i);
                    JSONObject tags = enotecaJson.optJSONObject("tags");  //ottieni il JSON dei tag
                    if (tags == null || !tags.has("name") || tags.getString("name").isEmpty()) {
                        continue;  // Se il tag 'name' è mancante o vuoto, salta questa enoteca
                    }

                    String name = tags.getString("name");
                    String enotecaLat = String.valueOf(enotecaJson.getDouble("lat")).replace(',','.');
                    String enotecaLon = String.valueOf(enotecaJson.getDouble("lon")).replace(',','.');
                    // Ottieni il link della posizione
                    String locationLink = getLocationLinkFromGoogle(name);
                    if (locationLink == "Link posizione non trovato.") {
                        // Se il link di Google non è trovato, crea il link con Overpass usando le coordinate
                        locationLink = String.format("https://www.google.com/maps?q=%s,%s", enotecaLat, enotecaLon);
                    }

                    // Aggiungi il risultato alla lista
                    enotecaResults.add(String.format("Nome: %s\nPosizione: %s", name, locationLink));
                }

                radius += 5000;  // Espandi il raggio

            }

            // Se sono stati trovati meno di 3 risultati
            if (!enotecaResults.isEmpty()) {
                StringBuilder responseMessage = new StringBuilder("I primi 3 risultati delle enoteche più vicine:\n\n");
                for (int i = 0; i < enotecaResults.size(); i++) {
                    responseMessage.append(String.format("Enoteca %d:\n%s\n\n", i + 1, enotecaResults.get(i)));
                }
                return responseMessage.toString();
            } else {
                return "Non ho trovato enoteche nelle vicinanze.";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "Errore durante la ricerca delle enoteche.";
        }
    }

    public static String findAndSortEnotecasByRating(double latitude, double longitude) {
        try {
            String latStr = String.valueOf(latitude).replace(",", ".");
            String lonStr = String.valueOf(longitude).replace(",", ".");
            int radius = 30000; // Raggio massimo di 30 km

            HttpClient client = HttpClient.newHttpClient();
            String query = String.format(
                    "[out:json];node[\"shop\"=\"wine\"](around:%d,%s,%s);out;",
                    radius, latStr, lonStr);
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://overpass-api.de/api/interpreter?data=" + encodedQuery;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject jsonObject = new JSONObject(response.body());
            JSONArray elements = jsonObject.getJSONArray("elements");

            // Lista di enoteche con informazioni su nome, latitudine, longitudine e valutazione
            List<Enoteca> enotecas = new ArrayList<>();
            for (int i = 0; i < elements.length() && i < 5; i++) {
                JSONObject enotecaJson = elements.getJSONObject(i);
                JSONObject tags = enotecaJson.optJSONObject("tags");

                //verifica che il tag 'name' esista prima di continuare
                if (tags == null || !tags.has("name")) {
                    continue; //salta l'iterazione se non c'è il nome
                }

                String name = tags.getString("name");
                double enotecaLat = enotecaJson.getDouble("lat");
                double enotecaLon = enotecaJson.getDouble("lon");
                System.out.println(i);
                String rating = getRatingFromGoogle(name);
                enotecas.add(new Enoteca(name, enotecaLat, enotecaLon, rating));
            }

            //ordina la lista in base alla valutazione
            enotecas.sort(Comparator.comparingDouble(Enoteca::getRatingValue).reversed());

            //costruisci il messaggio da inviare
            StringBuilder responseMessage = new StringBuilder("Enoteche ordinate per valutazione:\n\n");
            for (Enoteca enoteca : enotecas) {
                String name = enoteca.getName();
                String rating = enoteca.getRating() != null ? enoteca.getRating() : "N/D";
                String locationLink = getLocationLinkFromGoogle(name);
                responseMessage.append(String.format("Nome: %s\nValutazione: %s\nPosizione: %s\n\n",
                        name, rating, locationLink));
            }

            return responseMessage.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "Errore durante la ricerca delle enoteche.";
        }
    }

    private static String getFromGoogleSearch(String query, String selector) {
        try {
            if (query == null || query.isEmpty()) {
                return null;
            }

            String googleUrl = "https://www.google.com/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(googleUrl).get();
            Elements elements = doc.select(selector);
            if (!elements.isEmpty()) {
                return elements.text(); // Restituisce il testo estratto dal selettore
            }
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String getLocationLinkFromGoogle(String query) {
        String location = getFromGoogleSearch(query, "div.gqkR3b.hP3ybd a");
        if (location != null) {
            return "https://www.google.com" + location;
        }
        return "Link posizione non trovato.";
    }

    private static String getRatingFromGoogle(String query) {
        String rating = getFromGoogleSearch(query, ".Aq14fc");
        return rating != null ? rating : "N/D";
    }
}
