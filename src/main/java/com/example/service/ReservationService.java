package com.example.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.OptimisticLockException;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Root;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.model.StatutReservation;
import com.example.model.Utilisateur;
import com.example.util.Persistance;

/**
 * Coeur metier du domaine : pose, deplacement et annulation de reservations.
 *
 * <p>Toutes les ecritures passent par {@link Persistance#dansUneTransaction} :
 * une exception metier provoque un rollback complet, aucune reservation
 * partielle ne peut donc subsister.</p>
 */
public class ReservationService {

    private static final Logger LOG = Logger.getLogger(ReservationService.class.getName());

    private static final LocalTime OUVERTURE = LocalTime.of(8, 0);
    private static final LocalTime FERMETURE = LocalTime.of(20, 0);

    /** Cree une reservation apres avoir verifie que le creneau est libre. */
    public Reservation reserver(String email, String nomSalle, LocalDateTime debut, LocalDateTime fin) {
        LOG.info(() -> "Demande de reservation : " + nomSalle + " du " + debut + " au " + fin + " par " + email);
        try {
            return Persistance.dansUneTransaction(em -> {
                Utilisateur utilisateur = utilisateurParEmail(em, email);
                Salle salle = salleParNom(em, nomSalle);
                verifierDisponibilite(em, salle, debut, fin, null);

                Reservation reservation = new Reservation(salle, utilisateur, debut, fin);
                salle.accueillir(reservation);
                utilisateur.reserver(reservation);
                em.persist(reservation);
                return reservation;
            });
        } catch (OptimisticLockException | javax.persistence.RollbackException e) {
            LOG.log(Level.SEVERE, "Conflit de verrouillage detecte, veuillez reessayer", e);
            throw new ConflitReservationException(null, "Conflit detecte, veuillez reessayer.");
        }
    }

    /** Annule une reservation sans la supprimer : l'historique reste consultable. */
    public Reservation annuler(Long reservationId) {
        return Persistance.dansUneTransaction(em -> {
            Reservation reservation = em.find(Reservation.class, reservationId);
            if (reservation == null) {
                throw new IllegalArgumentException("Reservation inconnue : " + reservationId);
            }
            reservation.annuler();
            LOG.info(() -> "Reservation " + reservationId + " annulee");
            return reservation;
        });
    }

    /** Deplace une reservation existante, en revalidant le creneau cible. */
    public Reservation deplacer(Long reservationId, LocalDateTime nouveauDebut, LocalDateTime nouveauFin) {
        return Persistance.dansUneTransaction(em -> {
            Reservation reservation = em.find(Reservation.class, reservationId);
            if (reservation == null) {
                throw new IllegalArgumentException("Reservation inconnue : " + reservationId);
            }
            verifierDisponibilite(em, reservation.getSalle(), nouveauDebut, nouveauFin, reservationId);
            reservation.deplacerAu(nouveauDebut, nouveauFin);
            return reservation;
        });
    }

    public Reservation trouver(Long reservationId) {
        return Persistance.enLecture(em -> em.find(Reservation.class, reservationId));
    }

    public Reservation trouverAvecVerrouPessimiste(Long reservationId) {
        return Persistance.dansUneTransaction(em -> {
            Reservation reservation = em.find(Reservation.class, reservationId, LockModeType.PESSIMISTIC_WRITE);
            if (reservation == null) {
                throw new IllegalArgumentException("Reservation inconnue : " + reservationId);
            }
            return reservation;
        });
    }

    /** Toutes les reservations d'une salle posterieures a une date, en JPQL. */
    public List<Reservation> listerParSalleEtDate(String nomSalle, LocalDateTime apres) {
        return Persistance.enLecture(em -> em
                .createQuery("select r from Reservation r where r.salle.nom = :nomSalle and r.dateDebut > :date"
                        + " order by r.dateDebut", Reservation.class)
                .setParameter("nomSalle", nomSalle)
                .setParameter("date", apres)
                .setHint("org.hibernate.cacheable", Boolean.TRUE)
                .getResultList());
    }

    /** Meme requete, construite avec l'API Criteria : sert de controle d'equivalence. */
    public List<Reservation> listerParSalleEtDateViaCriteria(String nomSalle, LocalDateTime apres) {
        return Persistance.enLecture(em -> {
            CriteriaBuilder constructeur = em.getCriteriaBuilder();
            CriteriaQuery<Reservation> requete = constructeur.createQuery(Reservation.class);
            Root<Reservation> racine = requete.from(Reservation.class);
            Join<Reservation, Salle> jointure = racine.join("salle");
            requete.select(racine)
                    .where(constructeur.equal(jointure.get("nom"), nomSalle),
                            constructeur.greaterThan(racine.get("dateDebut"), apres))
                    .orderBy(constructeur.asc(racine.get("dateDebut")));
            return em.createQuery(requete).getResultList();
        });
    }

    /** Toutes les reservations confirmees d'un utilisateur, les plus recentes d'abord. */
    public List<Reservation> reservationsDe(String email) {
        return Persistance.enLecture(em -> em
                .createQuery("select r from Reservation r where r.utilisateur.email = :email"
                        + " order by r.dateDebut desc", Reservation.class)
                .setParameter("email", email)
                .getResultList());
    }

