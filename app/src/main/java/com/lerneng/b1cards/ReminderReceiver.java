package com.lerneng.b1cards;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Odpala się o ustawionej godzinie: pokazuje powiadomienie, jeśli cel dnia nie jest zaliczony. */
public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        int slot = intent.getIntExtra("slot", 0);
        try {
            if (Reminder.enabled(ctx)) {
                Db db = Db.get(ctx);
                boolean done = db.practicedToday() >= Db.DAILY_GOAL;
                int streak = db.streak();
                // slot 1 („ostatnia szansa”) ma sens tylko wtedy, gdy jest seria do uratowania
                if (!done && (slot == 0 || streak > 0)) {
                    String title = streak > 0 ? "Słówka B1 🔥" : "Słówka B1";
                    Reminder.show(ctx, title, Reminder.pickPhrase(ctx, streak, slot));
                }
            }
        } finally {
            Reminder.scheduleNext(ctx);
        }
    }
}
