package com.lerneng.b1cards;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Екран вивчення: картка з польським словом, тап — перевертає (English),
 * свайп / кнопки — наступна й попередня картка, кнопка — у чорний список.
 */
public class StudyActivity extends Activity {
    static final String EXTRA_PACK = "pack_id";

    private Db db;
    private long packId;
    private List<Db.Word> words = new ArrayList<>();
    private int index = 0;
    private boolean showEn = false;
    private boolean busy = false;

    private Db.Word lastWord;
    private int lastIndex;

    private TextView counter;
    private TextView label;
    private TextView wordView;
    private TextView hint;
    private FrameLayout card;
    private Button prev;
    private Button next;
    private Button black;
    private Button undo;
    private Button shuffle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.setupWindow(this);
        db = Db.get(this);
        packId = getIntent().getLongExtra(EXTRA_PACK, Db.MAIN);

        buildUi();
        loadWords();

        // Продовжуємо з того місця, де зупинились
        SharedPreferences prefs = getSharedPreferences("state", MODE_PRIVATE);
        int saved = prefs.getInt("idx_" + packId, 0);
        index = (saved >= 0 && saved < words.size()) ? saved : 0;
        bind();
    }

    @Override
    protected void onPause() {
        super.onPause();
        getSharedPreferences("state", MODE_PRIVATE).edit().putInt("idx_" + packId, index).apply();
    }

    private void loadWords() {
        words = (packId == Db.MAIN) ? db.mainBatch() : db.packWords(packId);
    }

    // ------------------------------------------------------------------
    // Інтерфейс
    // ------------------------------------------------------------------
    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 16));

        String title = (packId == Db.MAIN) ? "Партія B1" : db.packName(packId);
        LinearLayout header = Ui.header(this, title);
        counter = Ui.text(this, "", 15, Ui.MUTED, false);
        counter.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);
        header.addView(counter);
        root.addView(header);

        // --- Картка ---
        card = new FrameLayout(this);
        card.setElevation(Ui.dp(this, 6));
        card.setCameraDistance(8000f * getResources().getDisplayMetrics().density);

        label = Ui.text(this, "", 12, Ui.MUTED, true);
        label.setLetterSpacing(0.15f);
        FrameLayout.LayoutParams labelLp = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP,
                Gravity.TOP | Gravity.START);
        labelLp.setMargins(Ui.dp(this, 22), Ui.dp(this, 20), 0, 0);
        card.addView(label, labelLp);

        wordView = Ui.text(this, "", 32, Ui.INK, true);
        wordView.setGravity(Gravity.CENTER);
        wordView.setPadding(Ui.dp(this, 24), Ui.dp(this, 48), Ui.dp(this, 24), Ui.dp(this, 48));
        card.addView(wordView, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH, Gravity.CENTER));

        hint = Ui.text(this, "", 13, Ui.MUTED, false);
        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hintLp.setMargins(0, 0, 0, Ui.dp(this, 18));
        card.addView(hint, hintLp);

        final GestureDetector detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                flip();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (Math.abs(vx) > Math.abs(vy) && Math.abs(vx) > 600) {
                    go(vx < 0 ? 1 : -1);
                    return true;
                }
                return false;
            }
        });
        card.setOnTouchListener((v, ev) -> detector.onTouchEvent(ev));

        LinearLayout.LayoutParams cardLp = Ui.lp(this, Ui.MATCH, 0, 8, 12, 8, 16);
        cardLp.weight = 1f;
        root.addView(card, cardLp);

        // --- Кнопки ---
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        prev = Ui.button(this, "← Назад", Ui.GRAY_SOFT, 0, Ui.INK);
        next = Ui.button(this, "Далі →", Ui.ACCENT, 0, 0xFFFFFFFF);
        prev.setOnClickListener(v -> go(-1));
        next.setOnClickListener(v -> go(1));
        nav.addView(prev, Ui.lp(this, 0, Ui.WRAP, 0, 0, 6, 0));
        ((LinearLayout.LayoutParams) prev.getLayoutParams()).weight = 1f;
        nav.addView(next, Ui.lp(this, 0, Ui.WRAP, 6, 0, 0, 0));
        ((LinearLayout.LayoutParams) next.getLayoutParams()).weight = 1f;
        root.addView(nav, Ui.lp(this, Ui.MATCH, Ui.WRAP, 8, 0, 8, 0));

        black = Ui.button(this, "У чорний список (вивчено)", Ui.DANGER_SOFT, 0, Ui.DANGER);
        black.setOnClickListener(v -> blacklistCurrent());
        root.addView(black, Ui.lp(this, Ui.MATCH, Ui.WRAP, 8, 10, 8, 0));

        LinearLayout extra = new LinearLayout(this);
        extra.setOrientation(LinearLayout.HORIZONTAL);
        undo = Ui.button(this, "Скасувати останнє", Ui.BG, 0, Ui.MUTED);
        undo.setTextSize(14);
        undo.setOnClickListener(v -> undoLast());
        shuffle = Ui.button(this, "Перемішати", Ui.BG, 0, Ui.MUTED);
        shuffle.setTextSize(14);
        shuffle.setOnClickListener(v -> shuffleWords());
        extra.addView(undo, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        extra.addView(shuffle, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        root.addView(extra, Ui.lp(this, Ui.MATCH, Ui.WRAP, 8, 4, 8, 0));

        setContentView(root);
    }

    // ------------------------------------------------------------------
    // Показ поточної картки
    // ------------------------------------------------------------------
    private void bind() {
        boolean empty = words.isEmpty();
        prev.setEnabled(!empty);
        next.setEnabled(!empty);
        black.setEnabled(!empty);
        shuffle.setEnabled(!empty);
        float a = empty ? 0.4f : 1f;
        prev.setAlpha(a);
        next.setAlpha(a);
        black.setAlpha(a);
        shuffle.setAlpha(a);
        undo.setVisibility(lastWord != null ? View.VISIBLE : View.INVISIBLE);

        if (empty) {
            counter.setText("");
            label.setText("");
            wordView.setTextSize(22);
            wordView.setTextColor(Ui.INK);
            wordView.setText(packId == Db.MAIN
                    ? "Усі слова вивчено — вони в чорному списку 🎉"
                    : "Усі слова цього паку вже в чорному списку 🎉");
            hint.setText("Чорний список можна відкрити на головному екрані");
            card.setBackground(Ui.shape(this, Ui.BG, Ui.LINE, 24));
            return;
        }

        Db.Word w = words.get(index);
        String text = showEn ? w.en : w.pl;
        wordView.setText(text);
        wordView.setTextSize(sizeFor(text));
        wordView.setTextColor(showEn ? Ui.ACCENT : Ui.INK);
        label.setText(showEn ? "ENGLISH" : "POLSKI");
        hint.setText(showEn ? "Торкнись, щоб повернути" : "Торкнись, щоб перевернути");
        counter.setText((index + 1) + " / " + words.size());
        card.setBackground(Ui.shape(this,
                showEn ? Ui.ACCENT_SOFT : Ui.BG,
                showEn ? Ui.ACCENT_LINE : Ui.LINE, 24));
    }

    private static float sizeFor(String s) {
        int n = s.length();
        if (n <= 12) return 36;
        if (n <= 22) return 30;
        if (n <= 40) return 24;
        return 20;
    }

    // ------------------------------------------------------------------
    // Дії
    // ------------------------------------------------------------------
    private void flip() {
        if (busy || words.isEmpty()) {
            return;
        }
        busy = true;
        card.animate().rotationY(90f).setDuration(130).withEndAction(() -> {
            showEn = !showEn;
            bind();
            card.setRotationY(-90f);
            card.animate().rotationY(0f).setDuration(130).withEndAction(() -> busy = false).start();
        }).start();
    }

    private void go(final int dir) {
        if (busy || words.size() < 2) {
            return;
        }
        busy = true;
        final float shift = Math.max(card.getWidth(), 1) * 0.6f;
        card.animate().translationX(-dir * shift).alpha(0f).setDuration(110).withEndAction(() -> {
            index = (index + dir + words.size()) % words.size();
            showEn = false;
            bind();
            card.setTranslationX(dir * shift);
            card.animate().translationX(0f).alpha(1f).setDuration(110).withEndAction(() -> busy = false).start();
        }).start();
    }

    private void blacklistCurrent() {
        if (busy || words.isEmpty()) {
            return;
        }
        Db.Word w = words.get(index);
        db.blacklist(w.id);
        lastWord = w;
        lastIndex = index;
        words.remove(index);

        if (words.isEmpty() && packId == Db.MAIN) {
            // Партія закінчилась — автоматично беремо наступні 50 слів
            words = db.mainBatch();
            index = 0;
            if (!words.isEmpty()) {
                lastWord = null;
                Toast.makeText(this, "Нова партія: " + words.size() + " слів!", Toast.LENGTH_LONG).show();
            }
        } else if (index >= words.size()) {
            index = 0;
        }
        showEn = false;
        bind();
    }

    private void undoLast() {
        if (lastWord == null) {
            return;
        }
        db.unblacklist(lastWord.id);
        int at = Math.min(lastIndex, words.size());
        words.add(at, lastWord);
        index = at;
        showEn = false;
        lastWord = null;
        bind();
    }

    private void shuffleWords() {
        if (words.size() < 2) {
            return;
        }
        Collections.shuffle(words);
        index = 0;
        showEn = false;
        bind();
        Toast.makeText(this, "Перемішано", Toast.LENGTH_SHORT).show();
    }
}
