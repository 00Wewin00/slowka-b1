package com.lerneng.b1cards;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Baza danych aplikacji (SQLite).
 *
 * words      — wszystkie słowa (wbudowane z assets/words.csv + własne)
 * packs      — zestawy (tematy B1 = builtin, własne = custom)
 * pack_words — które słowa są w których zestawach
 * blacklist  — słowa opanowane; tested = 1 gdy zaliczone w teście na 100%
 * batch      — bieżąca partia 50 słów
 * practice   — słowa przećwiczone danego dnia (do serii 🔥)
 */
public class Db extends SQLiteOpenHelper {
    static final int BATCH_SIZE = 50;
    /** Ile różnych słów dziennie trzeba przećwiczyć, żeby seria trwała. */
    static final int DAILY_GOAL = 2;
    /** Specjalny id głównej partii B1. */
    static final long MAIN = -1L;

    static class Word {
        long id;
        String pl;
        String en;
        boolean tested;
    }

    static class Pack {
        long id;
        String name;
        boolean builtin;
        int total;
        int left;
    }

    private static Db instance;
    private final Context ctx;

    static synchronized Db get(Context c) {
        if (instance == null) {
            instance = new Db(c.getApplicationContext());
        }
        return instance;
    }

    private Db(Context c) {
        super(c, "cards.db", null, 3);
        ctx = c;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE words("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "pl TEXT NOT NULL,"
                + "en TEXT NOT NULL,"
                + "q TEXT NOT NULL,"
                + "ord INTEGER NOT NULL DEFAULT 0,"
                + "custom INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE packs("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL,"
                + "builtin INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE pack_words("
                + "pack_id INTEGER NOT NULL,"
                + "word_id INTEGER NOT NULL,"
                + "PRIMARY KEY(pack_id, word_id))");
        db.execSQL("CREATE TABLE blacklist("
                + "word_id INTEGER PRIMARY KEY,"
                + "tested INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE batch(word_id INTEGER PRIMARY KEY, pos INTEGER NOT NULL)");
        createPractice(db);
        importWords(db);
    }

    private void createPractice(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS practice("
                + "day TEXT NOT NULL,"
                + "word_id INTEGER NOT NULL,"
                + "PRIMARY KEY(day, word_id))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            // Wersja 1.1: test (pole tested) i seria dzienna.
            // Wszystkie dotychczasowe słowa z czarnej listy czekają na pierwszy test.
            // Sprawdzamy kolumnę, żeby migracja była bezpieczna także po wcześniejszych wersjach testowych.
            if (!hasColumn(db, "blacklist", "tested")) {
                db.execSQL("ALTER TABLE blacklist ADD COLUMN tested INTEGER NOT NULL DEFAULT 0");
            }
            createPractice(db);
        }
    }

