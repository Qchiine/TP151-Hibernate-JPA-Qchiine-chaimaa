package com.example.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.persistence.EntityManager;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.util.PerformanceReport;
import com.example.util.Persistance;

@DisplayName("Rapport de performance Hibernate")
class PerformanceReportTest {

    @BeforeAll
    static void avantTout() {
        EnvironnementTest.preparer();
    }

    @Test
    @DisplayName("le rapport expose requetes, cache, transactions et duree")
    void leRapportExposeLesCompteursAttendus() {
        Persistance.dansUneTransaction(em -> {
            Salle salle = new Salle("Salle Rapport", 10);
            em.persist(salle);
            return salle.getId();
        });
        Persistance.enLecture(em -> em
                .createQuery("select r from Reservation r order by r.id", Reservation.class)
                .setMaxResults(4)
                .getResultList());

        EntityManager em = Persistance.fabrique().createEntityManager();
        try {
            PerformanceReport rapport = new PerformanceReport(em);
            PerformanceReport.Instantane instantane = rapport.printReport();
            String texte = instantane.rendu();

            assertTrue(instantane.requetes() > 0, "les requetes HQL doivent etre comptees");
            assertTrue(instantane.instructionsJdbc() > 0);
            assertTrue(instantane.transactionsReussies() > 0);
            assertTrue(instantane.dureeRequetesMs() >= 0);
            assertTrue(texte.contains("RAPPORT DE PERFORMANCE"), texte);
            assertTrue(texte.contains("Cache 2eme niveau"), texte);
            assertTrue(texte.contains("Transactions"), texte);
            assertTrue(rapport.dernierInstantane() == instantane, "l'instantane doit rester consultable");
        } finally {
            em.close();
        }
    }

    @Test
    @DisplayName("le taux de reussite du cache reste dans les bornes 0 - 100")
    void leTauxDeReussiteResteDansLesBornes() {
        EntityManager em = Persistance.fabrique().createEntityManager();
        try {
            PerformanceReport.Instantane instantane = new PerformanceReport(em).capturer();
            assertTrue(instantane.tauxDeReussite() >= 0.0);
            assertTrue(instantane.tauxDeReussite() <= 100.0);
        } finally {
            em.close();
        }
    }

    @Test
    @DisplayName("la remise a zero vide tous les compteurs")
    void laRemiseAZeroVideLesCompteurs() {
        EntityManager em = Persistance.fabrique().createEntityManager();
        try {
            PerformanceReport rapport = new PerformanceReport(em);
            rapport.reinitialiser();
            PerformanceReport.Instantane instantane = rapport.capturer();

            assertEquals(0, instantane.requetes());
            assertEquals(0, instantane.hits());
            assertEquals(0, instantane.transactions());
            assertEquals(0, instantane.echecsOptimistes());
        } finally {
            em.close();
        }
    }
}
