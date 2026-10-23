package com.example.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Applique sur la base les scripts SQL places dans le classpath.
 * Les scripts sont rejouables : chaque version est enregistree dans la table
 * schema_version, ce qui evite de re-executer une migration deja passee.
 */
public class MigrationExecutor {

    private static final String TABLE_VERSION = "schema_version";

    private final String jdbcUrl;
    private final String username;
    private final String password;

    public MigrationExecutor(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    /**
     * Execute toutes les instructions du script indique, sans controle de version.
     * Le script doit rester idempotent (gardes IF NOT EXISTS) pour pouvoir etre rejoue.
     */
    public Resultat executeMigration(String scriptPath) throws SQLException {
        List<String> instructions = lireInstructions(scriptPath);
        long depart = System.nanoTime();
        try (Connection connexion = DriverManager.getConnection(jdbcUrl, username, password)) {
            connexion.setAutoCommit(false);
            try (Statement statement = connexion.createStatement()) {
                for (String instruction : instructions) {
                    statement.execute(instruction);
                }
                connexion.commit();
            } catch (SQLException e) {
                connexion.rollback();
                throw e;
            }
        }
        long duree = (System.nanoTime() - depart) / 1_000_000L;
        return new Resultat(scriptPath, instructions.size(), duree, true);
    }

    /**
     * Applique le script une seule fois : si la version est deja enregistree,
     * rien n'est execute et le resultat porte dejaAppliquee = true.
     */
    public Resultat appliquerUneSeuleFois(String version, String scriptPath) throws SQLException {
        initialiserLeJournal();
        if (estAppliquee(version)) {
            return new Resultat(scriptPath, 0, 0L, true);
        }
        Resultat resultat = executeMigration(scriptPath);
        enregistrer(version, scriptPath);
        return new Resultat(scriptPath, resultat.getInstructionsExecutees(), resultat.getDureeMs(), false);
    }

    public List<String> versionsAppliquees() throws SQLException {
        initialiserLeJournal();
        List<String> versions = new ArrayList<>();
        try (Connection connexion = DriverManager.getConnection(jdbcUrl, username, password);
             Statement statement = connexion.createStatement();
             ResultSet rs = statement.executeQuery("select version from " + TABLE_VERSION + " order by version")) {
            while (rs.next()) {
                versions.add(rs.getString(1));
            }
        }
        return versions;
    }

    public boolean estAppliquee(String version) throws SQLException {
        initialiserLeJournal();
        try (Connection connexion = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement ps = connexion.prepareStatement(
                     "select count(*) from " + TABLE_VERSION + " where version = ?")) {
            ps.setString(1, version);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /** Verifie l'existence d'une colonne, quel que soit le dialecte (via les metadonnees JDBC). */
    public boolean colonneExiste(String table, String colonne) throws SQLException {
        try (Connection connexion = DriverManager.getConnection(jdbcUrl, username, password)) {
            for (String nomTable : new String[] { table, table.toUpperCase() }) {
                try (ResultSet rs = connexion.getMetaData().getColumns(null, null, nomTable, null)) {
                    while (rs.next()) {
                        if (colonne.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    /** Verifie l'existence d'un index par son nom. */
    public boolean indexExiste(String nomIndex) throws SQLException {
        try (Connection connexion = DriverManager.getConnection(jdbcUrl, username, password)) {
            for (String table : new String[] { "salles", "utilisateurs" }) {
                for (String nomTable : new String[] { table, table.toUpperCase() }) {
                    try (ResultSet rs = connexion.getMetaData().getIndexInfo(null, null, nomTable, false, false)) {
                        while (rs.next()) {
                            if (nomIndex.equalsIgnoreCase(rs.getString("INDEX_NAME"))) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }
    }

    private void initialiserLeJournal() throws SQLException {
        try (Connection connexion = DriverManager.getConnection(jdbcUrl, username, password);
             Statement statement = connexion.createStatement()) {
            statement.execute("create table if not exists " + TABLE_VERSION
                    + " (version varchar(20) not null primary key,"
                    + " script varchar(255) not null,"
                    + " applique_le timestamp not null)");
        }
    }

    private void enregistrer(String version, String script) throws SQLException {
        try (Connection connexion = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement ps = connexion.prepareStatement(
                     "insert into " + TABLE_VERSION + " (version, script, applique_le) values (?, ?, ?)")) {
            ps.setString(1, version);
            ps.setString(2, script);
            ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();
        }
    }

    /** Decoupe le script en instructions, en ignorant les commentaires en ligne. */
    private List<String> lireInstructions(String scriptPath) {
        StringBuilder contenu = new StringBuilder();
        try (InputStream flux = getClass().getClassLoader().getResourceAsStream(scriptPath)) {
            if (flux == null) {
                throw new IllegalArgumentException("Script introuvable dans le classpath : " + scriptPath);
            }
            try (BufferedReader lecteur = new BufferedReader(
                    new InputStreamReader(flux, StandardCharsets.UTF_8))) {
                String ligne;
                while ((ligne = lecteur.readLine()) != null) {
                    int commentaire = ligne.indexOf("--");
                    contenu.append(commentaire >= 0 ? ligne.substring(0, commentaire) : ligne).append('\n');
                }
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Lecture impossible du script " + scriptPath, e);
        }

        List<String> instructions = new ArrayList<>();
        for (String morceau : contenu.toString().split(";")) {
            String instruction = morceau.trim();
            if (!instruction.isEmpty()) {
                instructions.add(instruction);
            }
        }
        return instructions;
    }

    /** Compte rendu d'une migration. */
    public static final class Resultat {

        private final String script;
        private final int instructionsExecutees;
        private final long dureeMs;
        private final boolean dejaAppliquee;

        Resultat(String script, int instructionsExecutees, long dureeMs, boolean dejaAppliquee) {
            this.script = script;
            this.instructionsExecutees = instructionsExecutees;
            this.dureeMs = dureeMs;
            this.dejaAppliquee = dejaAppliquee;
        }

        public String getScript() {
            return script;
        }

        public int getInstructionsExecutees() {
            return instructionsExecutees;
        }

        public long getDureeMs() {
            return dureeMs;
        }

        public boolean isDejaAppliquee() {
            return dejaAppliquee;
        }

        @Override
        public String toString() {
            return dejaAppliquee
                    ? script + " : deja applique, aucune instruction rejouee"
                    : script + " : " + instructionsExecutees + " instruction(s) en " + dureeMs + " ms";
        }
    }
}
