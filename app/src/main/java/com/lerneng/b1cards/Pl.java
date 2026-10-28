package com.lerneng.b1cards;

/** Polskie liczby mnogie: 1 słowo, 2 słowa, 5 słów. */
final class Pl {
    private Pl() {
    }

    static String plural(int n, String one, String few, String many) {
        if (n == 1) {
            return one;
        }
        int m10 = n % 10;
        int m100 = n % 100;
        if (m10 >= 2 && m10 <= 4 && !(m100 >= 12 && m100 <= 14)) {
            return few;
        }
        return many;
    }

    static String words(int n) {
        return n + " " + plural(n, "słowo", "słowa", "słów");
    }

    static String days(int n) {
        return n + " " + plural(n, "dzień", "dni", "dni");
    }
}
