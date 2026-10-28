package com.lerneng.b1cards;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tworzenie własnego zestawu (albo dodawanie słów do istniejącego).
 * Szybki sposób: wkleić listę „słowo = tłumaczenie”, po jednym w wierszu.
 * Dodatkowo można zaznaczyć słowa z wbudowanej bazy przez wyszukiwarkę.
 */
public class PackEditActivity extends Activity {
    static final String EXTRA_PACK = "pack_id";

    private static final String[] SEPARATORS = {"\t", "=", ";", " - ", " – ", " — ", " : ", " > ", "->"};

    private Db db;
    private long packId = -1;
    private final Set<Long> picked = new LinkedHashSet<>();

    private EditText nameEt;
    private EditText bulkEt;
    private EditText searchEt;
    private LinearLayout results;
    private TextView pickedInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.setupWindow(this);
        db = Db.get(this);
        packId = getIntent().getLongExtra(EXTRA_PACK, -1);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 28));
        scroll.addView(root, new ScrollView.LayoutParams(Ui.MATCH, Ui.WRAP));

        root.addView(Ui.header(this, packId < 0 ? "Nowy zestaw" : "Dodaj słowa do zestawu"));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        root.addView(body, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

        // --- Nazwa ---
        body.addView(Ui.text(this, "Nazwa zestawu", 14, Ui.MUTED, true), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 16, 0, 6));
        nameEt = field("Np. Sprawdzian z techniki", false);
        if (packId >= 0) {
            nameEt.setText(db.packName(packId));
        }
        body.addView(nameEt, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

        // --- Szybkie dodawanie ---
        body.addView(Ui.text(this, "Szybkie dodawanie słów", 14, Ui.MUTED, true), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 20, 0, 2));
        body.addView(Ui.text(this, "Każdy wiersz: słowo = tłumaczenie (działa też ; lub myślnik)", 13, Ui.MUTED, false),
                Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 0, 0, 6));
        bulkEt = field("kot = cat\npies = dog\nśrubokręt = screwdriver", true);
        body.addView(bulkEt, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

        // --- Dodaj z bazy ---
        body.addView(Ui.text(this, "Albo znajdź w bazie słów", 14, Ui.MUTED, true), Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 20, 0, 6));
        searchEt = field("Szukaj po polsku lub angielsku", false);
        searchEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refreshResults();
            }
        });
        body.addView(searchEt, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

        pickedInfo = Ui.text(this, "", 13, Ui.ACCENT, true);
        body.addView(pickedInfo, Ui.lp(this, Ui.WRAP, Ui.WRAP, 0, 8, 0, 0));

        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        body.addView(results, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

        // --- Zapisz ---
        Button save = Ui.button(this, "Zapisz zestaw", Ui.ACCENT, 0, 0xFFFFFFFF);
        save.setOnClickListener(v -> save());
        body.addView(save, Ui.lp(this, Ui.MATCH, Ui.WRAP, 0, 24, 0, 0));

        updatePickedInfo();
        setContentView(scroll);
    }

    private EditText field(String hint, boolean multiline) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(16);
        e.setTextColor(Ui.INK);
        e.setHintTextColor(0xFF9CA3AF);
        e.setBackground(Ui.shape(this, Ui.BG, Ui.LINE, 12));
        e.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        if (multiline) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            e.setMinLines(7);
            e.setGravity(Gravity.TOP | Gravity.START);
        } else {
            e.setInputType(InputType.TYPE_CLASS_TEXT);
            e.setSingleLine(true);
        }
        return e;
    }

    // ------------------------------------------------------------------
    // Wyszukiwanie w bazie
    // ------------------------------------------------------------------
    private void refreshResults() {
        results.removeAllViews();
        List<Db.Word> found = db.search(searchEt.getText().toString());
        for (final Db.Word w : found) {
            CheckBox cb = new CheckBox(this);
            cb.setText(w.pl + "  —  " + w.en);
            cb.setTextSize(16);
            cb.setTextColor(Ui.INK);
            cb.setButtonTintList(ColorStateList.valueOf(Ui.ACCENT));
            cb.setPadding(Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10));
            cb.setChecked(picked.contains(w.id));
            cb.setOnCheckedChangeListener((b, checked) -> {
                if (checked) {
                    picked.add(w.id);
                } else {
                    picked.remove(w.id);
                }
                updatePickedInfo();
            });
            results.addView(cb, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        }
    }

    private void updatePickedInfo() {
        pickedInfo.setText(picked.isEmpty() ? "" : "Wybrano z bazy: " + picked.size());
    }

    // ------------------------------------------------------------------
    // Parsowanie tekstu i zapis
    // ------------------------------------------------------------------
    private List<String[]> parse(String text, int[] bad) {
        List<String[]> out = new ArrayList<>();
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            boolean ok = false;
            for (String sep : SEPARATORS) {
                int i = line.indexOf(sep);
                if (i > 0) {
                    String pl = line.substring(0, i).trim();
                    String en = line.substring(i + sep.length()).trim();
                    if (!pl.isEmpty() && !en.isEmpty()) {
                        out.add(new String[]{pl, en});
                        ok = true;
                    }
                    break;
                }
            }
            if (!ok) {
                bad[0]++;
            }
        }
        return out;
    }

    private void save() {
        String name = nameEt.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Wpisz nazwę zestawu", Toast.LENGTH_SHORT).show();
            return;
        }
        int[] bad = {0};
        List<String[]> pairs = parse(bulkEt.getText().toString(), bad);
        if (pairs.isEmpty() && picked.isEmpty()) {
            Toast.makeText(this, bad[0] > 0
                            ? "Nie rozpoznano wierszy. Pisz tak: słowo = tłumaczenie"
                            : "Dodaj przynajmniej jedno słowo",
                    Toast.LENGTH_LONG).show();
            return;
        }
        db.savePack(packId, name, pairs, picked);
        int count = pairs.size() + picked.size();
        String msg = "Zestaw „" + name + "”: dodano " + Ui.words(count);
        if (bad[0] > 0) {
            msg += " (pominięto wierszy: " + bad[0] + ")";
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        finish();
    }
}
