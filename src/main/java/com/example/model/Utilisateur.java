package com.example.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Version;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * Personne qui effectue des reservations.
 * Le couple (email) est unique : c'est la cle naturelle utilisee par les services.
 */
@Entity
@Table(name = "utilisateurs")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(nullable = false, length = 120)
    private String nom;

    @Version
    private int version;

    @OneToMany(mappedBy = "utilisateur", cascade = CascadeType.ALL, orphanRemoval = true)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<Reservation> reservations = new ArrayList<>();

    protected Utilisateur() {
    }

    public Utilisateur(String nom, String email) {
        this.nom = nom;
        this.email = email;
    }

    public void reserver(Reservation reservation) {
        reservations.add(reservation);
        reservation.setUtilisateur(this);
    }

    public void annuler(Reservation reservation) {
        reservations.remove(reservation);
        reservation.setUtilisateur(null);
    }

    public int nombreDeReservations() {
        return reservations.size();
    }

    /** Vue non modifiable : la coherence de la collection reste pilotee par l'agregat. */
    public List<Reservation> getReservations() {
        return Collections.unmodifiableList(reservations);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public int getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return nom + " <" + email + ">";
    }
}
