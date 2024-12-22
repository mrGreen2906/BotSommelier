// Classe per memorizzare i dati del vino
class WineData {
    String id;
    String nome;
    double prezzoEffettivo;
    String categoria;
    String annata;
    String regione;
    String cantina;
    String denominazione;
    String abbinamento;
    String uve;
    String origine;

    public WineData(String id, String nome, double prezzoEffettivo, String categoria, String annata,
                    String regione, String cantina, String denominazione, String abbinamento, String uve,
                    String origine) {
        this.id = id;
        this.nome = nome;
        this.prezzoEffettivo = prezzoEffettivo;
        this.categoria = categoria;
        this.annata = annata;
        this.regione = regione;
        this.cantina = cantina;
        this.denominazione = denominazione;
        this.abbinamento = abbinamento;
        this.uve = uve;
        this.origine = origine;
    }
}