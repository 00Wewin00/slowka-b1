package com.lerneng.b1cards;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * База даних застосунку (SQLite).
 *
 * words      — усі слова (вбудовані з assets/words.csv + власні)
 * packs      — паки (теми B1 = builtin, власні = custom)
 * pack_words — які слова в яких паках
 * blacklist  — слова, які вже вивчені й більше не показуються
 * batch      — поточна партія з 50 слів основного режиму
 */
public class Db extends SQLiteOpenHelper {
    static final int BATCH_SIZE = 50;
    /** Спеціальний id для основної партії B1. */
    static final long MAIN = -1L;

    static class Word {
        long id;
        String pl;
        String en;
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
        super(c, "cards.db", null, 1);
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
        db.execSQL("CREATE TABLE blacklist(word_id INTEGER PRIMARY KEY)");
        db.execSQL("CREATE TABLE batch(word_id INTEGER PRIMARY KEY, pos INTEGER NOT NULL)");
        importWords(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // поки що нічого
    }

    // ------------------------------------------------------------------
    // Імпорт слів із assets/words.csv  (формат рядка: тема|polski|english)
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
            throw new RuntimeException("Не вдалося прочитати words.csv", e);
        }

        // Фіксований «випадковий» порядок: партії по 50 слів змішують різні теми.
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
    // Допоміжні запити
    // ------------------------------------------------------------------
    private int scalar(String sql, String... args) {
        Cursor c = getReadableDatabase().rawQuery(sql, args);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    private List<Word> words(String sql, String... args) {
        List<Word> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(sql, args);
        try {
            while (c.moveToNext()) {
                Word w = new Word();
                w.id = c.getLong(0);
                w.pl = c.getString(1);
                w.en = c.getString(2);
                out.add(w);
            }
        } finally {
            c.close();
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Основна партія B1 (по 50 слів)
    // ------------------------------------------------------------------

    /** Слова поточної партії, яких ще немає в чорному списку. Коли партія закінчилась — створює нову. */
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

    /** Скільки слів залишилось у поточній партії (для головного екрана). */
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
    // Паки
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
     * Створює пак (packId < 0) або додає слова в існуючий власний пак.
     * pairs — нові слова {polski, english}; existingIds — слова, вибрані з бази.
     * Слова, які явно додають у пак, прибираються з чорного списку.
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

    /** Пошук по всій базі (польське або англійське слово). */
    List<Word> search(String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return new ArrayList<>();
        }
        return words("SELECT id, pl, en FROM words WHERE q LIKE ? ORDER BY pl LIMIT 40", "%" + q + "%");
    }

    // ------------------------------------------------------------------
    // Чорний список
    // ------------------------------------------------------------------
    void blacklist(long wordId) {
        getWritableDatabase().execSQL("INSERT OR IGNORE INTO blacklist(word_id) VALUES(?)",
                new Object[]{wordId});
    }

    void unblacklist(long wordId) {
        getWritableDatabase().execSQL("DELETE FROM blacklist WHERE word_id = ?", new Object[]{wordId});
    }

    void unblacklistAll() {
        getWritableDatabase().execSQL("DELETE FROM blacklist");
    }

    List<Word> blacklisted() {
        return words("SELECT w.id, w.pl, w.en FROM blacklist b JOIN words w ON w.id = b.word_id "
                + "ORDER BY w.pl");
    }

    int blacklistCount() {
        return scalar("SELECT COUNT(*) FROM blacklist");
    }

    int totalBuiltinWords() {
        return scalar("SELECT COUNT(*) FROM words WHERE custom = 0");
    }

    int learnedBuiltinWords() {
        return scalar("SELECT COUNT(*) FROM blacklist b JOIN words w ON w.id = b.word_id WHERE w.custom = 0");
    }
}
