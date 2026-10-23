package com.example.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.model.StatutReservation;
import com.example.model.Utilisateur;
import com.example.service.ConflitReservationException;
import com.example.service.ReservationService;

@DisplayName("Regles metier de la reservation de salles")
class ReservationServiceTest {

    private static ReservationService service;

    @BeforeAll
    static void avantTout() {
        EnvironnementTest.preparer();
        service = new ReservationService();
    }

    @Test
    @DisplayName("une demande valide cree une reservation confirmee")
    void uneDemandeValideCreeUneReservationConfirmee() {
        Salle salle = EnvironnementTest.nouvelleSalle("Salle Valide");
        Utilisateur demandeur = EnvironnementTest.nouvelUtilisateur("Demandeur Valide");
        LocalDateTime debut = LocalDate.now().plusDays(20).atTime(9, 0);

        Reservation reservation = service.reserver(demandeur.getEmail(), salle.getNom(), debut, debut.plusHours(2));

        assertNotNull(reservation.getId(), "l'identifiant est genere par la base");
        assertEquals(StatutReservation.CONFIRMEE, reservation.getStatut());
        assertEquals(120, reservation.dureeEnMinutes());
        assertEquals(reservation.getId(), service.trouver(reservation.getId()).getId());
    }

    @Test
    @DisplayName("un creneau deja occupe est refuse et laisse la base intacte")
    void unCreneauDejaOccupeEstRefuse() {
        Salle salle = EnvironnementTest.nouvelleSalle("Salle Conflit");
        Utilisateur premier = EnvironnementTest.nouvelUtilisateur("Premier Arrivant");
        Utilisateur second = EnvironnementTest.nouvelUtilisateur("Second Arrivant");
        LocalDateTime debut = LocalDate.now().plusDays(21).atTime(10, 0);
        service.reserver(premier.getEmail(), salle.getNom(), debut, debut.plusHours(2));

        ConflitReservationException refus = assertThrows(ConflitReservationException.class,
                () -> service.reserver(second.getEmail(), salle.getNom(), debut.plusHours(1), debut.plusHours(3)),
                "Chevauchement partiel : la seconde demande doit etre rejetee");

        assertEquals(salle.getId(), refus.getSalleId());
        assertEquals(1, service.listerParSalleEtDate(salle.getNom(), debut.minusDays(1)).size(),
                "le rollback ne doit laisser aucune trace");
    }

    @Test
    @DisplayName("deux creneaux qui se touchent sont acceptes")
    void deuxCreneauxContigusSontAcceptes() {
        Salle salle = EnvironnementTest.nouvelleSalle("Salle Contigue");
        Utilisateur demandeur = EnvironnementTest.nouvelUtilisateur("Demandeur Contigu");
        LocalDateTime debut = LocalDate.now().plusDays(22).atTime(8, 0);

        service.reserver(demandeur.getEmail(), salle.getNom(), debut, debut.plusHours(2));
        Reservation suivante = service.reserver(demandeur.getEmail(), salle.getNom(),
                debut.plusHours(2), debut.plusHours(4));

        assertNotNull(suivante.getId());
        assertEquals(2, service.listerParSalleEtDate(salle.getNom(), debut.minusDays(1)).size());
    }

    @Test
    @DisplayName("une annulation libere immediatement le creneau")
    void uneAnnulationLibereLeCreneau() {
        Salle salle = EnvironnementTest.nouvelleSalle("Salle Annulation");
        Utilisateur demandeur = EnvironnementTest.nouvelUtilisateur("Demandeur Annulation");
        LocalDateTime debut = LocalDate.now().plusDays(23).atTime(14, 0);

        Reservation premiere = service.reserver(demandeur.getEmail(), salle.getNom(), debut, debut.plusHours(1));
        Reservation annulee = service.annuler(premiere.getId());
        assertEquals(StatutReservation.ANNULEE, annulee.getStatut());

        Reservation seconde = service.reserver(demandeur.getEmail(), salle.getNom(), debut, debut.plusHours(1));
        assertNotNull(seconde.getId());
        assertNotEquals(premiere.getId(), seconde.getId(), "une nouvelle reservation occupe le creneau libere");
    }

    @Test
    @DisplayName("un deplacement vers un creneau occupe est refuse")
    void unDeplacementVersUnCreneauOccupeEstRefuse() {
        Salle salle = EnvironnementTest.nouvelleSalle("Salle Deplacement");
        Utilisateur demandeur = EnvironnementTest.nouvelUtilisateur("Demandeur Deplacement");
        LocalDateTime debut = LocalDate.now().plusDays(24).atTime(9, 0);

        Reservation occupee = service.reserver(demandeur.getEmail(), salle.getNom(), debut, debut.plusHours(1));
        LocalDateTime autre = debut.plusHours(3);
        Reservation mobile = service.reserver(demandeur.getEmail(), salle.getNom(), autre, autre.plusHours(1));

        assertThrows(ConflitReservationException.class,
                () -> service.deplacer(mobile.getId(), debut, debut.plusHours(1)));

        Reservation deplacee = service.deplacer(mobile.getId(), debut.plusHours(5), debut.plusHours(6));
        assertEquals(debut.plusHours(5), deplacee.getDateDebut());
        assertEquals(occupee.getDateDebut(), debut);
    }

    @Test
    @DisplayName("JPQL et Criteria API renvoient exactement les memes reservations")
    void jpqlEtCriteriaDonnentLeMemeResultat() {
        Salle salle = EnvironnementTest.nouvelleSalle("Salle Equivalence");
        Utilisateur demandeur = EnvironnementTest.nouvelUtilisateur("Demandeur Equivalence");
        LocalDateTime depart = LocalDate.now().plusDays(25).atTime(8, 0);
        for (int heure = 0; heure < 4; heure++) {
            LocalDateTime debut = depart.plusHours(heure * 2L);
            service.reserver(demandeur.getEmail(), salle.getNom(), debut, debut.plusHours(2));
        }

        List<Reservation> parJpql = service.listerParSalleEtDate(salle.getNom(), depart.minusHours(1));
        List<Reservation> parCriteria = service.listerParSalleEtDateViaCriteria(salle.getNom(), depart.minusHours(1));

        assertEquals(4, parJpql.size());
        assertEquals(parJpql.size(), parCriteria.size());
        for (int i = 0; i < parJpql.size(); i++) {
            assertEquals(parJpql.get(i).getId(), parCriteria.get(i).getId());
        }
    }

    @Test
    @DisplayName("les creneaux proposes evitent les plages deja reservees")
    void lesCreneauxProposesEvitentLesPlagesOccupees() {
        Salle salle = EnvironnementTest.nouvelleSalle("Salle Planning");
        Utilisateur demandeur = EnvironnementTest.nouvelUtilisateur("Demandeur Planning");
        LocalDate jour = LocalDate.now().plusDays(26);
        LocalDateTime debut = jour.atTime(10, 0);
        service.reserver(demandeur.getEmail(), salle.getNom(), debut, debut.plusHours(2));

        List<ReservationService.Creneau> libres = service.proposerCreneauxLibres(salle.getNom(), jour, 60);

        assertEquals(10, libres.size(), "8 h - 20 h moins le creneau de deux heures occupe");
        for (ReservationService.Creneau creneau : libres) {
            assertTrue(creneau.getFin().isBefore(debut) || creneau.getFin().equals(debut)
                    || creneau.getDebut().isAfter(debut.plusHours(2))
                    || creneau.getDebut().equals(debut.plusHours(2)),
                    "aucune proposition ne doit chevaucher la reservation existante : " + creneau);
        }
    }
}
