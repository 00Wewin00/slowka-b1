package com.lerneng.b1cards;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Sprawdzanie odpowiedzi we sprawdzianie.
 *
 * Reguły:
 *  - wielkość liter nie ma znaczenia („Apple” = „apple”),
 *  - spacje na brzegach, podwójne spacje, interpunkcja, myślniki i apostrofy są ignorowane,
 *  - akcenty są ignorowane („fiance” = „fiancé”),
 *  - gdy w bazie jest kilka wariantów oddzielonych „/” (np. „flat / apartment”),
 *    wystarczy wpisać którykolwiek z nich,
 *  - tekst w nawiasach można pominąć („complaint (about a product)” → „complaint”),
 *  - przy czasownikach „to” na początku jest opcjonalne („to rent” = „rent”).
 *
 * Literówka = błąd.
 */
final class Answers {
    private Answers() {
    }

    static boolean matches(String typed, String expected) {
        String t = norm(typed);
        if (t.isEmpty()) {
            return false;
        }
        return variants(expected).contains(t);
    }

    static Set<String> variants(String expected) {
        Set<String> out = new LinkedHashSet<>();
        addVariant(out, expected);              // cały zapis, np. „flat apartment”
        for (String part : parts(expected)) {
            addVariant(out, part);
        }
        return out;
    }

    /**
     * Dzieli zapis wzorcowy na osobne tłumaczenia po „/” lub „;”.
     * Znaki w nawiasach się nie liczą: „to change (trains / buses)” to jedno tłumaczenie.
     */
    static List<String> parts(String expected) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < expected.length(); i++) {
            char ch = expected.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')' && depth > 0) {
                depth--;
            }
            if (depth == 0 && (ch == '/' || ch == ';')) {
                addPart(out, cur);
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        addPart(out, cur);
        return out;
    }

    private static void addPart(List<String> out, StringBuilder cur) {
        String s = cur.toString().trim();
        if (!s.isEmpty()) {
            out.add(s);
        }
    }

    /**
     * Pozostałe tłumaczenia, których NIE wpisano. Np. dla „flat / apartment” i odpowiedzi „flat”
     * zwraca [„apartment”]. Pusta lista, gdy słowo ma jedno tłumaczenie albo wpisano wszystko naraz.
     */
    static List<String> others(String typed, String expected) {
        List<String> all = parts(expected);
        List<String> out = new ArrayList<>();
        String t = norm(typed);
        if (all.size() < 2 || t.isEmpty()) {
            return out;
        }
        boolean matchedPart = false;
        for (String p : all) {
            Set<String> v = new LinkedHashSet<>();
            addVariant(v, p);
            if (v.contains(t)) {
                matchedPart = true;
            } else {
                out.add(p);
            }
        }
        if (!matchedPart) {
            out.clear();
        }
        return out;
    }

    private static void addVariant(Set<String> out, String part) {
        String noParen = part.replaceAll("\\([^)]*\\)", " ");
        String withParen = part.replace("(", " ").replace(")", " ");
        for (String p : new String[]{noParen, withParen}) {
            String n = norm(p);
            if (n.isEmpty()) {
                continue;
            }
            out.add(n);
            if (n.startsWith("to ") && n.length() > 3) {
                out.add(n.substring(3));
            }
        }
    }

    static String norm(String s) {
        if (s == null) {
            return "";
        }
        String x = s.toLowerCase(Locale.ROOT);
        x = Normalizer.normalize(x, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        x = x.replace("'", "").replace("’", "").replace("`", "");
        x = x.replace('-', ' ').replace('–', ' ').replace('—', ' ');
        x = x.replaceAll("[^\\p{L}\\p{N} ]", " ");
        x = x.replaceAll("\\s+", " ").trim();
        return x;
    }
}
