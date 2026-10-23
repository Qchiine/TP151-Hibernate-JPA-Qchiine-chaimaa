package com.example.util;

/**
 * Coordonnees JDBC de la base de demonstration.
 *
 * <p>Elles reprennent volontairement les valeurs declarees dans
 * {@code META-INF/persistence.xml} : Hibernate masque le mot de passe dans les
 * proprietes de l'EntityManagerFactory, il faut donc une source commune pour
 * les outils qui travaillent en JDBC pur (comme le migrateur).</p>
 */
public final class ParametresJdbc {

    public static final String URL = "jdbc:h2:mem:reservation;DB_CLOSE_DELAY=-1";
    public static final String UTILISATEUR = "sa";
    public static final String MOT_DE_PASSE = "";

    private ParametresJdbc() {
    }

    public static MigrationExecutor nouveauMigrateur() {
        return new MigrationExecutor(URL, UTILISATEUR, MOT_DE_PASSE);
    }
}
