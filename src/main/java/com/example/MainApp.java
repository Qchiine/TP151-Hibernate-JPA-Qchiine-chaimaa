package com.example;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.RollbackException;

import org.hibernate.SessionFactory;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.presentation.Tableau;
import com.example.service.CompteurSql;
import com.example.service.ConflitReservationException;
import com.example.service.ReservationService;
import com.example.util.DataInitializer;
import com.example.util.MigrationExecutor;
import com.example.util.ParametresJdbc;
import com.example.util.PerformanceReport;
import com.example.util.Persistance;

/**
 * Point d'entree de l'application de gestion des reservations de salles.
 *
 * <p>Le deroulement suit le cahier des charges du projet : migration du schema,
 * alimentation du jeu de donnees, mesure du cache de second niveau, controle
 * d'equivalence JPQL / Criteria, verification du verrouillage optimiste, puis
 * rapport de performance.</p>
 */
public final class MainApp {

    private static final Logger LOG = Logger.getLogger(MainApp.class.getName());

    private static final String SCRIPT_MIGRATION = "db/migration/v2_0_add_description.sql";
    private static final String VERSION_SCHEMA = "2.0";

    static {
        configurerLesLogs();
    }

    private MainApp() {
    }

    /** Charge logging.properties du classpath : java.util.logging, sans dependance externe. */
    private static void configurerLesLogs() {
        try (InputStream flux = MainApp.class.getClassLoader().getResourceAsStream("logging.properties")) {
            if (flux != null) {
                LogManager.getLogManager().readConfiguration(flux);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Configuration des logs non chargee, valeurs par defaut conservees", e);
        }
    }

    public static void main(String[] args) throws Exception {
        bandeau();
        EntityManagerFactory fabrique = Persistance.fabrique();

        MigrationExecutor migrateur = ParametresJdbc.nouveauMigrateur();

        etape1MigrerLeSchema(migrateur);
        DataInitializer donnees = etape2AlimenterLaBase();

        ReservationService service = new ReservationService();
        Reservation temoin = donnees.getReservations().get(0);

        etape3CacheDeuxiemeNiveau(temoin.getId());
        etape4JpqlContreCriteria(service, donnees.getSalles().get(0));
        etape5VerrouillageOptimiste(temoin.getId());
        etape6CreneauxLibres(service, donnees.getSalles().get(4));

        EntityManager em = fabrique.createEntityManager();
        try {
            PerformanceReport rapport = new PerformanceReport(em);
            etape7RapportDePerformance(rapport, service, temoin.getId());
        } finally {
            em.close();
        }

        Persistance.fermer();
        System.out.println();
        System.out.println("Application terminee : base en memoire liberee, contexte de persistance ferme.");
        LOG.info("Arret propre de l'application");
    }

    // ------------------------------------------------------------------ etapes

    private static void etape1MigrerLeSchema(MigrationExecutor migrateur) throws Exception {
        titre("1/7", "Migration du schema (version " + VERSION_SCHEMA + ")");

        MigrationExecutor.Resultat resultat = migrateur.appliquerUneSeuleFois(VERSION_SCHEMA, SCRIPT_MIGRATION);
        System.out.println("  " + resultat);
        System.out.println("  versions enregistrees : " + migrateur.versionsAppliquees());

        Tableau controle = new Tableau("CONTROLE", "CIBLE", "PRESENT");
        controle.ligne("colonne", "salles.description", oui(migrateur.colonneExiste("salles", "description")));
        controle.ligne("index", "idx_utilisateur_email", oui(migrateur.indexExiste("idx_utilisateur_email")));
        controle.afficher();

        boolean deja = migrateur.appliquerUneSeuleFois(VERSION_SCHEMA, SCRIPT_MIGRATION).isDejaAppliquee();
        System.out.println("  rejeu du meme script : " + (deja ? "ignore (idempotent)" : "REJOUE, anomalie"));
        LOG.info("Migration " + VERSION_SCHEMA + " verifiee");
    }

    private static DataInitializer etape2AlimenterLaBase() {
        titre("2/7", "Jeu de donnees de demonstration");

        EntityManager em = Persistance.fabrique().createEntityManager();
        DataInitializer donnees = new DataInitializer(em);
        try {
            donnees.initData();
        } finally {
            em.close();
        }

        Tableau inventaire = new Tableau("ENTITE", "LIGNES");
        inventaire.ligne("Utilisateur", donnees.getUtilisateurs().size());
        inventaire.ligne("Salle", donnees.getSalles().size());
        inventaire.ligne("Reservation", donnees.getReservations().size());
        inventaire.afficher();
        System.out.println("  " + CompteurSql.total() + " instruction(s) SQL pour l'insertion, "
                + donnees.getSalles().size() + " salles disponibles");
        return donnees;
    }

    private static void etape3CacheDeuxiemeNiveau(Long idReservation) {
        titre("3/7", "Cache de second niveau sur Reservation");

        SessionFactory fabrique = Persistance.fabrique().unwrap(SessionFactory.class);
        fabrique.getCache().evictEntityData(Reservation.class, idReservation);

        long avant = CompteurSql.total();
        Reservation premiere = Persistance.enLecture(em -> em.find(Reservation.class, idReservation));
        long apresPremiere = CompteurSql.total();

        Reservation seconde = Persistance.enLecture(em -> em.find(Reservation.class, idReservation));
        long apresSeconde = CompteurSql.total();

        System.out.println("  lecture 1 : " + premiere + " -> " + (apresPremiere - avant) + " requete(s) SQL");
        System.out.println("  lecture 2 : version " + seconde.getVersion() + " -> "
                + (apresSeconde - apresPremiere) + " requete(s) SQL");
        System.out.println("  >> " + (apresSeconde - apresPremiere == 0
                ? "cache de second niveau operationnel : aucune requete rejouee"
                : "cache inactif, verifier hibernate.cache.use_second_level_cache"));
        LOG.fine("Lecture cache de la reservation " + idReservation);
    }

    private static void etape4JpqlContreCriteria(ReservationService service, Salle salle) {
        titre("4/7", "Equivalence JPQL / Criteria API");

        LocalDateTime seuil = LocalDate.now().atStartOfDay();
        CompteurSql.reinitialiser();

        List<Reservation> parJpql = service.listerParSalleEtDate(salle.getNom(), seuil);
        long sqlJpql = CompteurSql.total();
        List<Reservation> parCriteria = service.listerParSalleEtDateViaCriteria(salle.getNom(), seuil);
        long sqlCriteria = CompteurSql.total() - sqlJpql;
        List<Reservation> rejoue = service.listerParSalleEtDate(salle.getNom(), seuil);
        long sqlRejoue = CompteurSql.total() - sqlCriteria - sqlJpql;

        System.out.println("  salle " + salle.getNom() + " apres le " + seuil.toLocalDate());
        System.out.println("  JPQL     : " + parJpql.size() + " reservation(s) en " + sqlJpql + " requete(s)");
        System.out.println("  Criteria : " + parCriteria.size() + " reservation(s) en " + sqlCriteria
                + " requete(s)");
        System.out.println("  JPQL rejouee (cache de requete) : " + rejoue.size() + " reservation(s) en "
                + sqlRejoue + " requete(s)");
        System.out.println("  >> " + (parJpql.size() == parCriteria.size()
                ? "les deux ecritures produisent le meme resultat"
                : "DIVERGENCE entre JPQL et Criteria"));

        Tableau detail = new Tableau("ID", "DEBUT", "FIN");
        parJpql.stream().limit(5).forEach(r -> detail.ligne(r.getId(), r.getDateDebut(), r.getDateFin()));
        detail.afficher();
    }

    private static void etape5VerrouillageOptimiste(Long idReservation) {
        titre("5/7", "Verrouillage optimiste sur deux sessions concurrentes");

        EntityManager premiere = Persistance.fabrique().createEntityManager();
        EntityManager seconde = Persistance.fabrique().createEntityManager();
        try {
            LocalDateTime reference = LocalDate.now().plusDays(40).atTime(10, 0);

            premiere.getTransaction().begin();
            seconde.getTransaction().begin();

            Reservation vueA = premiere.find(Reservation.class, idReservation);
            Reservation vueB = seconde.find(Reservation.class, idReservation);
            System.out.println("  version lue par les deux sessions : " + vueA.getVersion());

            vueA.deplacerAu(reference.plusDays(40), reference.plusDays(40).plusHours(1));
            premiere.getTransaction().commit();
            System.out.println("  session A validee, nouvelle version : " + vueA.getVersion());

            vueB.deplacerAu(reference.plusDays(41), reference.plusDays(41).plusHours(1));
            try {
                seconde.getTransaction().commit();
                System.out.println("  /!\\ session B acceptee : le controle de version n'a pas joue");
            } catch (RollbackException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                System.out.println("  session B refusee : " + cause.getClass().getSimpleName());
                System.out.println("  reponse applicative : Conflit detecte, veuillez reessayer.");
                LOG.log(Level.WARNING, "Ecriture concurrente rejetee par le verrouillage optimiste");
            }
        } catch (ConflitReservationException e) {
            System.out.println("  creneau indisponible : " + e.getMessage());
        } finally {
            if (premiere.getTransaction().isActive()) {
                premiere.getTransaction().rollback();
            }
            if (seconde.getTransaction().isActive()) {
                seconde.getTransaction().rollback();
            }
            premiere.close();
            seconde.close();
        }
    }

    private static void etape6CreneauxLibres(ReservationService service, Salle salle) {
        titre("6/7", "Proposition de creneaux libres (apport personnel)");

        LocalDate jour = LocalDate.now().plusDays(3);
        List<ReservationService.Creneau> libres = service.proposerCreneauxLibres(salle.getNom(), jour, 60);

        System.out.println("  salle " + salle.getNom() + " le " + jour + " (ouverte 08:00 - 20:00)");
        if (libres.isEmpty()) {
            System.out.println("  aucune plage d'une heure disponible");
        } else {
            Tableau planning = new Tableau("PLAGE LIBRE", "DEBUT", "FIN");
            libres.stream().limit(8).forEach(c -> planning.ligne(
                    c.getDebut().toLocalTime() + " - " + c.getFin().toLocalTime(),
                    c.getDebut().toLocalTime(), c.getFin().toLocalTime()));
            planning.afficher();
            System.out.println("  " + libres.size() + " plage(s) d'une heure a proposer");
        }
        System.out.println("  reservations confirmees en base : " + service.compterReservationsConfirmees());
    }

    private static void etape7RapportDePerformance(PerformanceReport rapport, ReservationService service,
            Long idTemoin) {
        titre("7/7", "Rapport de performance global");
        PerformanceReport.Instantane instantane = rapport.printReport();

        Reservation derniere = service.trouver(idTemoin);
        System.out.println("  controle de lecture : " + (derniere == null ? "aucune reservation" : derniere));
        System.out.println("  transactions reussies : " + instantane.transactionsReussies()
                + " / " + instantane.transactions());
    }

    // ---------------------------------------------------------------- affichage

    private static void bandeau() {
        System.out.println();
        System.out.println("+======================================================================+");
        System.out.println("|  GESTION DES RESERVATIONS DE SALLES - PROJET DE SYNTHESE HIBERNATE   |");
        System.out.println("|  JPA / Hibernate 5.4 - cache JCache + EhCache 3 - H2 en memoire      |");
        System.out.println("|  realise par Qchiine Chaimaa                                         |");
        System.out.println("+======================================================================+");
        System.out.println();
    }

    private static void titre(String numero, String libelle) {
        System.out.println();
        System.out.println("[Etape " + numero + "] " + libelle);
        System.out.println("---------------------------------------------------------------");
    }

    private static String oui(boolean valeur) {
        return valeur ? "oui" : "NON";
    }
}
