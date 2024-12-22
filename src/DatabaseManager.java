import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/botvino_telegram";
    private static final String USER = "root"; // Cambia con il tuo utente
    private static final String PASSWORD = ""; // Cambia con la tua password

    private Connection connection;

    public DatabaseManager() {
        try {
            connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
            resetDatabase();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Impossibile connettersi al database.");
        }
    }

    public static void resetDatabase() {
        String[] tables = {"coltivare", "comporre", "vino", "cantina", "regione", "uve"}; // Aggiungi qui i nomi delle tabelle

        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASSWORD)) {
            Statement stmt = conn.createStatement();
            stmt.execute("SET FOREIGN_KEY_CHECKS = 0;");
            // Per ogni tabella, esegui la query di TRUNCATE per rimuovere tutti i dati
            for (String table : tables) {
                String sql = "TRUNCATE TABLE " + table;
                stmt.executeUpdate(sql);
                System.out.println("Tabella " + table + " svuotata.");
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void SaveInfo(String id, String nome, double prezzoEffettivo, String categoria, String annata, String regione, String cantina, String denominazione, String abbinamento, String uve, String origine) {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASSWORD)) {

            // 1. Ottieni o inserisci l'ID della regione
            int regioneId = getRegioneId(conn, regione);
            if (regioneId == -1) {
                regioneId = insertRegione(conn, regione);
            }

            // 2. Ottieni o inserisci le uve e aggiorna la tabella 'coltivare'
            String[] uveArray = uve.split(",");
            for (String uva : uveArray) {
                uva = uva.trim();
                int uveId = getOrInsertUve(conn, uva);

                // Inserisci nella tabella 'coltivare'
                insertColtivare(conn, uveId, regioneId, denominazione);
            }

            // 3. Ottieni o inserisci la cantina
            int cantinaId = getCantinaId(conn, cantina);
            if (cantinaId == -1) {
                cantinaId = insertCantina(conn, cantina, regione);
            }

            // 4. Inserisci il vino nella tabella 'vino' e ottieni il suo ID
            String insertWine;
            PreparedStatement ps;
            int vinoId = -1; // ID del vino appena inserito

            if (annata.equalsIgnoreCase("s.a.")) {
                // Se l'annata è "s.a.", non viene inclusa nella query
                insertWine = "INSERT INTO vino (nome, prezzo, abbinamento, id_cantina) VALUES (?, ?, ?, ?)";
                ps = conn.prepareStatement(insertWine, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, nome);
                ps.setDouble(2, prezzoEffettivo);
                ps.setString(3, abbinamento);
                ps.setInt(4, cantinaId);
            } else {
                // Altrimenti, includiamo l'annata nella query
                insertWine = "INSERT INTO vino (nome, prezzo, annata, abbinamento, id_cantina) VALUES (?, ?, ?, ?, ?)";
                ps = conn.prepareStatement(insertWine, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, nome);
                ps.setDouble(2, prezzoEffettivo);
                ps.setInt(3, Integer.parseInt(annata));
                ps.setString(4, abbinamento);
                ps.setInt(5, cantinaId);
            }

            ps.executeUpdate();

            // Ottieni l'ID del vino appena inserito
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                vinoId = rs.getInt(1);
            }

            // 5. Inserisci i collegamenti nella tabella 'comporre'
            if (vinoId != -1) {
                for (String uva : uveArray) {
                    uva = uva.trim();
                    int uveId = getOrInsertUve(conn, uva);

                    // Inserisci nella tabella 'comporre'
                    insertComporre(conn, vinoId, uveId, "Principale"); // Ruolo di default
                }
            }

            System.out.println("Informazioni sul vino salvate con successo!");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }



    // Metodo per ottenere l'ID delle uve o inserirle se non esistono
    private int getOrInsertUve(Connection conn, String uva) throws SQLException {
        int uveId = getUveId(conn, uva);
        if (uveId == -1) {
            insertUve(conn, uva);
            uveId = getUveId(conn, uva); // Riprova ad ottenere l'ID dopo l'inserimento
        }
        return uveId;
    }

    // Metodo per controllare se il record esiste già nella tabella 'coltivare'
    private boolean existsInColtivare(Connection conn, int uvaId, int regioneId, String denominazione) throws SQLException {
        String query = "SELECT 1 FROM coltivare WHERE id_uva = ? AND id_regione = ?";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, uvaId);
            ps.setInt(2, regioneId);
            //ps.setString(3, denominazione);
            ResultSet rs = ps.executeQuery();
            return rs.next();  // Se troviamo un record, significa che esiste già
        }
    }

    // Modifica del metodo per inserire nella tabella 'coltivare' con controllo di duplicati
    private void insertColtivare(Connection conn, int uvaId, int regioneId, String denominazione) throws SQLException {
        if (!existsInColtivare(conn, uvaId, regioneId, denominazione)) {
            String insertColtivare = "INSERT INTO coltivare (id_uva, id_regione, denominazione) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertColtivare)) {
                ps.setInt(1, uvaId);
                ps.setInt(2, regioneId);
                ps.setString(3, denominazione);  // Usa la denominazione passata dal crawler
                ps.executeUpdate();
                System.out.println("Record coltivare inserito con successo.");
            }
        } else {
            System.out.println("Il record coltivare esiste già, non verrà inserito.");
        }
    }

    //va riformulata
    // Metodo per inserire nella tabella 'comporre' (aggiunto l'ID del vino)
    private void insertComporre(Connection conn, int vinoId, int uvaId, String ruolo) throws SQLException {
        String insertComporre = "INSERT INTO comporre (id_vino, id_uva, ruolo) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertComporre)) {
            ps.setInt(1, vinoId);  // ID del vino
            ps.setInt(2, uvaId);   // ID dell'uva
            ps.setString(3, ruolo); // Ruolo dell'uva
            ps.executeUpdate();
        }
    }



    // Metodo per ottenere l'ID della regione
    private int getRegioneId(Connection conn, String regione) throws SQLException {
        String query = "SELECT id_regione FROM regione WHERE nome = ?";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, regione);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("id_regione");
            } else {
                return -1;  // Regione non trovata
            }
        }
    }

    // Metodo per inserire una regione
    private int insertRegione(Connection conn, String regione) throws SQLException {
        String insertQuery = "INSERT INTO regione (nome) VALUES (?)";
        try (PreparedStatement ps = conn.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, regione);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);  // Restituisce l'ID della regione appena inserita
            }
        }
        return -1;
    }

    // Metodo per ottenere l'ID delle uve
    private int getUveId(Connection conn, String uva) throws SQLException {
        String query = "SELECT id_uva FROM uve WHERE nome = ?";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uva);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("id_uva");
            } else {
                return -1;  // Uva non trovata
            }
        }
    }

    // Metodo per inserire le uve
    private void insertUve(Connection conn, String uva) throws SQLException {
        String insertQuery = "INSERT INTO uve (nome) VALUES (?)";
        try (PreparedStatement ps = conn.prepareStatement(insertQuery)) {
            ps.setString(1, uva);
            ps.executeUpdate();
        }
    }

    // Metodo per ottenere l'ID della cantina
    private int getCantinaId(Connection conn, String cantina) throws SQLException {
        String query = "SELECT id_cantina FROM cantina WHERE nome = ?";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, cantina);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("id_cantina");
            } else {
                return -1;  // Cantina non trovata
            }
        }
    }

    // Metodo per inserire la cantina
    private int insertCantina(Connection conn, String cantina, String regione) throws SQLException {
        // 1. Ottieni l'ID della regione
        int regioneId = getRegioneId(conn, regione);

        // 2. Se la regione non esiste, la inserisci
        if (regioneId == -1) {
            regioneId = insertRegione(conn, regione);  // Ora abbiamo un ID valido per la regione
        }

        // 3. Ora inseriamo la cantina con l'ID della regione
        String insertQuery = "INSERT INTO cantina (nome, id_regione) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, cantina);
            ps.setInt(2, regioneId);  // Inseriamo l'ID della regione
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);  // Restituisce l'ID della cantina appena inserita
            }
        }
        return -1;
    }


    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


}
