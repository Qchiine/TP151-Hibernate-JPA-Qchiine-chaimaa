package com.example.model;

/** Etat d'une reservation. Une reservation annulee libere son creneau. */
public enum StatutReservation {

    CONFIRMEE,
    ANNULEE;

    public boolean occupeLeCreneau() {
        return this == CONFIRMEE;
    }
}
