package com.example.service;

/** Signale qu'un creneau demande est deja occupe par une autre reservation. */
public class ConflitReservationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Long salleId;

    public ConflitReservationException(Long salleId, String message) {
        super(message);
        this.salleId = salleId;
    }

    public Long getSalleId() {
        return salleId;
    }
}
