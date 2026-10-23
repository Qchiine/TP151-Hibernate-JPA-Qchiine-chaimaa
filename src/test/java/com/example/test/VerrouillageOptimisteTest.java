package com.example.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;

import javax.persistence.EntityManager;
import javax.persistence.OptimisticLockException;
import javax.persistence.RollbackException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.model.Utilisateur;
import com.example.service.ReservationService;
import com.example.util.Persistance;

@DisplayName("Verrouillage optimiste sur ecritures concurrentes")
class VerrouillageOptimisteTest {

    private static ReservationService service;

    @BeforeAll
    static void avantTout() {
        EnvironnementTest.preparer();
        service = new ReservationService();
    }

    @Test
    @DisplayName("la seconde ecriture concurrente est rejetee par le controle de version")
    void laSecondeEcritureConcurrenteEstRejetee() {
        Salle salle = EnvironnementTest.nouvelleSalle("Salle Verrou");
        Utilisateur demandeur = EnvironnementTest.nouvelUtilisateur("Demandeur Verrou");
        LocalDateTime debut = LocalDate.now().plusDays(30).atTime(9, 0);
        Reservation initiale = service.reserver(demandeur.getEmail(), salle.getNom(), debut, debut.plusHours(1));
        Long identifiant = initiale.getId();

        EntityManager sessionA = Persistance.fabrique().createEntityManager();
        EntityManager sessionB = Persistance.fabrique().createEntityManager();
        try {
            sessionA.getTransaction().begin();
            sessionB.getTransaction().begin();

            Reservation vueA = sessionA.find(Reservation.class, identifiant);
            Reservation vueB = sessionB.find(Reservation.class, identifiant);
            assertEquals(vueA.getVersion(), vueB.getVersion());

            vueA.deplacerAu(debut.plusHours(6), debut.plusHours(7));
            sessionA.getTransaction().commit();

            vueB.deplacerAu(debut.plusHours(8), debut.plusHours(9));
            RuntimeException refus = assertThrows(RuntimeException.class,
                    () -> sessionB.getTransaction().commit());

            Throwable cause = refus instanceof RollbackException && refus.getCause() != null
                    ? refus.getCause()
                    : refus;
            assertTrue(cause instanceof OptimisticLockException,
                    "attendu OptimisticLockException, obtenu " + cause.getClass().getName());
        } finally {
            if (sessionA.getTransaction().isActive()) {
                sessionA.getTransaction().rollback();
            }
            if (sessionB.getTransaction().isActive()) {
                sessionB.getTransaction().rollback();
            }
            sessionA.close();
            sessionB.close();
        }

        assertEquals(debut.plusHours(6), service.trouver(identifiant).getDateDebut(),
                "seule l'ecriture gagnante est conservee");
    }

    @Test
    @DisplayName("chaque modification incremente la version de l'entite")
    void chaqueModificationIncrementeLaVersion() {
        Salle salle = EnvironnementTest.nouvelleSalle("Salle Version");
        Utilisateur demandeur = EnvironnementTest.nouvelUtilisateur("Demandeur Version");
        LocalDateTime debut = LocalDate.now().plusDays(31).atTime(11, 0);
        Reservation reservation = service.reserver(demandeur.getEmail(), salle.getNom(), debut, debut.plusHours(1));
        int versionInitiale = reservation.getVersion();

        service.deplacer(reservation.getId(), debut.plusHours(2), debut.plusHours(3));

        assertEquals(versionInitiale + 1, service.trouver(reservation.getId()).getVersion());
    }
}