    /**
     * Touche personnelle : au lieu de refuser sechement un creneau pris, on
     * propose les plages encore libres de la journee demandee.
     *
     * @return les creneaux libres d'une duree donnee, dans l'ordre chronologique
     */
    public List<Creneau> proposerCreneauxLibres(String nomSalle, LocalDate jour, int dureeMinutes) {
        if (dureeMinutes <= 0) {
            throw new IllegalArgumentException("La duree doit etre strictement positive");
        }
        List<Reservation> occupants = Persistance.enLecture(em -> em
                .createQuery("select r from Reservation r where r.salle.nom = :nomSalle"
                        + " and r.dateDebut < :finJour and r.dateFin > :debutJour"
                        + " and r.statut = :statut order by r.dateDebut", Reservation.class)
                .setParameter("nomSalle", nomSalle)
                .setParameter("debutJour", jour.atStartOfDay())
                .setParameter("finJour", jour.plusDays(1).atStartOfDay())
                .setParameter("statut", StatutReservation.CONFIRMEE)
                .getResultList());

        List<Creneau> libres = new ArrayList<>();
        LocalDateTime curseur = jour.atTime(OUVERTURE);
        LocalDateTime limite = jour.atTime(FERMETURE);
        for (Reservation occupant : occupants) {
            LocalDateTime debutOccupe = occupant.getDateDebut().isBefore(curseur) ? curseur : occupant.getDateDebut();
            if (debutOccupe.isAfter(curseur)) {
                ajouterSiAssezLong(libres, curseur, debutOccupe, dureeMinutes);
            }
            if (occupant.getDateFin().isAfter(curseur)) {
                curseur = occupant.getDateFin();
            }
        }
        if (curseur.isBefore(limite)) {
            ajouterSiAssezLong(libres, curseur, limite, dureeMinutes);
        }
        LOG.fine(() -> libres.size() + " creneau(x) libre(s) le " + jour + " pour " + nomSalle);
        return libres;
    }

    public long compterReservationsConfirmees() {
        return Persistance.enLecture(em -> em
                .createQuery("select count(r) from Reservation r where r.statut = :statut", Long.class)
                .setParameter("statut", StatutReservation.CONFIRMEE)
                .getSingleResult());
    }

    private void ajouterSiAssezLong(List<Creneau> libres, LocalDateTime debut, LocalDateTime fin, int dureeMinutes) {
        LocalDateTime debutCreneau = debut;
        while (!debutCreneau.plusMinutes(dureeMinutes).isAfter(fin)) {
            libres.add(new Creneau(debutCreneau, debutCreneau.plusMinutes(dureeMinutes)));
            debutCreneau = debutCreneau.plusMinutes(dureeMinutes);
        }
    }

    /**
     * Verifie qu'aucune reservation confirmee ne chevauche le creneau demande.
     *
     * @param ignorer identifiant a exclure du controle (deplacement d'une reservation)
     */
    private void verifierDisponibilite(EntityManager em, Salle salle, LocalDateTime debut, LocalDateTime fin,
            Long ignorer) {
        String hql = "select count(r) from Reservation r where r.salle = :salle"
                + " and r.statut = :statut and r.dateDebut < :fin and r.dateFin > :debut"
                + (ignorer == null ? "" : " and r.id <> :ignorer");
        javax.persistence.TypedQuery<Long> requete = em.createQuery(hql, Long.class)
                .setParameter("salle", salle)
                .setParameter("statut", StatutReservation.CONFIRMEE)
                .setParameter("debut", debut)
                .setParameter("fin", fin);
        if (ignorer != null) {
            requete.setParameter("ignorer", ignorer);
        }
        Long conflits = requete.getSingleResult();
        if (conflits != null && conflits > 0) {
            LOG.warning(() -> "Creneau refuse sur " + salle.getNom() + " : " + conflits + " conflit(s)");
            throw new ConflitReservationException(salle.getId(),
                    "La salle " + salle.getNom() + " est deja occupee sur ce creneau");
        }
    }

    private Utilisateur utilisateurParEmail(EntityManager em, String email) {
        return em.createQuery("select u from Utilisateur u where u.email = :email", Utilisateur.class)
                .setParameter("email", email)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur inconnu : " + email));
    }

    private Salle salleParNom(EntityManager em, String nom) {
        return em.createQuery("select s from Salle s where s.nom = :nom", Salle.class)
                .setParameter("nom", nom)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Salle inconnue : " + nom));
    }

    /** Intervalle horaire propose a l'utilisateur. */
    public static final class Creneau {

        private final LocalDateTime debut;
        private final LocalDateTime fin;

        Creneau(LocalDateTime debut, LocalDateTime fin) {
            this.debut = debut;
            this.fin = fin;
        }

        public LocalDateTime getDebut() {
            return debut;
        }

        public LocalDateTime getFin() {
            return fin;
        }

        @Override
        public String toString() {
            return debut.toLocalTime() + " - " + fin.toLocalTime();
        }
    }
}
