package com.lerneng.b1cards;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Test: pokazuje polskie słowo, a ty wpisujesz angielskie tłumaczenie.
 * Testowane są wszystkie słowa z czarnej listy, które jeszcze nie przeszły testu.
 * Jeśli cały test jest bezbłędny (100%), te słowa znikają z kolejnych testów.
 */
public class TestActivity extends Activity {
    private Db db;
    private LinearLayout root;

    private List<Db.Word> queue = new ArrayList<>();
    private final List<String[]> mistakes = new ArrayList<>(); // {pl, en, wpisane}
    private int pos;
    private int correct;
    private boolean checked;

    // widoki pytania
    private TextView counter;
    private LinearLayout progressFill;
    private LinearLayout progressRest;
    private LinearLayout card;
    private TextView prompt;
    private EditText answer;
    private TextView feedback;
    private Button action;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.setupWindow(this);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        db = Db.get(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 24));
        scroll.addView(root, new ScrollView.LayoutParams(Ui.MATCH, Ui.WRAP));
        setContentView(scroll);

        begin();
    }

    private void begin() {
        queue = db.pendingTestWords();
        mistakes.clear();
        pos = 0;
        correct = 0;
        if (queue.isEmpty()) {
            showEmpty();
        } else {
            buildQuiz();
            bindQuestion();
        }
    }

    // ------------------------------------------------------------------
    // Brak słów
    // ------------------------------------------------------------------
    private void showEmpty() {
        root.removeAllViews();
        root.addView(Ui.header(this, "Test"));
        TextView t = Ui.text(this, "Nie ma słów do sprawdzenia 🙂", 22, Ui.INK, true);
        t.setGravity(Gravity.CENTER);
        root.addView(t, Ui.lp(this, Ui.MATCH, Ui.WRAP, 16, 80, 16, 8));
        TextView s = Ui.text(this,
                "Oznaczaj opanowane słowa przyciskiem „Na czarną listę”, a potem sprawdź się tutaj.",
                15, Ui.MUTED, false);
        s.setGravity(Gravity.CENTER);
        root.addView(s, Ui.lp(this, Ui.MATCH, Ui.WRAP, 24, 0, 24, 0));
    }

    // ------------------------------------------------------------------
    // Pytania
    // ------------------------------------------------------------------
    private void buildQuiz() {
        root.removeAllViews();

        LinearLayout header = Ui.header(this, "Test");
        counter = Ui.text(this, "", 15, Ui.MUTED, false);
        counter.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);
        header.addView(counter);
        root.addView(header);

        // pasek postępu
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackground(Ui.shape(this, Ui.GRAY_SOFT, 0, 4));
        progressFill = new LinearLayout(this);
        progressFill.setBackground(Ui.shape(this, Ui.ACCENT, 0, 4));
        progressRest = new LinearLayout(this);
        bar.addView(progressFill, new LinearLayout.LayoutParams(0, Ui.MATCH, 0f));
        bar.addView(progressRest, new LinearLayout.LayoutParams(0, Ui.MATCH, 1f));
        root.addView(bar, Ui.lp(this, Ui.MATCH, Ui.dp(this, 8), 8, 12, 8, 0));

        // karta z pytaniem
        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(Ui.dp(this, 20), Ui.dp(this, 28), Ui.dp(this, 20), Ui.dp(this, 28));
        card.setBackground(Ui.shape(this, Ui.BG, Ui.LINE, 24));
        card.setElevation(Ui.dp(this, 4));

        TextView cap = Ui.text(this, "PRZETŁUMACZ NA ANGIELSKI", 12, Ui.MUTED, true);
        cap.setLetterSpacing(0.12f);
        card.addView(cap);

        prompt = Ui.text(this, "", 32, Ui.INK, true);
        prompt.setGravity(Gravity.CENTER);
        card.addView(prompt, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 18, 0, 0));
        root.addView(card, Ui.lp(this, Ui.MATCH, Ui.WRAP, 8, 20, 8, 0));

        // pole odpowiedzi
        answer = new EditText(this);
        answer.setHint("Wpisz po angielsku");
        answer.setTextSize(20);
        answer.setTextColor(Ui.INK);
        answer.setHintTextColor(0xFF9CA3AF);
        answer.setSingleLine(true);
        answer.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        answer.setImeOptions(EditorInfo.IME_ACTION_DONE);
        answer.setBackground(Ui.shape(this, Ui.BG, Ui.LINE, 14));
        answer.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        answer.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_DONE || enter) {
                onPrimary();
                return true;
            }
            return false;
        });
        root.addView(answer, Ui.lp(this, Ui.MATCH, Ui.WRAP, 8, 16, 8, 0));

        // informacja zwrotna
        feedback = Ui.text(this, "", 16, Ui.INK, true);
        feedback.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        feedback.setVisibility(View.GONE);
        root.addView(feedback, Ui.lp(this, Ui.MATCH, Ui.WRAP, 8, 12, 8, 0));

        action = Ui.button(this, "Sprawdź", Ui.ACCENT, 0, 0xFFFFFFFF);
        action.setOnClickListener(v -> onPrimary());
        root.addView(action, Ui.lp(this, Ui.MATCH, Ui.WRAP, 8, 16, 8, 0));
    }

    private void bindQuestion() {
        Db.Word w = queue.get(pos);
        checked = false;
        counter.setText((pos + 1) + " / " + queue.size());
        setProgress(pos, queue.size());

        prompt.setText(w.pl);
        prompt.setTextSize(w.pl.length() <= 14 ? 34 : (w.pl.length() <= 26 ? 28 : 22));
        card.setBackground(Ui.shape(this, Ui.BG, Ui.LINE, 24));

        answer.setText("");
        feedback.setVisibility(View.GONE);
        action.setText("Sprawdź");

        // wjazd nowej karty
        card.setAlpha(0f);
        card.setTranslationX(Ui.dp(this, 48));
        card.animate().alpha(1f).translationX(0f).setDuration(220)
                .setInterpolator(new OvershootInterpolator(0.8f)).start();

        answer.requestFocus();
        answer.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(answer, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 120);
    }

    private void setProgress(int done, int total) {
        float f = total == 0 ? 0f : (float) done / total;
        ((LinearLayout.LayoutParams) progressFill.getLayoutParams()).weight = f;
        ((LinearLayout.LayoutParams) progressRest.getLayoutParams()).weight = 1f - f;
        progressFill.requestLayout();
    }

    private void onPrimary() {
        if (!checked) {
            check();
        } else {
            next();
        }
    }

    private void check() {
        Db.Word w = queue.get(pos);
        String typed = answer.getText().toString().trim();
        boolean ok = Answers.matches(typed, w.en);
        checked = true;

        feedback.setVisibility(View.VISIBLE);
        if (ok) {
            correct++;
            db.practiced(w.id);
            // Poprawne słowo znika z testów od razu — nie trzeba czekać na koniec testu.
            db.markTested(Collections.singletonList(w.id));
            String msg = "✓ Dobrze!";
            List<String> others = Answers.others(typed, w.en);
            if (!others.isEmpty()) {
                msg += "\n" + (others.size() == 1 ? "Drugie tłumaczenie: " : "Inne tłumaczenia: ")
                        + TextUtils.join(", ", others);
            }
            feedback.setText(msg);
            feedback.setTextColor(Ui.GREEN);
            feedback.setBackground(Ui.shape(this, Ui.GREEN_SOFT, Ui.GREEN_LINE, 12));
            card.setBackground(Ui.shape(this, Ui.GREEN_SOFT, Ui.GREEN_LINE, 24));
            card.animate().scaleX(1.04f).scaleY(1.04f).setDuration(110)
                    .withEndAction(() -> card.animate().scaleX(1f).scaleY(1f).setDuration(140).start())
                    .start();
        } else {
            mistakes.add(new String[]{w.pl, w.en, typed});
            feedback.setText("✗ Źle. Poprawna odpowiedź:\n" + w.en);
            feedback.setTextColor(Ui.DANGER);
            feedback.setBackground(Ui.shape(this, Ui.DANGER_SOFT, 0xFFFECACA, 12));
            card.setBackground(Ui.shape(this, Ui.DANGER_SOFT, 0xFFFECACA, 24));
            ObjectAnimator.ofFloat(card, "translationX", 0f, -22f, 22f, -16f, 16f, -8f, 8f, 0f)
                    .setDuration(380).start();
        }
        action.setText(pos == queue.size() - 1 ? "Zobacz wynik" : "Dalej");
    }

    private void next() {
        pos++;
        if (pos >= queue.size()) {
            showResult();
        } else {
            bindQuestion();
        }
    }

    // ------------------------------------------------------------------
    // Wynik
    // ------------------------------------------------------------------
    private void showResult() {
        hideKeyboard();
        root.removeAllViews();
        root.addView(Ui.header(this, "Wynik testu"));

        final int total = queue.size();
        final int pct = total == 0 ? 0 : Math.round(correct * 100f / total);
        final boolean perfect = mistakes.isEmpty();

        final int remaining = db.pendingTestCount();

        TextView emoji = Ui.text(this, perfect ? "🎉" : "💪", 64, Ui.INK, false);
        emoji.setGravity(Gravity.CENTER);
        root.addView(emoji, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 24, 0, 0));
        emoji.setScaleX(0.2f);
        emoji.setScaleY(0.2f);
        emoji.animate().scaleX(1f).scaleY(1f).setDuration(450)
                .setInterpolator(new OvershootInterpolator(2f)).start();

        TextView title = Ui.text(this, perfect ? "100% — znakomicie!" : pct + "% — prawie!",
                28, perfect ? Ui.GREEN : Ui.INK, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 8, 0, 0));

        TextView sub = Ui.text(this, "Poprawne odpowiedzi: " + correct + " z " + total, 16, Ui.MUTED, false);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 4, 0, 0));

        String info = perfect
                ? "Wszystkie słowa zaliczone — znikają z kolejnych testów."
                : "Poprawne słowa już zniknęły z testów. Błędne zostają i wrócą w kolejnym teście "
                + "(zostało do sprawdzenia: " + remaining + "). Poniżej zobaczysz, co poszło nie tak.";
        TextView infoTv = Ui.text(this, info, 14, Ui.MUTED, false);
        infoTv.setGravity(Gravity.CENTER);
        root.addView(infoTv, Ui.lp(this, Ui.MATCH, Ui.WRAP, 20, 14, 20, 8));

        if (!perfect) {
            root.addView(Ui.text(this, "Do poprawy (" + mistakes.size() + ")", 18, Ui.INK, true),
                    Ui.lp(this, Ui.WRAP, Ui.WRAP, 8, 12, 8, 4));
            for (String[] m : mistakes) {
                root.addView(mistakeRow(m), Ui.lp(this, Ui.MATCH, Ui.WRAP, 8, 6, 8, 0));
            }
        }

        if (!perfect) {
            Button again = Ui.button(this, "Powtórz błędne", Ui.ACCENT, 0, 0xFFFFFFFF);
            again.setOnClickListener(v -> begin());
            root.addView(again, Ui.lp(this, Ui.MATCH, Ui.WRAP, 8, 20, 8, 0));
        }
        Button close = Ui.button(this, "Wróć do menu", perfect ? Ui.ACCENT : Ui.GRAY_SOFT, 0,
                perfect ? 0xFFFFFFFF : Ui.INK);
        close.setOnClickListener(v -> finish());
        root.addView(close, Ui.lp(this, Ui.MATCH, Ui.WRAP, 8, perfect ? 24 : 10, 8, 0));
    }

    private View mistakeRow(String[] m) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        row.setBackground(Ui.shape(this, Ui.BG, Ui.LINE, 12));
        row.addView(Ui.text(this, m[0], 17, Ui.INK, true));
        row.addView(Ui.text(this, "Poprawnie: " + m[1], 15, Ui.GREEN, true),
                Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 2, 0, 0));
        String typed = m[2].isEmpty() ? "(brak odpowiedzi)" : m[2];
        row.addView(Ui.text(this, "Twoja odpowiedź: " + typed, 14, Ui.DANGER, false),
                Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 2, 0, 0));
        return row;
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && root != null) {
            imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
        }
    }
}
