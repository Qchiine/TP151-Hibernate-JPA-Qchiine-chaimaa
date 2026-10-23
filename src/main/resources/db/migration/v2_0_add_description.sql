-- ============================================================================
-- Migration version 2.0
-- Objet : faire evoluer le schema de la version 1.0 vers la version 2.0.
--   * la table "salles" recoit une colonne libre "description" ;
--   * un index unique protege l'unicite de l'adresse email des utilisateurs.
-- Les gardes IF NOT EXISTS rendent le script rejouable (idempotence) : il peut
-- etre relance sur une base deja migree sans provoquer d'erreur.
-- ============================================================================

-- Ajout de la colonne "description" a la table "salles"
ALTER TABLE salles ADD COLUMN IF NOT EXISTS description VARCHAR(255);

-- Ajout d'un index unique sur la colonne "email" de la table "utilisateurs"
CREATE UNIQUE INDEX IF NOT EXISTS idx_utilisateur_email ON utilisateurs(email);

-- Marquage de la version applicative du schema
CREATE TABLE IF NOT EXISTS schema_version (
    version       VARCHAR(20)  NOT NULL PRIMARY KEY,
    script        VARCHAR(255) NOT NULL,
    applique_le   TIMESTAMP    NOT NULL
);
