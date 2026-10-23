package com.example.test;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.persistence.EntityManager;

import com.example.model.Salle;
import com.example.model.Utilisateur;
import com.example.util.DataInitializer;
import com.example.util.ParametresJdbc;
import com.example.util.Persistance;

/**
 * Prepare une base unique pour toute la campagne de tests : la base memoire
 * H2 vit aussi longtemps que la JVM, le jeu de donnees n'est donc cree qu'une
 * seule fois, quel que soit le nombre de classes de test.
 */
final class EnvironnementTest {

    static final String SCRIPT_MIGRATION = "db/migration/v2_0_add_description.sql";
    static final String VERSION_SCHEMA = "2.0";

    private static final AtomicInteger COMPTEUR = new AtomicInteger();

    private static volatile DataInitializer jeu;

    private EnvironnementTest() {
    }

    static synchronized DataInitializer preparer() {
        if (jeu != null) {
            return jeu;
        }
        Persistance.fabrique();
        try {
            ParametresJdbc.nouveauMigrateur().appliquerUneSeuleFois(VERSION_SCHEMA, SCRIPT_MIGRATION);
        } catch (SQLException e) {
            throw new IllegalStateException("Migration impossible avant les tests", e);
        }

        EntityManager em = Persistance.fabrique().createEntityManager();
        try {
            DataInitializer initialiseur = new DataInitializer(em);
            initialiseur.initData();
            jeu = initialiseur;
        } finally {
            em.close();
        }
        return jeu;
    }

    static Salle nouvelleSalle(String prefixe) {
        String nom = prefixe + " " + COMPTEUR.incrementAndGet();
        return Persistance.dansUneTransaction(em -> {
            Salle salle = new Salle(nom, 24);
            em.persist(salle);
            return salle;
        });
    }

    static Utilisateur nouvelUtilisateur(String prefixe) {
        int numero = COMPTEUR.incrementAndGet();
        String email = prefixe.toLowerCase().replace(' ', '.') + "." + numero + "@test.campus.ma";
        return Persistance.dansUneTransaction(em -> {
            Utilisateur utilisateur = new Utilisateur(prefixe + " " + numero, email);
            em.persist(utilisateur);
            return utilisateur;
        });
    }
}