    private static boolean hasColumn(SQLiteDatabase db, String table, String column) {
        Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        try {
            int nameIdx = c.getColumnIndexOrThrow("name");
            while (c.moveToNext()) {
                if (column.equals(c.getString(nameIdx))) {
                    return true;
                }
            }
        } finally {
            c.close();
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Import słów z assets/words.csv  (format wiersza: temat|polski|english)
    // ------------------------------------------------------------------
    private void importWords(SQLiteDatabase db) {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(ctx.getAssets().open("words.csv"), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.replace("﻿", "").trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.split("\\|");
                if (p.length < 3) {
                    continue;
                }
                rows.add(new String[]{p[0].trim(), p[1].trim(), p[2].trim()});
            }
        } catch (IOException e) {
            throw new RuntimeException("Nie udało się wczytać words.csv", e);
        }

        // Stała „losowa” kolejność: partie po 50 słów mieszają różne tematy.
        int n = rows.size();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            order.add(i);
        }
        Collections.shuffle(order, new Random(2024));

        Map<String, Long> packIds = new HashMap<>();
        Map<String, Long> wordIds = new HashMap<>();
        for (int i = 0; i < n; i++) {
            String[] r = rows.get(i);
            String key = r[1].toLowerCase(Locale.ROOT) + "\u0001" + r[2].toLowerCase(Locale.ROOT);
            Long wid = wordIds.get(key);
            if (wid == null) {
                ContentValues v = new ContentValues();
                v.put("pl", r[1]);
                v.put("en", r[2]);
                v.put("q", (r[1] + " " + r[2]).toLowerCase(Locale.ROOT));
                v.put("ord", order.get(i));
                v.put("custom", 0);
                wid = db.insert("words", null, v);
                wordIds.put(key, wid);
            }
            Long pid = packIds.get(r[0]);
            if (pid == null) {
                ContentValues pv = new ContentValues();
                pv.put("name", r[0]);
                pv.put("builtin", 1);
                pid = db.insert("packs", null, pv);
                packIds.put(r[0], pid);
            }
            ContentValues pw = new ContentValues();
            pw.put("pack_id", pid);
            pw.put("word_id", wid);
            db.insertWithOnConflict("pack_words", null, pw, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    // ------------------------------------------------------------------
    // Zapytania pomocnicze
    // ------------------------------------------------------------------
    private int scalar(String sql, String... args) {
        Cursor c = getReadableDatabase().rawQuery(sql, args);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    /** Kolumny: id, pl, en [, tested]. */
    private List<Word> words(String sql, String... args) {
        List<Word> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(sql, args);
        try {
            boolean hasTested = c.getColumnCount() > 3;
            while (c.moveToNext()) {
                Word w = new Word();
                w.id = c.getLong(0);
                w.pl = c.getString(1);
                w.en = c.getString(2);
                w.tested = hasTested && c.getInt(3) == 1;
                out.add(w);
            }
        } finally {
            c.close();
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Główna partia B1 (po 50 słów)
    // ------------------------------------------------------------------

    /** Słowa bieżącej partii spoza czarnej listy. Gdy partia się skończyła — tworzy nową. */
    List<Word> mainBatch() {
        int left = scalar("SELECT COUNT(*) FROM batch WHERE word_id NOT IN (SELECT word_id FROM blacklist)");
        if (left == 0) {
            newBatch();
        }
        return words("SELECT w.id, w.pl, w.en FROM batch b JOIN words w ON w.id = b.word_id "
                + "WHERE b.word_id NOT IN (SELECT word_id FROM blacklist) ORDER BY b.pos");
    }

    private void newBatch() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM batch");
            Cursor c = db.rawQuery("SELECT id FROM words WHERE custom = 0 "
                    + "AND id NOT IN (SELECT word_id FROM blacklist) ORDER BY ord LIMIT " + BATCH_SIZE, null);
            try {
                int pos = 0;
                while (c.moveToNext()) {
                    ContentValues v = new ContentValues();
                    v.put("word_id", c.getLong(0));
                    v.put("pos", pos++);
                    db.insert("batch", null, v);
                }
            } finally {
                c.close();
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Ile słów zostało w bieżącej partii (dla ekranu głównego). */
    int mainLeft() {
        int left = scalar("SELECT COUNT(*) FROM batch WHERE word_id NOT IN (SELECT word_id FROM blacklist)");
        if (left > 0) {
            return left;
        }
        int available = scalar("SELECT COUNT(*) FROM words WHERE custom = 0 "
                + "AND id NOT IN (SELECT word_id FROM blacklist)");
        return Math.min(BATCH_SIZE, available);
    }

    // ------------------------------------------------------------------
    // Zestawy
    // ------------------------------------------------------------------
    List<Pack> packs() {
        List<Pack> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT p.id, p.name, p.builtin, "
                        + "(SELECT COUNT(*) FROM pack_words x WHERE x.pack_id = p.id), "
                        + "(SELECT COUNT(*) FROM pack_words x WHERE x.pack_id = p.id "
                        + "   AND x.word_id NOT IN (SELECT word_id FROM blacklist)) "
                        + "FROM packs p ORDER BY p.builtin ASC, p.id ASC", null);
        try {
            while (c.moveToNext()) {
                Pack p = new Pack();
                p.id = c.getLong(0);
                p.name = c.getString(1);
                p.builtin = c.getInt(2) == 1;
                p.total = c.getInt(3);
                p.left = c.getInt(4);
                out.add(p);
            }
        } finally {
            c.close();
        }
        return out;
    }

    String packName(long packId) {
        Cursor c = getReadableDatabase().rawQuery("SELECT name FROM packs WHERE id = ?",
                new String[]{String.valueOf(packId)});
        try {
            return c.moveToFirst() ? c.getString(0) : "";
        } finally {
            c.close();
        }
    }

    List<Word> packWords(long packId) {
        return words("SELECT w.id, w.pl, w.en FROM pack_words p JOIN words w ON w.id = p.word_id "
                + "WHERE p.pack_id = ? AND p.word_id NOT IN (SELECT word_id FROM blacklist) "
                + "ORDER BY p.rowid", String.valueOf(packId));
    }

    /**
     * Tworzy zestaw (packId < 0) albo dodaje słowa do istniejącego własnego zestawu.
     * pairs — nowe słowa {polski, english}; existingIds — słowa wybrane z bazy.
     * Słowa dodane wprost do zestawu są usuwane z czarnej listy.
     */
    long savePack(long packId, String name, List<String[]> pairs, Collection<Long> existingIds) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (packId < 0) {
                ContentValues pv = new ContentValues();
                pv.put("name", name);
                pv.put("builtin", 0);
                packId = db.insert("packs", null, pv);
            } else {
                db.execSQL("UPDATE packs SET name = ? WHERE id = ? AND builtin = 0",
                        new Object[]{name, packId});
            }
            for (String[] p : pairs) {
                addToPack(db, packId, findOrCreateWord(db, p[0], p[1]));
            }
            for (Long id : existingIds) {
                addToPack(db, packId, id);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return packId;
    }

    private long findOrCreateWord(SQLiteDatabase db, String pl, String en) {
        String q = (pl + " " + en).toLowerCase(Locale.ROOT);
        Cursor c = db.rawQuery("SELECT id FROM words WHERE q = ? LIMIT 1", new String[]{q});
        try {
            if (c.moveToFirst()) {
                return c.getLong(0);
            }
        } finally {
            c.close();
        }
        ContentValues v = new ContentValues();
        v.put("pl", pl);
        v.put("en", en);
        v.put("q", q);
        v.put("ord", 0);
        v.put("custom", 1);
        return db.insert("words", null, v);
    }

    private void addToPack(SQLiteDatabase db, long packId, long wordId) {
        ContentValues pw = new ContentValues();
        pw.put("pack_id", packId);
        pw.put("word_id", wordId);
        db.insertWithOnConflict("pack_words", null, pw, SQLiteDatabase.CONFLICT_IGNORE);
        db.execSQL("DELETE FROM blacklist WHERE word_id = ?", new Object[]{wordId});
    }

    void deletePack(long packId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("DELETE FROM pack_words WHERE pack_id = ? "
                    + "AND pack_id IN (SELECT id FROM packs WHERE builtin = 0)", new Object[]{packId});
            db.execSQL("DELETE FROM packs WHERE id = ? AND builtin = 0", new Object[]{packId});
            db.execSQL("DELETE FROM words WHERE custom = 1 AND id NOT IN (SELECT word_id FROM pack_words)");
            db.execSQL("DELETE FROM blacklist WHERE word_id NOT IN (SELECT id FROM words)");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Wyszukiwanie w całej bazie (słowo polskie lub angielskie). */
    List<Word> search(String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return new ArrayList<>();
        }
        return words("SELECT id, pl, en FROM words WHERE q LIKE ? ORDER BY pl LIMIT 40", "%" + q + "%");
    }

    // ------------------------------------------------------------------
    // Czarna lista i testy
    // ------------------------------------------------------------------

    /** Dodaje słowo do czarnej listy; od nowa czeka ono na test (tested = 0). */
    void blacklist(long wordId) {
        getWritableDatabase().execSQL("INSERT OR IGNORE INTO blacklist(word_id, tested) VALUES(?, 0)",
                new Object[]{wordId});
    }

    void unblacklist(long wordId) {
        getWritableDatabase().execSQL("DELETE FROM blacklist WHERE word_id = ?", new Object[]{wordId});
    }

    void unblacklistAll() {
        getWritableDatabase().execSQL("DELETE FROM blacklist");
    }

    /** Cała czarna lista; niesprawdzone słowa najpierw. */
    List<Word> blacklisted() {
        return words("SELECT w.id, w.pl, w.en, b.tested FROM blacklist b JOIN words w ON w.id = b.word_id "
                + "ORDER BY b.tested, w.pl");
    }

    int blacklistCount() {
        return scalar("SELECT COUNT(*) FROM blacklist");
    }

    /** Słowa z czarnej listy, które jeszcze nie przeszły testu na 100% — losowa kolejność. */
    List<Word> pendingTestWords() {
        return words("SELECT w.id, w.pl, w.en FROM blacklist b JOIN words w ON w.id = b.word_id "
                + "WHERE b.tested = 0 ORDER BY RANDOM()");
    }

    int pendingTestCount() {
        return scalar("SELECT COUNT(*) FROM blacklist WHERE tested = 0");
    }

    /** Test zaliczony na 100%: te słowa nie pojawią się już w kolejnych testach. */
    void markTested(Collection<Long> ids) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Long id : ids) {
                db.execSQL("UPDATE blacklist SET tested = 1 WHERE word_id = ?", new Object[]{id});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    int totalBuiltinWords() {
        return scalar("SELECT COUNT(*) FROM words WHERE custom = 0");
    }

    int learnedBuiltinWords() {
        return scalar("SELECT COUNT(*) FROM blacklist b JOIN words w ON w.id = b.word_id WHERE w.custom = 0");
    }

    // ------------------------------------------------------------------
    // Seria dzienna 🔥
    // ------------------------------------------------------------------
    static String dayKey(Calendar c) {
        return String.format(Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    static String today() {
        return dayKey(Calendar.getInstance());
    }

    /** Zapisuje, że słowo zostało dziś przećwiczone. Zwraca true, jeśli to nowe słowo na dziś. */
    private boolean markPracticed(long wordId) {
        ContentValues v = new ContentValues();
        v.put("day", today());
        v.put("word_id", wordId);
        return getWritableDatabase().insertWithOnConflict("practice", null, v,
                SQLiteDatabase.CONFLICT_IGNORE) != -1;
    }

    /**
     * Słowo przećwiczone: obrócona fiszka, słowo oznaczone jako opanowane
     * albo poprawna odpowiedź w teście. Gdy dzienny cel zostaje właśnie osiągnięty — pokazuje komunikat.
     */
    void practiced(long wordId) {
        if (!markPracticed(wordId)) {
            return;
        }
        if (practicedToday() == DAILY_GOAL) {
            Toast.makeText(ctx, "🔥 Cel na dziś zaliczony! Seria: " + Pl.days(streak()),
                    Toast.LENGTH_LONG).show();
        }
    }

    int practicedToday() {
        return scalar("SELECT COUNT(*) FROM practice WHERE day = ?", today());
    }

    /** Dni, w których osiągnięto dzienny cel. */
    Set<String> doneDays() {
        Set<String> out = new HashSet<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT day FROM practice GROUP BY day HAVING COUNT(*) >= " + DAILY_GOAL, null);
        try {
            while (c.moveToNext()) {
                out.add(c.getString(0));
            }
        } finally {
            c.close();
        }
        return out;
    }

    /** Ile dni z rzędu osiągnięto cel (dzisiejszy dzień może jeszcze nie być zaliczony). */
    int streak() {
        Set<String> done = doneDays();
        Calendar c = Calendar.getInstance();
        if (!done.contains(dayKey(c))) {
            c.add(Calendar.DAY_OF_YEAR, -1);
        }
        int n = 0;
        while (done.contains(dayKey(c))) {
            n++;
            c.add(Calendar.DAY_OF_YEAR, -1);
        }
        return n;
    }
}
