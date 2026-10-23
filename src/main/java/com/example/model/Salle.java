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
 * Salle reservable. La colonne "description" n'apparait qu'en version 2.0 du
 * schema : elle est ajoutee par le script de migration, pas par le mapping.
 */
@Entity
@Table(name = "salles")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Salle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String nom;

    @Column(nullable = false)
    private int capacite;

    @Version
    private int version;

    @OneToMany(mappedBy = "salle", cascade = CascadeType.ALL, orphanRemoval = true)
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<Reservation> reservations = new ArrayList<>();

    protected Salle() {
    }

    public Salle(String nom, int capacite) {
        this.nom = nom;
        this.capacite = capacite;
    }

    public void accueillir(Reservation reservation) {
        reservations.add(reservation);
        reservation.setSalle(this);
    }

    public int nombreDeReservations() {
        return reservations.size();
    }

    public List<Reservation> getReservations() {
        return Collections.unmodifiableList(reservations);
    }

    public Long getId() {
        return id;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public int getCapacite() {
        return capacite;
    }

    public void setCapacite(int capacite) {
        this.capacite = capacite;
    }

    public int getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return nom + " (" + capacite + " places)";
    }
}
