package com.lerneng.b1cards;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Czarna lista: słowa, które już się nie pojawiają. Tutaj można je przywrócić. */
public class BlacklistActivity extends Activity {
    private Db db;
    private LinearLayout list;
    private TextView info;
    private Button restoreAll;
    private int count;
    private int pending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.setupWindow(this);
        db = Db.get(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 28));
        scroll.addView(root, new ScrollView.LayoutParams(Ui.MATCH, Ui.WRAP));

        root.addView(Ui.header(this, "Czarna lista"));

        info = Ui.text(this, "", 14, Ui.MUTED, false);
        root.addView(info, Ui.lp(this, Ui.WRAP, Ui.WRAP, 8, 12, 8, 8));

        restoreAll = Ui.button(this, "Przywróć wszystkie słowa", Ui.GRAY_SOFT, 0, Ui.INK);
        restoreAll.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Przywrócić wszystkie słowa?")
                .setMessage("Wszystkie opanowane słowa znów pojawią się w partiach i zestawach.")
                .setNegativeButton("Anuluj", null)
                .setPositiveButton("Przywróć", (d, w) -> {
                    db.unblacklistAll();
                    fill();
                })
                .show());
        root.addView(restoreAll, Ui.lp(this, Ui.MATCH, Ui.WRAP, 8, 0, 8, 8));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list, Ui.lp(this, Ui.MATCH, Ui.WRAP, 8, 0, 8, 0));

        setContentView(scroll);
        fill();
    }

    private void fill() {
        list.removeAllViews();
        List<Db.Word> words = db.blacklisted();
        count = words.size();
        pending = 0;
        for (Db.Word w : words) {
            if (!w.tested) {
                pending++;
            }
        }
        updateInfo();
        for (Db.Word w : words) {
            list.addView(row(w), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 6, 0, 0));
        }
    }

    private void updateInfo() {
        if (count == 0) {
            info.setText("Na razie pusto. Słowa, które oznaczysz jako opanowane, pojawią się tutaj.");
        } else {
            info.setText("Słów na czarnej liście: " + count + "\n"
                    + "Czeka na test: " + pending + "\n"
                    + "Zaliczone w teście: " + (count - pending));
        }
        restoreAll.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
    }

    private View row(final Db.Word w) {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        row.setBackground(Ui.shape(this, Ui.BG, Ui.LINE, 12));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(Ui.text(this, w.pl, 16, Ui.INK, true));
        texts.addView(Ui.text(this, w.en, 14, Ui.MUTED, false));
        texts.addView(Ui.text(this, w.tested ? "✓ zaliczone w teście" : "czeka na test", 12,
                w.tested ? Ui.GREEN : Ui.ORANGE, false));
        row.addView(texts, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));

        Button back = Ui.button(this, "Przywróć", Ui.ACCENT_SOFT, 0, Ui.ACCENT);
        back.setTextSize(13);
        back.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        back.setOnClickListener(v -> {
            db.unblacklist(w.id);
            list.removeView(row);
            count--;
            if (!w.tested) {
                pending--;
            }
            updateInfo();
        });
        row.addView(back, new LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP));
        return row;
    }
}
