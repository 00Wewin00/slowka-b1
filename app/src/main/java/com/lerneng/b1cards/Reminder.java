package com.lerneng.b1cards;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

/**
 * Przypomnienia w stylu Duolingo: powiadomienia z krótkimi hasłami (assets/phrases.txt).
 *
 * Slot 0 — o wybranej godzinie (domyślnie 18:00), tylko jeśli dzisiejszy cel nie jest zaliczony.
 * Slot 1 — „ostatnia szansa” o 21:30, tylko gdy trwa seria i cel nadal nie jest zaliczony.
 *
 * Alarmy są niedokładne (setAndAllowWhileIdle), więc nie potrzeba specjalnych uprawnień.
 * Po każdym odpaleniu ustawiamy następny alarm; po restarcie telefonu robi to BootReceiver.
 */
final class Reminder {
    static final String CHANNEL = "reminders";
    private static final int LAST_CHANCE_H = 21;
    private static final int LAST_CHANCE_M = 30;

    private Reminder() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return prefs(c).getBoolean("remind_on", true);
    }

    static int hour(Context c) {
        return prefs(c).getInt("remind_h", 18);
    }

    static int minute(Context c) {
        return prefs(c).getInt("remind_m", 0);
    }

    static void setEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean("remind_on", on).apply();
        scheduleNext(c);
    }

    static void setTime(Context c, int h, int m) {
        prefs(c).edit().putInt("remind_h", h).putInt("remind_m", m).apply();
        scheduleNext(c);
    }

    private static int immutableFlag() {
        return Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    private static PendingIntent alarmIntent(Context c, int slot) {
        Intent i = new Intent(c, ReminderReceiver.class);
        i.putExtra("slot", slot);
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
    }

    private static long nextTime(int h, int m, long nowMs) {
        Calendar t = Calendar.getInstance();
        t.set(Calendar.HOUR_OF_DAY, h);
        t.set(Calendar.MINUTE, m);
        t.set(Calendar.SECOND, 0);
        t.set(Calendar.MILLISECOND, 0);
        if (t.getTimeInMillis() <= nowMs + 1000) {
            t.add(Calendar.DAY_OF_YEAR, 1);
        }
        return t.getTimeInMillis();
    }

    /** Ustawia (lub kasuje) najbliższy alarm. Można wywoływać wielokrotnie. */
    static void scheduleNext(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        am.cancel(alarmIntent(c, 0));
        if (!enabled(c)) {
            return;
        }
        long now = System.currentTimeMillis();
        int h = hour(c);
        int m = minute(c);
        long best = nextTime(h, m, now);
        int slot = 0;
        if (h * 60 + m < LAST_CHANCE_H * 60) {
            long last = nextTime(LAST_CHANCE_H, LAST_CHANCE_M, now);
            if (last < best) {
                best = last;
                slot = 1;
            }
        }
        PendingIntent pi = alarmIntent(c, slot);
        if (Build.VERSION.SDK_INT >= 23) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, best, pi);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, best, pi);
        }
    }

    // ------------------------------------------------------------------
    // Hasła
    // ------------------------------------------------------------------
    private static List<String> loadPhrases(Context c) {
        List<String> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(c.getAssets().open("phrases.txt"), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.replace("﻿", "").trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    out.add(line);
                }
            }
        } catch (IOException e) {
            // użyjemy hasła awaryjnego
        }
        return out;
    }

    /** Losuje hasło (bez powtórzenia poprzedniego). Hasła z {streak} tylko gdy trwa seria. */
    static String pickPhrase(Context c, int streak, int slot) {
        List<String> pool = new ArrayList<>();
        for (String line : loadPhrases(c)) {
            boolean usesStreak = line.contains("{streak}");
            if (slot == 1) {
                if (usesStreak) {
                    pool.add(line);
                }
            } else if (!usesStreak || streak > 0) {
                pool.add(line);
            }
        }
        if (pool.isEmpty()) {
            return "Czas na krótką lekcję słówek!";
        }
        SharedPreferences p = prefs(c);
        String last = p.getString("last_phrase", "");
        Random rnd = new Random();
        String pick = pool.get(rnd.nextInt(pool.size()));
        int tries = 0;
        while (pick.equals(last) && pool.size() > 1 && tries++ < 10) {
            pick = pool.get(rnd.nextInt(pool.size()));
        }
        p.edit().putString("last_phrase", pick).apply();
        return pick.replace("{streak}", Pl.days(Math.max(streak, 1)));
    }

    // ------------------------------------------------------------------
    // Powiadomienie
    // ------------------------------------------------------------------
    static void show(Context c, String title, String text) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Przypomnienia o nauce",
                    NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Codzienne przypomnienia o lekcji słówek");
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CHANNEL)
                : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true);
        nm.notify(1, b.build());
    }
}
