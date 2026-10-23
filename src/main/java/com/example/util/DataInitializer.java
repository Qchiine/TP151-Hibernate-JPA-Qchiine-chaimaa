package com.example.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.model.Utilisateur;

/**
 * Alimente la base avec un jeu de donnees coherent : 12 utilisateurs,
 * 6 salles et 42 reservations reparties sur la semaine a venir.
 * Les dates sont calculees a l'execution pour rester toujours valides.
 */
public class DataInitializer {

    private static final String[] NOMS = {
        "Alice Dupont", "Bruno Marchand", "Chaimaa Qchiine", "Driss El Amrani",
        "Emma Laurent", "Farid Benali", "Ghita Zeroual", "Hugo Petit",
        "Ines Berrada", "Julien Moreau", "Karim Toubal", "Leila Fassi"
    };

    private static final String[] DOMAINES = { "etu", "ens", "admin", "labo" };

    private static final String[][] SALLES = {
        { "Amphi A", "180" },
        { "Salle B1", "40" },
        { "Salle B2", "40" },
        { "Salle C3", "25" },
        { "Labo Informatique", "20" },
        { "Salle Reunion", "12" }
    };

    private final EntityManager em;

    private final List<Utilisateur> utilisateurs = new ArrayList<>();
    private final List<Salle> salles = new ArrayList<>();
    private final List<Reservation> reservations = new ArrayList<>();

    public DataInitializer(EntityManager em) {
        this.em = em;
    }

    public void initData() {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        try {
            construireUtilisateurs();
            construireSalles();
            construireReservations();
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        }
    }

    private void construireUtilisateurs() {
        for (int i = 0; i < NOMS.length; i++) {
            String domaine = DOMAINES[i % DOMAINES.length];
            String email = NOMS[i].toLowerCase()
                    .replace(' ', '.')
                    .replace("é", "e")
                    .replace("è", "e")
                    + "@" + domaine + ".campus.ma";
            Utilisateur utilisateur = new Utilisateur(NOMS[i], email);
            em.persist(utilisateur);
            utilisateurs.add(utilisateur);
        }
    }

    private void construireSalles() {
        for (String[] definition : SALLES) {
            Salle salle = new Salle(definition[0], Integer.parseInt(definition[1]));
            em.persist(salle);
            salles.add(salle);
        }
    }

    /**
     * Une reservation par salle et par jour, sur sept jours : les creneaux ne se
     * chevauchent jamais, ce qui rend le jeu de donnees directement exploitable.
     */
    private void construireReservations() {
        LocalDate aujourdhui = LocalDate.now();
        int compteur = 0;
        for (int jour = 1; jour <= 7; jour++) {
            for (int indiceSalle = 0; indiceSalle < salles.size(); indiceSalle++) {
                Salle salle = salles.get(indiceSalle);
                Utilisateur titulaire = utilisateurs.get(compteur % utilisateurs.size());
                LocalTime debut = LocalTime.of(8 + (jour % 3) * 2, 0);
                LocalDateTime dateDebut = aujourdhui.plusDays(jour).atTime(debut);
                LocalDateTime dateFin = dateDebut.plusHours(2);

                Reservation reservation = new Reservation(salle, titulaire, dateDebut, dateFin);
                salle.accueillir(reservation);
                titulaire.reserver(reservation);
                em.persist(reservation);
                reservations.add(reservation);
                compteur++;
            }
        }
    }

    public List<Utilisateur> getUtilisateurs() {
        return List.copyOf(utilisateurs);
    }

    public List<Salle> getSalles() {
        return List.copyOf(salles);
    }

    public List<Reservation> getReservations() {
        return List.copyOf(reservations);
    }
}
