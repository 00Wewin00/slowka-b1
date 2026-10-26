package com.lerneng.b1cards;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Головний екран: партія B1, мої паки, теми B1, чорний список. */
public class MainActivity extends Activity {
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        content.removeAllViews();

        // --- Заголовок ---
        content.addView(Ui.text(this, "Słówka B1", 32, Ui.INK, true));
        TextView sub = Ui.text(this, "Польська → English · словник для технікуму", 14, Ui.MUTED, false);
        content.addView(sub, Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 2, 0, 16));

        // --- Загальний прогрес ---
        int total = db.totalBuiltinWords();
        int learned = db.learnedBuiltinWords();
        content.addView(Ui.text(this, "Вивчено: " + learned + " з " + total + " слів", 14, Ui.INK, false));
        content.addView(progressBar(learned, total), Ui.lp(this, Ui.MATCH, Ui.dp(this, 8), 0, 8, 0, 20));

        // --- Основна партія ---
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20));
        main.setBackground(Ui.ripple(this, Ui.ACCENT, 0, 20));
        main.addView(Ui.text(this, "Партія B1", 22, 0xFFFFFFFF, true));
        int left = db.mainLeft();
        String info = left == 0
                ? "Усі слова вивчено 🎉"
                : "Залишилось " + left + " з " + Db.BATCH_SIZE + " слів у партії";
        main.addView(Ui.text(this, info, 15, 0xFFDBEAFE, false), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 4, 0, 0));
        main.setOnClickListener(v -> openStudy(Db.MAIN));
        content.addView(main, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 0, 0, 24));

        // --- Мої паки ---
        List<Db.Pack> packs = db.packs();
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.text(this, "Мої паки", 20, Ui.INK, true), new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        Button add = Ui.button(this, "+ Новий пак", Ui.ACCENT_SOFT, 0, Ui.ACCENT);
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
                    "Тут будуть твої паки. Натисни «+ Новий пак», встав слова списком — і вчи тільки їх.",
                    14, Ui.MUTED, false), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 6, 0, 0));
        } else {
            content.addView(Ui.text(this, "Довге натискання на пак — додати слова або видалити.",
                    12, Ui.MUTED, false), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 8, 0, 0));
        }

        // --- Теми B1 ---
        content.addView(Ui.text(this, "Теми B1", 20, Ui.INK, true), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 28, 0, 0));
        for (Db.Pack p : packs) {
            if (p.builtin) {
                content.addView(packRow(p), Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));
            }
        }

        // --- Чорний список ---
        Button bl = Ui.button(this, "Чорний список · " + db.blacklistCount(), Ui.GRAY_SOFT, 0, Ui.INK);
        bl.setOnClickListener(v -> startActivity(new Intent(this, BlacklistActivity.class)));
        content.addView(bl, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 28, 0, 0));
    }

    private View progressBar(int value, int total) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackground(Ui.shape(this, Ui.GRAY_SOFT, 0, 4));
        float done = total == 0 ? 0f : (float) value / total;
        View fill = new View(this);
        fill.setBackground(Ui.shape(this, Ui.ACCENT, 0, 4));
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

        String right = p.left == 0 ? "готово ✓" : p.left + " / " + p.total;
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
                .setItems(new String[]{"Додати слова", "Видалити пак"}, (d, which) -> {
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
                .setTitle("Видалити пак «" + p.name + "»?")
                .setMessage("Слова з вбудованої бази залишаться, зникнуть тільки твої власні.")
                .setNegativeButton("Скасувати", null)
                .setPositiveButton("Видалити", (d, w) -> {
                    db.deletePack(p.id);
                    Toast.makeText(this, "Пак видалено", Toast.LENGTH_SHORT).show();
                    build();
                })
                .show();
    }
}
