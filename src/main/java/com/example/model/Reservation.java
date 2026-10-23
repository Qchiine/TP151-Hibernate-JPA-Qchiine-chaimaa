package com.example.model;

import java.time.Duration;
import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Version;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * Occupation d'une salle par un utilisateur sur un intervalle de temps.
 * Les deux associations sont en LAZY : une reservation seule ne doit pas
 * entrainer le chargement de la salle ni de l'utilisateur.
 */
@Entity
@Table(name = "reservations")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "salle_id", nullable = false)
    private Salle salle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(nullable = false)
    private LocalDateTime dateDebut;

    @Column(nullable = false)
    private LocalDateTime dateFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StatutReservation statut = StatutReservation.CONFIRMEE;

    @Version
    private int version;

    protected Reservation() {
    }

    public Reservation(Salle salle, Utilisateur utilisateur, LocalDateTime dateDebut, LocalDateTime dateFin) {
        if (dateDebut == null || dateFin == null) {
            throw new IllegalArgumentException("Les bornes du creneau sont obligatoires");
        }
        if (!dateFin.isAfter(dateDebut)) {
            throw new IllegalArgumentException("La fin du creneau doit suivre son debut");
        }
        this.salle = salle;
        this.utilisateur = utilisateur;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
    }

    /** Deux creneaux se chevauchent si chacun commence avant la fin de l'autre. */
    public boolean chevauche(LocalDateTime debut, LocalDateTime fin) {
        return dateDebut.isBefore(fin) && dateFin.isAfter(debut);
    }

    public long dureeEnMinutes() {
        return Duration.between(dateDebut, dateFin).toMinutes();
    }

    public void annuler() {
        this.statut = StatutReservation.ANNULEE;
    }

    public void deplacerAu(LocalDateTime nouveauDebut, LocalDateTime nouvelleFin) {
        if (!nouvelleFin.isAfter(nouveauDebut)) {
            throw new IllegalArgumentException("La fin du creneau doit suivre son debut");
        }
        this.dateDebut = nouveauDebut;
        this.dateFin = nouvelleFin;
    }

    public Long getId() {
        return id;
    }

    public Salle getSalle() {
        return salle;
    }

    void setSalle(Salle salle) {
        this.salle = salle;
    }

    public Utilisateur getUtilisateur() {
        return utilisateur;
    }

    void setUtilisateur(Utilisateur utilisateur) {
        this.utilisateur = utilisateur;
    }

    public LocalDateTime getDateDebut() {
        return dateDebut;
    }

    public LocalDateTime getDateFin() {
        return dateFin;
    }

    public StatutReservation getStatut() {
        return statut;
    }

    public int getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "Reservation#" + id + " [" + dateDebut + " -> " + dateFin + "] " + statut;
    }
}
