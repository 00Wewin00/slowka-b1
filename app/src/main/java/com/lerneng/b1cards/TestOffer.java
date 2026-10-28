package com.lerneng.b1cards;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Kiedy proponować test: gdy na czarnej liście jest 50 niesprawdzonych słów,
 * a potem za każdym razem, gdy przybędzie kolejnych 10 (60, 70, 80...).
 */
final class TestOffer {
    static final int FIRST = 50;
    static final int STEP = 10;
    private static final String KEY = "offer_floor";

    private TestOffer() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("state", Context.MODE_PRIVATE);
    }

    /** Dostosowuje zapamiętany próg, gdy liczba słów do sprawdzenia spadła (np. po teście). */
    static void sync(Context c, int pending) {
        SharedPreferences p = prefs(c);
        int last = p.getInt(KEY, 0);
        int floor = pending / STEP * STEP;
        int fixed = last;
        if (pending < FIRST) {
            fixed = 0;
        } else if (floor < last) {
            fixed = floor;
        }
        if (fixed != last) {
            p.edit().putInt(KEY, fixed).apply();
        }
    }

    /** Zwraca true dokładnie raz przy każdym nowym progu (50, 60, 70...). */
    static boolean check(Context c, int pending) {
        sync(c, pending);
        if (pending < FIRST) {
            return false;
        }
        SharedPreferences p = prefs(c);
        int last = p.getInt(KEY, 0);
        int floor = pending / STEP * STEP;
        if (floor > last) {
            p.edit().putInt(KEY, floor).apply();
            return true;
        }
        return false;
    }
}
