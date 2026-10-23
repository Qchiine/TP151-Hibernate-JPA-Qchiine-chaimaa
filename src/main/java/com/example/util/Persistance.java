package com.example.util;

import java.util.function.Function;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;

/**
 * Point d'acces unique a la persistence "ReservationPU".
 * L'EntityManagerFactory est couteux : il est cree une seule fois.
 */
public final class Persistance {

    private static final String UNITE = "ReservationPU";

    private static volatile EntityManagerFactory fabrique;

    private Persistance() {
    }

    public static EntityManagerFactory fabrique() {
        EntityManagerFactory locale = fabrique;
        if (locale == null) {
            synchronized (Persistance.class) {
                locale = fabrique;
                if (locale == null) {
                    locale = Persistence.createEntityManagerFactory(UNITE);
                    fabrique = locale;
                }
            }
        }
        return locale;
    }

    /** Execute un traitement en lecture dans un EntityManager dedie, puis le ferme. */
    public static <T> T enLecture(Function<EntityManager, T> traitement) {
        EntityManager em = fabrique().createEntityManager();
        try {
            return traitement.apply(em);
        } finally {
            em.close();
        }
    }

    /** Execute un traitement en ecriture dans une transaction, avec rollback automatique. */
    public static <T> T dansUneTransaction(Function<EntityManager, T> traitement) {
        EntityManager em = fabrique().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            T resultat = traitement.apply(em);
            tx.commit();
            return resultat;
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    /** Ferme la fabrique : indispensable pour liberer le pool et les regions de cache. */
    public static void fermer() {
        EntityManagerFactory locale = fabrique;
        if (locale != null) {
            synchronized (Persistance.class) {
                if (fabrique != null) {
                    fabrique.close();
                    fabrique = null;
                }
            }
        }
    }
}
