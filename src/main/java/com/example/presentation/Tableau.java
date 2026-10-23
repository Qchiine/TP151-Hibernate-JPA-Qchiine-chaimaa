package com.example.presentation;

import java.util.ArrayList;
import java.util.List;

/**
 * Petit moteur de rendu de tableaux texte, sans dependance externe.
 * Les colonnes sont dimensionnees sur leur contenu et les nombres sont alignes
 * a droite pour rester lisibles dans une console.
 */
public class Tableau {

    private static final char H = '\u2500';
    private static final char V = '\u2502';
    private static final char TL = '\u250C';
    private static final char TM = '\u252C';
    private static final char TR = '\u2510';
    private static final char ML = '\u251C';
    private static final char MM = '\u253C';
    private static final char MR = '\u2524';
    private static final char BL = '\u2514';
    private static final char BM = '\u2534';
    private static final char BR = '\u2518';

    private final String[] entetes;
    private final List<String[]> lignes = new ArrayList<>();

    public Tableau(String... entetes) {
        if (entetes == null || entetes.length == 0) {
            throw new IllegalArgumentException("Un tableau doit avoir au moins une colonne");
        }
        this.entetes = entetes.clone();
    }

    public Tableau ligne(Object... cellules) {
        if (cellules == null || cellules.length != entetes.length) {
            throw new IllegalArgumentException(
                    "Attendu " + entetes.length + " valeur(s), recu " + (cellules == null ? 0 : cellules.length));
        }
        String[] valeurs = new String[cellules.length];
        for (int i = 0; i < cellules.length; i++) {
            valeurs[i] = cellules[i] == null ? "" : String.valueOf(cellules[i]);
            if (valeurs[i].length() > 60) {
                valeurs[i] = valeurs[i].substring(0, 57) + "...";
            }
        }
        lignes.add(valeurs);
        return this;
    }

    public boolean estVide() {
        return lignes.isEmpty();
    }

    public String texte() {
        int[] largeurs = largeurs();
        StringBuilder sortie = new StringBuilder();

        sortie.append(separateur(TL, TM, TR, largeurs)).append('\n');
        sortie.append(rangee(entetes, largeurs, false)).append('\n');
        sortie.append(separateur(ML, MM, MR, largeurs)).append('\n');
        for (String[] ligne : lignes) {
            sortie.append(rangee(ligne, largeurs, true)).append('\n');
        }
        sortie.append(separateur(BL, BM, BR, largeurs));
        return sortie.toString();
    }

    public void afficher() {
        System.out.println(texte());
    }

    private int[] largeurs() {
        int[] largeurs = new int[entetes.length];
        for (int i = 0; i < entetes.length; i++) {
            largeurs[i] = entetes[i].length();
        }
        for (String[] ligne : lignes) {
            for (int i = 0; i < ligne.length; i++) {
                largeurs[i] = Math.max(largeurs[i], ligne[i].length());
            }
        }
        return largeurs;
    }

    private String separateur(char debut, char milieu, char fin, int[] largeurs) {
        StringBuilder ligne = new StringBuilder();
        ligne.append(debut);
        for (int i = 0; i < largeurs.length; i++) {
            for (int j = 0; j < largeurs[i] + 2; j++) {
                ligne.append(H);
            }
            if (i < largeurs.length - 1) {
                ligne.append(milieu);
            }
        }
        ligne.append(fin);
        return ligne.toString();
    }

    private String rangee(String[] cellules, int[] largeurs, boolean alignerLesNombres) {
        StringBuilder ligne = new StringBuilder();
        ligne.append(V);
        for (int i = 0; i < cellules.length; i++) {
            String cellule = cellules[i];
            boolean numerique = alignerLesNombres && cellule.matches("-?\\d+([.,]\\d+)?");
            int espaces = largeurs[i] - cellule.length();
            ligne.append(' ');
            if (numerique) {
                ligne.append(" ".repeat(Math.max(0, espaces))).append(cellule);
            } else {
                ligne.append(cellule).append(" ".repeat(Math.max(0, espaces)));
            }
            ligne.append(' ').append(V);
        }
        return ligne.toString();
    }
}
