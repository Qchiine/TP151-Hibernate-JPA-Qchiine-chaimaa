package com.example.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.util.MigrationExecutor;
import com.example.util.ParametresJdbc;

@DisplayName("Migration du schema par script SQL")
class MigrationExecutorTest {

    private static MigrationExecutor migrateur;

    @BeforeAll
    static void avantTout() {
        EnvironnementTest.preparer();
        migrateur = ParametresJdbc.nouveauMigrateur();
    }

    @Test
    @DisplayName("la migration ajoute la colonne description a la table des salles")
    void laMigrationAjouteLaColonneDescription() throws SQLException {
        assertTrue(migrateur.colonneExiste("salles", "description"));
    }

    @Test
    @DisplayName("la migration cree l'index unique sur l'email des utilisateurs")
    void laMigrationCreeLIndexUnique() throws SQLException {
        assertTrue(migrateur.indexExiste("idx_utilisateur_email"));
    }

    @Test
    @DisplayName("la version appliquee est tracee dans le journal des migrations")
    void laVersionAppliqueeEstTracee() throws SQLException {
        List<String> versions = migrateur.versionsAppliquees();
        assertTrue(versions.contains(EnvironnementTest.VERSION_SCHEMA), "versions trouvees : " + versions);
        assertTrue(migrateur.estAppliquee(EnvironnementTest.VERSION_SCHEMA));
        assertFalse(migrateur.estAppliquee("9.9"));
    }

    @Test
    @DisplayName("rejouer la migration ne reexecute aucune instruction")
    void rejouerLaMigrationEstSansEffet() throws SQLException {
        MigrationExecutor.Resultat resultat = migrateur.appliquerUneSeuleFois(
                EnvironnementTest.VERSION_SCHEMA, EnvironnementTest.SCRIPT_MIGRATION);

        assertTrue(resultat.isDejaAppliquee());
        assertEquals(0, resultat.getInstructionsExecutees());
    }

    @Test
    @DisplayName("le script est rejouable tel quel, sans erreur SQL")
    void leScriptResteIdempotent() throws SQLException {
        MigrationExecutor.Resultat resultat = migrateur.executeMigration(EnvironnementTest.SCRIPT_MIGRATION);

        assertEquals(3, resultat.getInstructionsExecutees());
        assertTrue(migrateur.colonneExiste("salles", "description"));
    }

    @Test
    @DisplayName("un script absent du classpath est signale explicitement")
    void unScriptAbsentEstSignale() {
        IllegalArgumentException erreur = assertThrows(IllegalArgumentException.class,
                () -> migrateur.executeMigration("db/migration/inexistant.sql"));

        assertTrue(erreur.getMessage().contains("inexistant.sql"));
    }
}
