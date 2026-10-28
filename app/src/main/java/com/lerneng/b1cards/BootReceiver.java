package com.lerneng.b1cards;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Po restarcie telefonu lub aktualizacji aplikacji ustawia przypomnienia od nowa. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Reminder.scheduleNext(ctx);
    }
}
