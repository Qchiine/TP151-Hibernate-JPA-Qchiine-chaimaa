package com.example.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Compte et memorise les ordres SQL reellement envoyes a la base.
 * Sert de preuve chiffree pour le cache de second niveau.
 */
public class CompteurSql implements StatementInspector {

    private static final AtomicLong TOTAL = new AtomicLong();
    private static final List<String> TRACE = Collections.synchronizedList(new ArrayList<>());

    public static void reinitialiser() {
        TOTAL.set(0L);
        TRACE.clear();
    }

    public static long total() {
        return TOTAL.get();
    }

    public static List<String> trace() {
        synchronized (TRACE) {
            return new ArrayList<>(TRACE);
        }
    }

    /** Nombre de SELECT distincts par leur debut : met en evidence le N+1. */
    public static Map<String, Integer> motifs() {
        Map<String, Integer> comptes = new LinkedHashMap<>();
        for (String sql : trace()) {
            String motif = sql.length() > 48 ? sql.substring(0, 48) : sql;
            comptes.merge(motif, 1, Integer::sum);
        }
        return comptes;
    }

    public static long nombreDeSelect() {
        long total = 0L;
        for (String sql : trace()) {
            if (sql.startsWith("select")) {
                total++;
            }
        }
        return total;
    }

    @Override
    public String inspect(String sql) {
        TOTAL.incrementAndGet();
        synchronized (TRACE) {
            if (TRACE.size() < 400) {
                TRACE.add(sql.trim().replaceAll("\\s+", " "));
            }
        }
        return sql;
    }
}
