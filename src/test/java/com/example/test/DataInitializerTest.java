package com.example.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.model.StatutReservation;
import com.example.model.Utilisateur;
import com.example.util.DataInitializer;

@DisplayName("Jeu de donnees de demonstration")
class DataInitializerTest {

    private static DataInitializer donnees;

    @BeforeAll
    static void avantTout() {
        donnees = EnvironnementTest.preparer();
    }

    @Test
    @DisplayName("le volume attendu d'utilisateurs, de salles et de reservations est present")
    void leVolumeAttenduEstPresent() {
        assertEquals(12, donnees.getUtilisateurs().size());
        assertEquals(6, donnees.getSalles().size());
        assertEquals(42, donnees.getReservations().size());
    }

    @Test
    @DisplayName("toutes les reservations initiales sont confirmees et durent deux heures")
    void toutesLesReservationsInitialesSontCoherentes() {
        for (Reservation reservation : donnees.getReservations()) {
            assertEquals(StatutReservation.CONFIRMEE, reservation.getStatut());
            assertEquals(120, reservation.dureeEnMinutes());
            assertTrue(reservation.getDateFin().isAfter(reservation.getDateDebut()));
        }
    }

    @Test
    @DisplayName("deux reservations d'une meme salle ne se chevauchent jamais")
    void aucunChevauchementDansLeJeuInitial() {
        Map<Long, List<Reservation>> parSalle = new HashMap<>();
        for (Reservation reservation : donnees.getReservations()) {
            parSalle.computeIfAbsent(reservation.getSalle().getId(), cle -> new java.util.ArrayList<>())
                    .add(reservation);
        }

        for (List<Reservation> reservations : parSalle.values()) {
            for (int i = 0; i < reservations.size(); i++) {
                for (int j = i + 1; j < reservations.size(); j++) {
                    Reservation a = reservations.get(i);
                    Reservation b = reservations.get(j);
                    assertFalse(a.chevauche(b.getDateDebut(), b.getDateFin()),
                            "chevauchement detecte : " + a + " / " + b);
                }
            }
        }
    }

    @Test
    @DisplayName("les adresses email des utilisateurs sont uniques")
    void lesEmailsSontUniques() {
        Set<String> emails = new HashSet<>();
        for (Utilisateur utilisateur : donnees.getUtilisateurs()) {
            assertTrue(emails.add(utilisateur.getEmail()), "email en double : " + utilisateur.getEmail());
        }
        assertEquals(donnees.getUtilisateurs().size(), emails.size());
    }

    @Test
    @DisplayName("chaque salle du jeu de donnees possede au moins une reservation")
    void chaqueSalleEstUtilisee() {
        for (Salle salle : donnees.getSalles()) {
            assertTrue(salle.nombreDeReservations() > 0, "salle sans reservation : " + salle.getNom());
            assertTrue(salle.getCapacite() > 0);
        }
    }
}
