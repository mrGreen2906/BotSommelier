public class Enoteca {
    private String name;
    private double latitude;
    private double longitude;
    private String rating;

    public Enoteca(String name, double latitude, double longitude, String rating) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.rating = rating;
    }

    public String getName() {
        return name;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getRating() {
        return rating;
    }

    public double getRatingValue() {
        if (rating == null || rating.equals("N/D")) {
            return Double.MIN_VALUE; // Valutazione mancante, usa il valore più basso
        }

        // Sostituisci la virgola con il punto se necessario
        rating = rating.replace(",", ".");

        try {
            return Double.parseDouble(rating); // Restituisci il valore numerico
        } catch (NumberFormatException e) {
            return Double.MIN_VALUE; // Se la valutazione non è valida, restituisci un valore basso
        }
    }
}
