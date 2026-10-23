package com.example.util;

import com.example.presentation.Tableau;
import org.hibernate.SessionFactory;
import org.hibernate.stat.CacheRegionStatistics;
import org.hibernate.stat.QueryStatistics;
import org.hibernate.stat.Statistics;

import javax.persistence.EntityManager;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Locale;

/**
 * Exploite les compteurs internes d'Hibernate pour produire un bilan chiffre
 * du travail effectue : requetes, cache de second niveau, transactions.
 *
 * <p>Les statistiques doivent etre activees par
 * {@code hibernate.generate_statistics=true} ; sinon tous les compteurs
 * restent a zero.</p>
 */
public final class PerformanceReport {

    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Statistics statistiques;
    private final Deque<Instantane> historique = new ArrayDeque<>();

    public PerformanceReport(EntityManager em) {
        this.statistiques = em.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
    }

    /** Remet tous les compteurs a zero : utile entre deux scenarios mesures. */
    public void reinitialiser() {
        statistiques.clear();
        historique.clear();
    }

    public Statistics statistiques() {
        return statistiques;
    }

    /**
     * Construit le bilan courant, l'affiche et le conserve dans l'historique.
     *
     * @return l'instantane affiche
     */
    public Instantane printReport() {
        Instantane instantane = capturer();
        historique.addLast(instantane);
        System.out.println(instantane.rendu());
        return instantane;
    }

    /** Bilan de la derniere capture, ou {@code null} si aucune capture. */
    public Instantane dernierInstantane() {
        return historique.peekLast();
    }

    public Instantane capturer() {
        long dureeRequetes = 0L;
        for (String hql : statistiques.getQueries()) {
            QueryStatistics requete = statistiques.getQueryStatistics(hql);
            dureeRequetes += requete.getExecutionTotalTime();
        }

        Tableau regions = new Tableau("REGION", "HITS", "MISSES", "PUTS", "ELEMENTS");
        int regionsInactives = remplirRegions(regions);

        return new Instantane(
                LocalTime.now(),
                statistiques.getQueryExecutionCount(),
                statistiques.getPrepareStatementCount(),
                statistiques.getSecondLevelCacheHitCount(),
                statistiques.getSecondLevelCacheMissCount(),
                statistiques.getSecondLevelCachePutCount(),
                statistiques.getTransactionCount(),
                statistiques.getSuccessfulTransactionCount(),
                statistiques.getOptimisticFailureCount(),
                dureeRequetes,
                regions,
                regionsInactives);
    }

    /** Remplit le tableau des regions actives et renvoie le nombre de regions restees muettes. */
    private int remplirRegions(Tableau tableau) {
        String[] noms = statistiques.getSecondLevelCacheRegionNames();
        Arrays.sort(noms);
        int inactives = 0;
        for (String nom : noms) {
            CacheRegionStatistics region = region(nom);
            if (region == null || region.getHitCount() + region.getMissCount() + region.getPutCount() == 0) {
                inactives++;
                continue;
            }
            String elements = region.getElementCountInMemory() < 0
                    ? "n/d"
                    : String.valueOf(region.getElementCountInMemory());
            tableau.ligne(region.getRegionName(), region.getHitCount(), region.getMissCount(),
                    region.getPutCount(), elements);
        }
        return inactives;
    }

    /**
     * Les regions techniques (horodatages, resultats de requetes) ne sont pas
     * des regions de donnees : Hibernate refuse de les interroger en tant que
     * telles, on bascule alors sur l'API generique.
     */
    private CacheRegionStatistics region(String nom) {
        try {
            return statistiques.getDomainDataRegionStatistics(nom);
        } catch (IllegalArgumentException pasUneRegionDeDonnees) {
            try {
                return statistiques.getCacheRegionStatistics(nom);
            } catch (IllegalArgumentException regionInconnue) {
                return null;
            }
        }
    }

    /** Photographie immuable des compteurs a un instant donne. */
    public static final class Instantane {

        private final LocalTime heure;
        private final long requetes;
        private final long instructionsJdbc;
        private final long hits;
        private final long misses;
        private final long puts;
        private final long transactions;
        private final long transactionsReussies;
        private final long echecsOptimistes;
        private final long dureeRequetesMs;
        private final Tableau regions;
        private final int regionsInactives;

        Instantane(LocalTime heure, long requetes, long instructionsJdbc, long hits, long misses, long puts,
                long transactions, long transactionsReussies, long echecsOptimistes, long dureeRequetesMs,
                Tableau regions, int regionsInactives) {
            this.heure = heure;
            this.requetes = requetes;
            this.instructionsJdbc = instructionsJdbc;
            this.hits = hits;
            this.misses = misses;
            this.puts = puts;
            this.transactions = transactions;
            this.transactionsReussies = transactionsReussies;
            this.echecsOptimistes = echecsOptimistes;
            this.dureeRequetesMs = dureeRequetesMs;
            this.regions = regions;
            this.regionsInactives = regionsInactives;
        }

        public long requetes() {
            return requetes;
        }

        public long instructionsJdbc() {
            return instructionsJdbc;
        }

        public long hits() {
            return hits;
        }

        public long misses() {
            return misses;
        }

        public long puts() {
            return puts;
        }

        public long transactions() {
            return transactions;
        }

        public long transactionsReussies() {
            return transactionsReussies;
        }

        public long echecsOptimistes() {
            return echecsOptimistes;
        }

        public long dureeRequetesMs() {
            return dureeRequetesMs;
        }

        /** Nombre de regions configurees mais encore inutilisees. */
        public int regionsInactives() {
            return regionsInactives;
        }

        /** Taux de reussite du cache, entre 0 et 100. */
        public double tauxDeReussite() {
            long total = hits + misses;
            return total == 0 ? 0.0 : Math.round(1000.0 * hits / total) / 10.0;
        }

        public String rendu() {
            StringBuilder texte = new StringBuilder();
            texte.append("+=== RAPPORT DE PERFORMANCE ").append(HEURE.format(heure)).append(" ===+\n");
            texte.append("Requetes HQL executees ....... : ").append(requetes).append('\n');
            texte.append("Instructions JDBC preparees .. : ").append(instructionsJdbc).append('\n');
            texte.append("Transactions ouvertes ........ : ").append(transactions)
                    .append(" (reussies ").append(transactionsReussies).append(")\n");
            texte.append("Cache 2eme niveau ............ : hits ").append(hits)
                    .append(" / misses ").append(misses)
                    .append(" / puts ").append(puts)
                    .append("  -> ").append(String.format(Locale.ROOT, "%.1f %%", tauxDeReussite()))
                    .append('\n');
            texte.append("Echecs de verrouillage ....... : ").append(echecsOptimistes).append('\n');
            texte.append("Duree cumulee des requetes ... : ").append(dureeRequetesMs).append(" ms\n");
            if (!regions.estVide()) {
                texte.append(regions.texte()).append('\n');
            }
            if (regionsInactives > 0) {
                texte.append("Regions encore inutilisees ... : ").append(regionsInactives).append('\n');
            }
            return texte.toString().stripTrailing();
        }
    }
}
