package com.lerneng.b1cards;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Ekran główny: seria 🔥, partia B1, test, moje zestawy, tematy B1, czarna lista, przypomnienia. */
public class MainActivity extends Activity {
    private static final String[] WEEKDAYS = {"Nd", "Pn", "Wt", "Śr", "Cz", "Pt", "So"};

    private LinearLayout content;
    private Db db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.setupWindow(this);
        db = Db.get(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 20), Ui.dp(this, 24), Ui.dp(this, 20), Ui.dp(this, 32));
        scroll.addView(content, new ScrollView.LayoutParams(Ui.MATCH, Ui.WRAP));
        setContentView(scroll);

        askNotificationPermissionOnce();
        Reminder.scheduleNext(this);
    }

    /** Android 13+: powiadomienia wymagają zgody. Pytamy tylko raz, przy pierwszym uruchomieniu. */
    private void askNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < 33) {
            return;
        }
        SharedPreferences p = getSharedPreferences("settings", MODE_PRIVATE);
        if (p.getBoolean("asked_notif", false)) {
            return;
        }
        p.edit().putBoolean("asked_notif", true).apply();
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        content.removeAllViews();
        int pending = db.pendingTestCount();
        TestOffer.sync(this, pending);

        // --- Nagłówek ---
        content.addView(Ui.text(this, "Słówka B1", 32, Ui.INK, true));
        content.addView(Ui.text(this, "Polski → English · słownictwo do technikum", 14, Ui.MUTED, false),
                Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 2, 0, 16));

        // --- Seria dzienna ---
        content.addView(streakCard(), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 16));

        // --- Ogólny postęp ---
        int total = db.totalBuiltinWords();
        int learned = db.learnedBuiltinWords();
        content.addView(Ui.text(this, "Opanowane: " + learned + " z " + total + " słów", 14, Ui.INK, false));
        content.addView(progressBar(learned, total, Ui.ACCENT), Ui.lp(this, Ui.MATCH, Ui.dp(this, 8), 0, 8, 0, 20));

        // --- Główna partia ---
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20));
        main.setBackground(Ui.ripple(this, Ui.ACCENT, 0, 20));
        main.addView(Ui.text(this, "Partia B1", 22, 0xFFFFFFFF, true));
        int left = db.mainLeft();
        String info = left == 0
                ? "Wszystkie słowa opanowane 🎉"
                : "Zostało " + left + " z " + Db.BATCH_SIZE + " słów w partii";
        main.addView(Ui.text(this, info, 15, 0xFFDBEAFE, false), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 4, 0, 0));
        main.setOnClickListener(v -> openStudy(Db.MAIN));
        content.addView(main, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 12));

        // --- Test ---
        content.addView(testCard(pending), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 24));

        // --- Moje zestawy ---
        List<Db.Pack> packs = db.packs();
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.text(this, "Moje zestawy", 20, Ui.INK, true), new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        Button add = Ui.button(this, "+ Nowy zestaw", Ui.ACCENT_SOFT, 0, Ui.ACCENT);
        add.setTextSize(14);
        add.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
        add.setOnClickListener(v -> startActivity(new Intent(this, PackEditActivity.class)));
        head.addView(add, new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP));
        content.addView(head);

        boolean anyCustom = false;
        for (Db.Pack p : packs) {
            if (!p.builtin) {
                anyCustom = true;
                content.addView(packRow(p), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
            }
        }
        if (!anyCustom) {
            content.addView(Ui.text(this,
                    "Tu pojawią się twoje zestawy. Kliknij „+ Nowy zestaw”, wklej słowa listą i ucz się tylko ich.",
                    14, Ui.MUTED, false), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 6, 0, 0));
        } else {
            content.addView(Ui.text(this, "Przytrzymaj zestaw, aby dodać słowa lub go usunąć.",
                    12, Ui.MUTED, false), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 8, 0, 0));
        }

        // --- Tematy B1 ---
        content.addView(Ui.text(this, "Tematy B1", 20, Ui.INK, true), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 28, 0, 0));
        for (Db.Pack p : packs) {
            if (p.builtin) {
                content.addView(packRow(p), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
            }
        }

        // --- Czarna lista ---
        String blTitle = "Czarna lista · " + db.blacklistCount();
        if (pending > 0) {
            blTitle += "  (do sprawdzenia: " + pending + ")";
        }
        Button bl = Ui.button(this, blTitle, Ui.GRAY_SOFT, 0, Ui.INK);
        bl.setOnClickListener(v -> startActivity(new Intent(this, BlacklistActivity.class)));
        content.addView(bl, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 28, 0, 0));

        // --- Przypomnienia ---
        String when = Reminder.enabled(this)
                ? String.format(Locale.US, "%02d:%02d", Reminder.hour(this), Reminder.minute(this))
                : "wyłączone";
        Button rem = Ui.button(this, "🔔 Przypomnienia: " + when, Ui.BG, Ui.LINE, Ui.MUTED);
        rem.setTextSize(14);
        rem.setOnClickListener(v -> reminderMenu());
        content.addView(rem, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 10, 0, 0));
    }

    // ------------------------------------------------------------------
    // Seria 🔥
    // ------------------------------------------------------------------
    private View streakCard() {
        int streak = db.streak();
        int today = db.practicedToday();
        boolean done = today >= Db.DAILY_GOAL;
        Set<String> doneDays = db.doneDays();

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
        card.setBackground(Ui.shape(this, Ui.ORANGE_SOFT, Ui.ORANGE_LINE, 20));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        final TextView flame = Ui.text(this, "🔥", 40, Ui.INK, false);
        flame.setAlpha(streak > 0 ? 1f : 0.35f);
        flame.setPadding(0, 0, Ui.dp(this, 12), 0);
        top.addView(flame);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        String title = streak > 0 ? Pl.days(streak) + " z rzędu" : "Zacznij swoją serię!";
        texts.addView(Ui.text(this, title, 22, Ui.INK, true));
        String sub = done
                ? "Cel na dziś zaliczony ✓"
                : "Dziś: " + today + " / " + Pl.words(Db.DAILY_GOAL);
        texts.addView(Ui.text(this, sub, 14, done ? Ui.GREEN : Ui.MUTED, done));
        top.addView(texts, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        card.addView(top);

        card.addView(progressBar(Math.min(today, Db.DAILY_GOAL), Db.DAILY_GOAL, Ui.ORANGE),
                Ui.lp(this, Ui.MATCH, Ui.dp(this, 8), 0, 12, 0, 12));

        // ostatnie 7 dni
        LinearLayout days = new LinearLayout(this);
        days.setOrientation(LinearLayout.HORIZONTAL);
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -6);
        for (int i = 0; i < 7; i++) {
            boolean isToday = i == 6;
            boolean ok = doneDays.contains(Db.dayKey(c));
            days.addView(dayDot(WEEKDAYS[c.get(Calendar.DAY_OF_WEEK) - 1], ok, isToday),
                    new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        card.addView(days);

        if (done) {
            flame.setScaleX(0.6f);
            flame.setScaleY(0.6f);
            flame.animate().scaleX(1f).scaleY(1f).setDuration(500)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
        }
        return card;
    }

    private View dayDot(String label, boolean ok, boolean isToday) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView dot = Ui.text(this, ok ? "✓" : "", 15, 0xFFFFFFFF, true);
        dot.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(ok ? Ui.ORANGE : 0xFFFFFFFF);
        g.setStroke(Ui.dp(this, isToday ? 2 : 1), ok ? Ui.ORANGE : (isToday ? Ui.ORANGE : Ui.LINE));
        dot.setBackground(g);
        col.addView(dot, new LinearLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30)));

        TextView t = Ui.text(this, label, 11, isToday ? Ui.INK : Ui.MUTED, isToday);
        col.addView(t, Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 4, 0, 0));
        return col;
    }

    // ------------------------------------------------------------------
    // Test
    // ------------------------------------------------------------------
    private View testCard(final int pending) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 16));
        boolean ready = pending > 0;
        box.setBackground(Ui.ripple(this, ready ? Ui.ACCENT_SOFT : Ui.GRAY_SOFT,
                ready ? Ui.ACCENT_LINE : 0, 20));
        box.addView(Ui.text(this, "Test sprawdzający 📝", 18, ready ? Ui.ACCENT : Ui.MUTED, true));

        String info;
        if (!ready) {
            info = "Brak słów do sprawdzenia. Oznacz słowa jako opanowane, a pojawią się tutaj.";
        } else if (pending >= TestOffer.FIRST) {
            info = "Czas na test! Do sprawdzenia: " + Pl.words(pending) + ". Wpisz angielskie tłumaczenie.";
        } else {
            info = "Do sprawdzenia: " + Pl.words(pending) + ". Wpisz angielskie tłumaczenie.";
        }
        box.addView(Ui.text(this, info, 14, Ui.MUTED, false), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 4, 0, 0));

        box.setOnClickListener(v -> {
            if (pending > 0) {
                startActivity(new Intent(this, TestActivity.class));
            } else {
                Toast.makeText(this, "Najpierw oznacz kilka słów jako opanowane", Toast.LENGTH_SHORT).show();
            }
        });
        return box;
    }

    // ------------------------------------------------------------------
    // Pozostałe elementy
    // ------------------------------------------------------------------
    private View progressBar(int value, int total, int color) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackground(Ui.shape(this, Ui.GRAY_SOFT, 0, 4));
        float done = total == 0 ? 0f : Math.min(1f, (float) value / total);
        View fill = new View(this);
        fill.setBackground(Ui.shape(this, color, 0, 4));
        bar.addView(fill, new LinearLayout.LayoutParams(0, Ui.MATCH, done));
        View rest = new View(this);
        bar.addView(rest, new LinearLayout.LayoutParams(0, Ui.MATCH, 1f - done));
        return bar;
    }

    private View packRow(final Db.Pack p) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        row.setBackground(Ui.ripple(this, Ui.BG, Ui.LINE, 14));

        TextView name = Ui.text(this, p.name, 16, Ui.INK, false);
        row.addView(name, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));

        String right = p.left == 0 ? "gotowe ✓" : p.left + " / " + p.total;
        row.addView(Ui.text(this, right, 14, p.left == 0 ? Ui.ACCENT : Ui.MUTED, false));

        row.setOnClickListener(v -> openStudy(p.id));
        if (!p.builtin) {
            row.setOnLongClickListener(v -> {
                packMenu(p);
                return true;
            });
        }
        return row;
    }

    private void openStudy(long packId) {
        Intent i = new Intent(this, StudyActivity.class);
        i.putExtra(StudyActivity.EXTRA_PACK, packId);
        startActivity(i);
    }

    private void packMenu(final Db.Pack p) {
        new AlertDialog.Builder(this)
                .setTitle(p.name)
                .setItems(new String[]{"Dodaj słowa", "Usuń zestaw"}, (d, which) -> {
                    if (which == 0) {
                        Intent i = new Intent(this, PackEditActivity.class);
                        i.putExtra(PackEditActivity.EXTRA_PACK, p.id);
                        startActivity(i);
                    } else {
                        confirmDelete(p);
                    }
                })
                .show();
    }

    private void confirmDelete(final Db.Pack p) {
        new AlertDialog.Builder(this)
                .setTitle("Usunąć zestaw „" + p.name + "”?")
                .setMessage("Słowa z wbudowanej bazy zostaną, znikną tylko twoje własne.")
                .setNegativeButton("Anuluj", null)
                .setPositiveButton("Usuń", (d, w) -> {
                    db.deletePack(p.id);
                    Toast.makeText(this, "Zestaw usunięty", Toast.LENGTH_SHORT).show();
                    build();
                })
                .show();
    }

    // ------------------------------------------------------------------
    // Przypomnienia
    // ------------------------------------------------------------------
    private void reminderMenu() {
        final boolean on = Reminder.enabled(this);
        String[] items = on
                ? new String[]{"Zmień godzinę", "Wyłącz przypomnienia"}
                : new String[]{"Włącz przypomnienia"};
        new AlertDialog.Builder(this)
                .setTitle("Przypomnienia o nauce")
                .setItems(items, (d, which) -> {
                    if (on && which == 0) {
                        pickTime();
                    } else if (on) {
                        Reminder.setEnabled(this, false);
                        build();
                    } else {
                        Reminder.setEnabled(this, true);
                        build();
                    }
                })
                .show();
    }

    private void pickTime() {
        new TimePickerDialog(this, (view, h, m) -> {
            Reminder.setTime(this, h, m);
            Reminder.setEnabled(this, true);
            Toast.makeText(this, String.format(Locale.US, "Przypomnienie ustawione na %02d:%02d", h, m),
                    Toast.LENGTH_SHORT).show();
            build();
        }, Reminder.hour(this), Reminder.minute(this), true).show();
    }
}
