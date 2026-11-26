package com.nrcracker.air;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pantalla principal de NR AirCrack, con diseno oscuro moderno.
 * El motor (aircrack-ng en CrackerService) no se toca: la UI solo pinta
 * el estado cada 500 ms y no afecta a la velocidad de descifrado.
 */
public class MainActivity extends Activity {
    private static final int REQ_HASH = 11;
    private static final int REQ_WORD = 12;
    private static final int REQ_SAVE_22000 = 13;

    private SharedPreferences prefs;
    private String lang = "es";
    private boolean dark = true;

    private int BG, CARD, CARD2, STROKE, TEXT, MUTED, LOGTEXT, PILLBG, PILLTEXT, WARN;
    private int CYAN, VIOLET, GREEN, RED;
    private int SB_OK, SB_ERR, SB_GREEN, SB_CYAN, SB_GRAY, SB_VIOLET;
    private int RESULT_BG, RESULT_STROKE, RESULT_TEXT;

    private TextView pcapNameView;
    private TextView wordNameView;
    private TextView statusPill;
    private TextView elapsedView;
    private TextView speedView;
    private ProgressBar progressBar;
    private TextView progressTextView;
    private LinearLayout resultCard;
    private TextView resultView;
    private TextView logView;
    private EditText ssidInput;
    private TextView ssidDetected;
    private Button startBtn;
    private Button stopBtn;

    private String pickedHashUri;
    private String pickedWordUri;
    private String pickedHashName = "(ninguno)";
    private String pickedWordName = "(ninguna)";

    private final List<String> logHistory = new ArrayList<String>();
    private String pending22000;
    private String pending22000Name = "handshake.22000";

    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("nr_air", MODE_PRIVATE);
        lang = prefs.getString("lang", "es");
        dark = prefs.getBoolean("dark", true);
        applyTheme();
        buildUi();
        restorePicks();
        requestNotifPermission();
        CrackerService.listener = new CrackerService.Listener() {
            @Override
            public void onState(final String status, final double speed, final long tested,
                                final long total, final long elapsedMs, final String result) {
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        render(status, speed, tested, total, elapsedMs, result);
                    }
                });
            }
        };
        if (getIntent() != null && getIntent().getBooleanExtra("selftest", false)) {
            startSelftest();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        CrackerService.emitCurrent();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra("selftest", false)) {
            startSelftest();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CrackerService.listener = null;
    }

    private void startSelftest() {
        Intent i = new Intent(this, CrackerService.class);
        i.setAction(CrackerService.ACTION_SELFTEST);
        startForegroundService(i);
        startBtn.setEnabled(false);
        startBtn.setAlpha(0.45f);
        stopBtn.setEnabled(true);
        stopBtn.setAlpha(1f);
    }

    // ------------------------------------------------------------- idioma y tema

    private String t(String es, String en) {
        return "en".equals(lang) ? en : es;
    }

    private void applyTheme() {
        if (dark) {
            BG = 0xFF000000;
            CARD = 0xFF070707;
            CARD2 = 0xFF0B0B0B;
            STROKE = 0xFF232323;
            TEXT = 0xFFF5F5F5;
            MUTED = 0xFF9CA3AF;
            LOGTEXT = 0xFFB3BAC7;
            PILLBG = 0xFF14142B;
            PILLTEXT = 0xFFA78BFA;
            WARN = 0xFFFBBF24;
            CYAN = 0xFF22D3EE;
            VIOLET = 0xFFA78BFA;
            GREEN = 0xFF34D399;
            RED = 0xFFF87171;
            SB_OK = 0xFF1E1B4B;
            SB_ERR = 0xFF2A0F14;
            SB_GREEN = 0xFF052E1A;
            SB_CYAN = 0xFF082F49;
            SB_GRAY = 0xFF111827;
            SB_VIOLET = 0xFF14142B;
            RESULT_BG = 0xFF052E1A;
            RESULT_STROKE = 0xFF10B981;
            RESULT_TEXT = 0xFF6EE7B7;
        } else {
            BG = 0xFFF8FAFC;
            CARD = 0xFFFFFFFF;
            CARD2 = 0xFFF1F5F9;
            STROKE = 0xFFCBD5E1;
            TEXT = 0xFF0F172A;
            MUTED = 0xFF64748B;
            LOGTEXT = 0xFF334155;
            PILLBG = 0xFFEDE9FE;
            PILLTEXT = 0xFF6D28D9;
            WARN = 0xFFB45309;
            CYAN = 0xFF0891B2;
            VIOLET = 0xFF7C3AED;
            GREEN = 0xFF059669;
            RED = 0xFFDC2626;
            SB_OK = 0xFFEDE9FE;
            SB_ERR = 0xFFFEE2E2;
            SB_GREEN = 0xFFDCFCE7;
            SB_CYAN = 0xFFE0F2FE;
            SB_GRAY = 0xFFF1F5F9;
            SB_VIOLET = 0xFFEDE9FE;
            RESULT_BG = 0xFFDCFCE7;
            RESULT_STROKE = 0xFF059669;
            RESULT_TEXT = 0xFF065F46;
        }
        Window w = getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);
    }

    private void toggleLang() {
        lang = "en".equals(lang) ? "es" : "en";
        prefs.edit().putString("lang", lang).apply();
        rebuildUi();
    }

    private void toggleTheme() {
        dark = !dark;
        prefs.edit().putBoolean("dark", dark).apply();
        rebuildUi();
    }

    private void rebuildUi() {
        if (ssidInput != null) {
            prefs.edit().putString("ssid", ssidInput.getText().toString().trim()).apply();
        }
        applyTheme();
        buildUi();
        restorePicks();
        renderLog();
        CrackerService.emitCurrent();
    }

    private void restorePicks() {
        pickedHashUri = prefs.getString("hash_uri", null);
        pickedWordUri = prefs.getString("word_uri", null);
        pickedHashName = prefs.getString("hash_name", t("(ninguno)", "(none)"));
        pickedWordName = prefs.getString("word_name", t("(ninguna)", "(none)"));
        if (pcapNameView != null) {
            pcapNameView.setText(pickedHashName);
        }
        if (wordNameView != null) {
            wordNameView.setText(pickedWordName);
        }
        String s = prefs.getString("ssid", "");
        if (ssidInput != null && !s.isEmpty() && ssidInput.getText().length() == 0) {
            ssidInput.setText(s);
        }
    }

    private void renderLog() {
        if (logView == null) {
            return;
        }
        int start = Math.max(0, logHistory.size() - 12);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < logHistory.size(); i++) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(logHistory.get(i));
        }
        logView.setText(sb.toString());
    }

    private String displayStatus(String s) {
        if (s == null || !"en".equals(lang)) {
            return s;
        }
        if (s.startsWith("Preparando aircrack-ng")) return "Preparing aircrack-ng...";
        if (s.startsWith("Preparando")) return "Preparing...";
        if (s.startsWith("Copiando")) return "Copying files...";
        if (s.startsWith("Iniciando")) return "Starting aircrack-ng...";
        if (s.startsWith("Reanudando desde")) return s.replace("Reanudando desde", "Resuming from");
        if (s.startsWith("Reanudando")) return "Resuming aircrack-ng...";
        if (s.startsWith("Crackeando")) return "Cracking...";
        if (s.startsWith("Contrasena")) return "Password found";
        if (s.startsWith("Terminado sin resultado")) return "Finished, no match";
        if (s.startsWith("Detenido")) return "Stopped";
        if (s.startsWith("Inactivo")) return "Idle";
        return s;
    }

    private Button headerBtn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(10);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setTextColor(0xFFFFFFFF);
        b.setBackground(rounded(0x33FFFFFF, 0x66FFFFFF, 10));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(9), dp(4), dp(9), dp(4));
        b.setStateListAnimator(null);
        return b;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ------------------------------------------------------------- diseno

    private Drawable rounded(int color, int stroke, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        if (stroke != 0) {
            g.setStroke(dp(1), stroke);
        }
        return g;
    }

    private Drawable gradient(int[] colors, int radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private Drawable ghost() {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, rounded(STROKE, 0, 12));
        s.addState(new int[]{}, rounded(CARD2, 0, 12));
        return s;
    }

    private Drawable primary() {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed},
                gradient(new int[]{0xFF0891B2, 0xFF7C3AED}, 16));
        s.addState(new int[]{},
                gradient(new int[]{0xFF06B6D4, 0xFF8B5CF6}, 16));
        return s;
    }

    private Drawable progressDrawable() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(STROKE);
        bg.setCornerRadius(dp(5));
        GradientDrawable fg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{CYAN, VIOLET});
        fg.setCornerRadius(dp(5));
        ClipDrawable clip = new ClipDrawable(fg, Gravity.LEFT, ClipDrawable.HORIZONTAL);
        LayerDrawable ld = new LayerDrawable(new Drawable[]{bg, clip});
        ld.setId(0, android.R.id.background);
        ld.setId(1, android.R.id.progress);
        return ld;
    }

    private TextView sectionLabel(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(9);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setLetterSpacing(0.14f);
        t.setTextColor(MUTED);
        t.setPadding(0, dp(12), 0, dp(6));
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        c.setBackground(rounded(CARD, STROKE, 20));
        c.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        c.setLayoutParams(lp);
        return c;
    }

    private LinearLayout accentCard(int bg, int stroke) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        c.setBackground(rounded(bg, stroke, 20));
        c.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        c.setLayoutParams(lp);
        return c;
    }

    private Button actionButton(String text, Drawable bg, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setTextColor(color);
        b.setBackground(bg);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(14), 0, dp(14), 0);
        b.setStateListAnimator(null);
        b.setLayoutParams(new LinearLayout.LayoutParams(0, dp(52), 1));
        return b;
    }

    private LinearLayout fileRow(String tag, TextView name, Button btn) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(9), 0, dp(9));
        TextView t = new TextView(this);
        t.setText(tag);
        t.setTextSize(9);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setLetterSpacing(0.1f);
        t.setTextColor(PILLTEXT);
        t.setBackground(rounded(PILLBG, 0, 8));
        t.setPadding(dp(8), dp(4), dp(8), dp(4));
        row.addView(t);
        name.setTextSize(12);
        name.setTextColor(TEXT);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        name.setPadding(dp(10), 0, dp(8), 0);
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        btn.setText(t("Elegir", "Pick"));
        btn.setAllCaps(false);
        btn.setTextSize(11);
        btn.setTextColor(VIOLET);
        btn.setBackground(ghost());
        btn.setMinHeight(0);
        btn.setMinimumHeight(0);
        btn.setPadding(dp(12), dp(6), dp(12), dp(6));
        btn.setStateListAnimator(null);
        row.addView(btn);
        return row;
    }

    private Button miniButton(String text, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(10);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setTextColor(color);
        b.setBackground(rounded(CARD2, STROKE, 9));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(10), dp(5), dp(10), dp(5));
        b.setStateListAnimator(null);
        return b;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(root);

        // ----- Cabecera con logo
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(18), dp(18), dp(18));
        header.setBackground(gradient(new int[]{0xFF0EA5E9, 0xFF7C3AED}, 24));
        header.setElevation(dp(8));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher_foreground);
        logo.setLayoutParams(new LinearLayout.LayoutParams(dp(58), dp(58)));
        header.addView(logo);
        LinearLayout headText = new LinearLayout(this);
        headText.setOrientation(LinearLayout.VERTICAL);
        headText.setPadding(dp(14), 0, dp(8), 0);
        TextView title = new TextView(this);
        title.setText("NR AirCrack");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(22);
        title.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        title.setLetterSpacing(0.01f);
        TextView subtitle = new TextView(this);
        subtitle.setText(t("WPA / WPA2 · auditoría con aircrack-ng", "WPA / WPA2 · aircrack-ng audit"));
        subtitle.setTextColor(0xE6FFFFFF);
        subtitle.setTextSize(12);
        headText.addView(title);
        headText.addView(subtitle);
        header.addView(headText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        Button langBtn = headerBtn("en".equals(lang) ? "EN" : "ES");
        langBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleLang();
            }
        });
        Button themeBtn = headerBtn(dark ? t("Claro", "Light") : t("Oscuro", "Dark"));
        themeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTheme();
            }
        });
        tools.addView(langBtn);
        tools.addView(themeBtn);
        header.addView(tools);
        root.addView(header);

        // ----- Tarjeta de entrada
        LinearLayout inputCard = card();
        inputCard.addView(sectionLabel(t("ENTRADA", "INPUT")));
        pcapNameView = new TextView(this);
        pcapNameView.setText(pickedHashName);
        Button pickHash = new Button(this);
        final Button pickHashRef = pickHash;
        pickHash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPicker(REQ_HASH);
            }
        });
        inputCard.addView(fileRow("PCAP", pcapNameView, pickHashRef));

        wordNameView = new TextView(this);
        wordNameView.setText(pickedWordName);
        Button pickWord = new Button(this);
        final Button pickWordRef = pickWord;
        pickWord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPicker(REQ_WORD);
            }
        });
        inputCard.addView(fileRow("WORDLIST", wordNameView, pickWordRef));

        View divider = new View(this);
        divider.setBackgroundColor(STROKE);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dlp.topMargin = dp(10);
        inputCard.addView(divider, dlp);

        inputCard.addView(sectionLabel(t("SSID · OPCIONAL", "SSID · OPTIONAL")));
        ssidInput = new EditText(this);
        ssidInput.setHint(t("Solo si el pcap tiene varias redes", "Only if the pcap has several networks"));
        ssidInput.setHintTextColor(MUTED);
        ssidInput.setTextColor(TEXT);
        ssidInput.setTextSize(13);
        ssidInput.setSingleLine(true);
        ssidInput.setBackground(rounded(CARD2, STROKE, 12));
        ssidInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        inputCard.addView(ssidInput);
        ssidDetected = new TextView(this);
        ssidDetected.setText(t("SSID: se detectara al elegir el pcap", "SSID: detected when you pick the pcap"));
        ssidDetected.setTextSize(10);
        ssidDetected.setTextColor(MUTED);
        ssidDetected.setPadding(0, dp(8), 0, 0);
        inputCard.addView(ssidDetected);

        Button convert = new Button(this);
        convert.setText(t("Convertir pcap  →  .22000", "Convert pcap  →  .22000"));
        convert.setAllCaps(false);
        convert.setTextSize(13);
        convert.setTextColor(CYAN);
        convert.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        convert.setBackground(rounded(CARD2, 0xFF0EA5E9, 12));
        convert.setStateListAnimator(null);
        convert.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(12);
        convert.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                convertTo22000();
            }
        });
        inputCard.addView(convert, clp);
        root.addView(inputCard);

        // ----- Acciones
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(16), 0, dp(2));
        startBtn = actionButton(t("Iniciar", "Start"), primary(), 0xFFFFFFFF);
        stopBtn = actionButton(t("Detener", "Stop"), rounded(CARD2, STROKE, 16), TEXT);
        stopBtn.setEnabled(false);
        stopBtn.setAlpha(0.45f);
        actions.addView(startBtn);
        View gap = new View(this);
        actions.addView(gap, new LinearLayout.LayoutParams(dp(10), dp(1)));
        actions.addView(stopBtn);
        root.addView(actions);
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCrack();
            }
        });
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopCrack();
            }
        });

        if (Build.VERSION.SDK_INT >= 23) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Button stable = miniButton(t("Evitar cierre en segundo plano",
                        "Prevent background kill"), WARN);
                stable.setTextSize(11);
                stable.setPadding(dp(12), dp(8), dp(12), dp(8));
                stable.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        requestBatteryExemption();
                    }
                });
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                slp.topMargin = dp(10);
                root.addView(stable, slp);
            }
        }

        // ----- Tarjeta de estado
        LinearLayout statusCard = card();
        LinearLayout statusTop = new LinearLayout(this);
        statusTop.setOrientation(LinearLayout.HORIZONTAL);
        statusTop.setGravity(Gravity.CENTER_VERTICAL);
        statusPill = new TextView(this);
        statusPill.setText(t("Listo", "Ready"));
        statusPill.setTextSize(10);
        statusPill.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        statusPill.setTextColor(PILLTEXT);
        statusPill.setBackground(rounded(PILLBG, 0, 99));
        statusPill.setPadding(dp(10), dp(5), dp(10), dp(5));
        statusTop.addView(statusPill);
        elapsedView = new TextView(this);
        elapsedView.setText("00:00");
        elapsedView.setTextSize(11);
        elapsedView.setTextColor(MUTED);
        elapsedView.setGravity(Gravity.END);
        statusTop.addView(elapsedView, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        statusCard.addView(statusTop);

        LinearLayout speedRow = new LinearLayout(this);
        speedRow.setOrientation(LinearLayout.HORIZONTAL);
        speedRow.setGravity(Gravity.BOTTOM);
        speedRow.setPadding(0, dp(10), 0, dp(4));
        speedView = new TextView(this);
        speedView.setText("0");
        speedView.setTextSize(34);
        speedView.setTextColor(CYAN);
        speedView.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        speedRow.addView(speedView);
        TextView unit = new TextView(this);
        unit.setText(" keys/s");
        unit.setTextSize(12);
        unit.setTextColor(MUTED);
        unit.setPadding(0, 0, 0, dp(5));
        speedRow.addView(unit);
        statusCard.addView(speedRow);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(1000);
        progressBar.setProgress(0);
        progressBar.setProgressDrawable(progressDrawable());
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        plp.topMargin = dp(6);
        statusCard.addView(progressBar, plp);

        progressTextView = new TextView(this);
        progressTextView.setText("0 / 0");
        progressTextView.setTextSize(11);
        progressTextView.setTextColor(MUTED);
        progressTextView.setPadding(0, dp(8), 0, 0);
        statusCard.addView(progressTextView);
        root.addView(statusCard);

        // ----- Tarjeta de resultado
        resultCard = accentCard(RESULT_BG, RESULT_STROKE);
        TextView resultLabel = new TextView(this);
        resultLabel.setText(t("CONTRASEÑA ENCONTRADA", "PASSWORD FOUND"));
        resultLabel.setTextSize(9);
        resultLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        resultLabel.setLetterSpacing(0.14f);
        resultLabel.setTextColor(GREEN);
        resultCard.addView(resultLabel);
        resultView = new TextView(this);
        resultView.setTextSize(22);
        resultView.setTextColor(RESULT_TEXT);
        resultView.setTypeface(Typeface.create("monospace", Typeface.BOLD));
        resultView.setTextIsSelectable(true);
        resultView.setPadding(0, dp(8), 0, dp(10));
        resultCard.addView(resultView);
        Button copy = new Button(this);
        copy.setText(t("Copiar contraseña", "Copy password"));
        copy.setAllCaps(false);
        copy.setTextSize(11);
        copy.setTextColor(RESULT_TEXT);
        copy.setBackground(rounded(RESULT_BG, RESULT_STROKE, 10));
        copy.setMinHeight(0);
        copy.setMinimumHeight(0);
        copy.setPadding(dp(12), dp(6), dp(12), dp(6));
        copy.setStateListAnimator(null);
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyResult();
            }
        });
        resultCard.addView(copy);
        resultCard.setVisibility(View.GONE);
        root.addView(resultCard);

        // ----- Tarjeta de registro
        LinearLayout logCard = card();
        LinearLayout logTop = new LinearLayout(this);
        logTop.setOrientation(LinearLayout.HORIZONTAL);
        logTop.setGravity(Gravity.CENTER_VERTICAL);
        logTop.addView(sectionLabel(t("REGISTRO", "LOG")), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button copyLog = miniButton(t("Copiar log", "Copy log"), GREEN);
        copyLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyLog();
            }
        });
        Button clearLog = miniButton(t("Borrar log", "Clear log"), RED);
        clearLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearLog();
            }
        });
        LinearLayout.LayoutParams mbp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mbp.leftMargin = dp(8);
        logTop.addView(copyLog, mbp);
        logTop.addView(clearLog, mbp);
        logCard.addView(logTop);

        logView = new TextView(this);
        logView.setTextSize(10);
        logView.setTextColor(LOGTEXT);
        logView.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
        logView.setPadding(dp(2), dp(6), dp(2), dp(4));
        logView.setMaxLines(12);
        logView.setVerticalScrollBarEnabled(true);
        logView.setTextIsSelectable(true);
        logView.setMovementMethod(new ScrollingMovementMethod());
        logCard.addView(logView);
        root.addView(logCard);

        TextView footer = new TextView(this);
        footer.setText(t("aircrack-ng · 8 hilos · pcap directo",
                "aircrack-ng · 8 threads · direct pcap"));
        footer.setTextSize(10);
        footer.setTextColor(MUTED);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(16), 0, 0);
        root.addView(footer);

        setContentView(scroll);
    }

    // ------------------------------------------------------------- logica

    private void requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
        }
    }

    private void openPicker(int req) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, req);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Throwable ignored) {
        }
        String name = uri.getLastPathSegment();
        if (name == null || name.isEmpty()) {
            name = uri.toString();
        }
        if (req == REQ_HASH) {
            pickedHashUri = uri.toString();
            pickedHashName = name;
            pcapNameView.setText(name);
            log(t("pcap elegido: ", "pcap selected: ") + name);
            detectSsid(uri);
        } else if (req == REQ_WORD) {
            pickedWordUri = uri.toString();
            pickedWordName = name;
            wordNameView.setText(name);
            log(t("wordlist elegida: ", "wordlist selected: ") + name);
        } else if (req == REQ_SAVE_22000) {
            save22000(uri);
            return;
        }
        prefs.edit()
                .putString("hash_uri", pickedHashUri)
                .putString("hash_name", pickedHashName)
                .putString("word_uri", pickedWordUri)
                .putString("word_name", pickedWordName)
                .apply();
        if (pickedHashUri != null && pickedWordUri != null) {
            statusPill.setText(t("Listo", "Ready"));
            statusPill.setTextColor(PILLTEXT);
            statusPill.setBackground(rounded(PILLBG, 0, 99));
        }
        Log.i("NRAirCrack", "manual pcap=" + pickedHashUri + " word=" + pickedWordUri);
    }

    private void detectSsid(final Uri uri) {
        ssidDetected.setText(t("SSID: analizando pcap...", "SSID: analyzing pcap..."));
        ssidDetected.setTextColor(MUTED);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File tmp = new File(getCacheDir(), "detect.pcap");
                    InputStream in = getContentResolver().openInputStream(uri);
                    if (in == null) {
                        throw new Exception(t("no se pudo abrir el pcap", "could not open the pcap"));
                    }
                    OutputStream out = new FileOutputStream(tmp);
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                    out.close();
                    in.close();
                    final PcapConverter.Result r = PcapConverter.convert(tmp, "");
                    tmp.delete();
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            if (r.firstSsid != null && !r.firstSsid.isEmpty()) {
                                ssidInput.setText(r.firstSsid);
                                ssidDetected.setText(t("SSID detectado: ", "SSID detected: ") + r.firstSsid
                                        + t(" · handshake: ", " · handshake: ") + r.handshakes);
                                ssidDetected.setTextColor(VIOLET);
                            } else if (r.handshakes > 0) {
                                ssidDetected.setText(t("Handshake encontrado, SSID no; escribelo manualmente",
                                        "Handshake found, no SSID; type it manually"));
                                ssidDetected.setTextColor(WARN);
                            } else {
                                ssidDetected.setText(t("No se encontro handshake 4 vias en el pcap",
                                        "No 4-way handshake found in the pcap"));
                                ssidDetected.setTextColor(RED);
                            }
                        }
                    });
                } catch (final Throwable t) {
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            ssidDetected.setText(t("No se pudo analizar el pcap: ",
                                    "Could not analyze the pcap: ") + t.getMessage());
                            ssidDetected.setTextColor(RED);
                        }
                    });
                }
            }
        }, "ssid-detect").start();
    }

    private void startCrack() {
        if (pickedHashUri == null) {
            Toast.makeText(this, t("Elige primero el pcap", "Pick the pcap first"), Toast.LENGTH_SHORT).show();
            return;
        }
        if (pickedWordUri == null) {
            Toast.makeText(this, t("Elige la wordlist", "Pick the wordlist"), Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(this, CrackerService.class);
        i.setAction(CrackerService.ACTION_START);
        i.putExtra(CrackerService.EXTRA_HASH_URI, pickedHashUri);
        i.putExtra(CrackerService.EXTRA_WORD_URI, pickedWordUri);
        String ssid = ssidInput.getText().toString().trim();
        prefs.edit().putString("ssid", ssid).apply();
        if (!ssid.isEmpty()) {
            i.putExtra(CrackerService.EXTRA_SSID, ssid);
            log(t("SSID manual: ", "manual SSID: ") + ssid);
        }
        try {
            startForegroundService(i);
        } catch (Throwable t) {
            log(t("No se pudo iniciar el servicio: ", "Could not start the service: ") + t.getMessage());
            Toast.makeText(this, t("No se pudo iniciar: ", "Could not start: ") + t.getMessage(),
                    Toast.LENGTH_LONG).show();
            return;
        }
        startBtn.setEnabled(false);
        startBtn.setAlpha(0.45f);
        stopBtn.setEnabled(true);
        stopBtn.setAlpha(1f);
        resultCard.setVisibility(View.GONE);
        log(t("Iniciando: ", "Starting: ") + pickedHashName + " + " + pickedWordName);
    }

    private void stopCrack() {
        Intent i = new Intent(this, CrackerService.class);
        i.setAction(CrackerService.ACTION_STOP);
        startService(i);
        startBtn.setEnabled(true);
        startBtn.setAlpha(1f);
        stopBtn.setEnabled(false);
        stopBtn.setAlpha(0.45f);
    }

    private void convertTo22000() {
        if (pickedHashUri == null) {
            Toast.makeText(this, t("Elige primero el pcap", "Pick the pcap first"), Toast.LENGTH_SHORT).show();
            return;
        }
        final Uri uri = Uri.parse(pickedHashUri);
        log(t("Convirtiendo ", "Converting ") + pickedHashName + t(" a .22000 ...", " to .22000 ..."));
        new Thread(new Runnable() {
            @Override
            public void run() {
                File tmp = null;
                try {
                    tmp = new File(getCacheDir(), "conv.pcap");
                    InputStream in = getContentResolver().openInputStream(uri);
                    if (in == null) {
                        throw new Exception(t("no se pudo abrir el pcap", "could not open the pcap"));
                    }
                    OutputStream out = new FileOutputStream(tmp);
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                    out.close();
                    in.close();
                    String manual = ssidInput.getText().toString().trim();
                    final PcapConverter.Result r = PcapConverter.convert(tmp, manual);
                    if (r.lines.isEmpty()) {
                        final String msg = r.handshakes > 0
                                ? t("Hay handshake pero falta el SSID: escribelo y reintenta",
                                "Handshake found but SSID missing: type it and retry")
                                : t("No se encontro handshake 4 vias en el pcap",
                                "No 4-way handshake found in the pcap");
                        throw new Exception(msg);
                    }
                    StringBuilder sb = new StringBuilder();
                    for (String l : r.lines) {
                        sb.append(l).append('\n');
                    }
                    final String content = sb.toString();
                    String base = r.firstSsid;
                    if (base == null || base.isEmpty()) {
                        base = "handshake";
                    }
                    final String fileName = safeName(base) + ".22000";
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            pending22000 = content;
                            pending22000Name = fileName;
                            log(t("Listo: ", "Ready: ") + r.lines.size()
                                    + t(" linea(s) · ", " line(s) · ") + fileName);
                            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                            i.addCategory(Intent.CATEGORY_OPENABLE);
                            i.setType("application/octet-stream");
                            i.putExtra(Intent.EXTRA_TITLE, fileName);
                            try {
                                startActivityForResult(i, REQ_SAVE_22000);
                            } catch (Throwable t) {
                                Toast.makeText(MainActivity.this,
                                        t("Sin gestor de archivos: ", "No file manager: ") + t.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                } catch (final Throwable t) {
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            String m = t.getMessage();
                            log(t("Error al convertir: ", "Conversion error: ") + (m == null ? t.toString() : m));
                            Toast.makeText(MainActivity.this,
                                    m == null ? t("No se pudo convertir", "Could not convert") : m,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                } finally {
                    if (tmp != null) {
                        tmp.delete();
                    }
                }
            }
        }, "conv-22000").start();
    }

    private void save22000(final Uri uri) {
        final String content = pending22000;
        if (content == null) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    OutputStream out;
                    try {
                        out = getContentResolver().openOutputStream(uri, "wt");
                    } catch (Throwable mode) {
                        out = getContentResolver().openOutputStream(uri, "w");
                    }
                    if (out == null) {
                        throw new Exception(t("no se pudo abrir el destino", "could not open the destination"));
                    }
                    out.write(content.getBytes("UTF-8"));
                    out.close();
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            log(t("Guardado: ", "Saved: ") + pending22000Name + " (" + content.length() + " bytes)");
                            Toast.makeText(MainActivity.this,
                                    t("Guardado ", "Saved ") + pending22000Name, Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (final Throwable t) {
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            log(t("Error al guardar: ", "Save error: ") + t.getMessage());
                            Toast.makeText(MainActivity.this,
                                    t("No se pudo guardar: ", "Could not save: ") + t.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }, "save-22000").start();
    }

    private String safeName(String s) {
        String r = s.replaceAll("[^A-Za-z0-9._-]+", "_");
        if (r.isEmpty()) {
            r = "handshake";
        }
        if (r.length() > 60) {
            r = r.substring(0, 60);
        }
        return r;
    }

    private void copyLog() {
        String all = TextUtils.join("\n", logHistory);
        if (all.isEmpty()) {
            Toast.makeText(this, t("El registro esta vacio", "The log is empty"), Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("log", all));
        Toast.makeText(this, t("Registro copiado", "Log copied"), Toast.LENGTH_SHORT).show();
    }

    private void clearLog() {
        logHistory.clear();
        logView.setText("");
        Toast.makeText(this, t("Registro borrado", "Log cleared"), Toast.LENGTH_SHORT).show();
    }

    private void requestBatteryExemption() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Throwable t) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Throwable ignored) {
            }
        }
    }

    private void copyResult() {
        String p = resultView.getText().toString();
        if (p.isEmpty()) {
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("password", p));
        Toast.makeText(this, t("Contraseña copiada", "Password copied"), Toast.LENGTH_SHORT).show();
    }

    private int[] statusColors(String s) {
        if (s == null) {
            return new int[]{SB_OK, MUTED};
        }
        if (s.startsWith("Error")) {
            return new int[]{SB_ERR, RED};
        }
        if (s.startsWith("Contrasena") || s.startsWith("Contraseña")) {
            return new int[]{SB_GREEN, GREEN};
        }
        if (s.startsWith("Crackeando")) {
            return new int[]{SB_CYAN, CYAN};
        }
        if (s.startsWith("Detenido") || s.startsWith("Terminado")) {
            return new int[]{SB_GRAY, MUTED};
        }
        return new int[]{SB_VIOLET, VIOLET};
    }

    private void render(String status, double speed, long tested, long total,
                        long elapsedMs, String result) {
        int[] sc = statusColors(status);
        statusPill.setText(displayStatus(status));
        statusPill.setTextColor(sc[1]);
        statusPill.setBackground(rounded(sc[0], 0, 99));
        elapsedView.setText(fmt(elapsedMs));
        speedView.setText(String.format(Locale.US, "%,.0f", speed));
        if (total > 0) {
            int p = (int) Math.max(0, Math.min(1000, 1000.0 * tested / total));
            progressBar.setProgress(p);
            progressTextView.setText(String.format(Locale.US, "%,d / %,d  ·  %.1f%%",
                    tested, total, 100.0 * tested / total));
        } else {
            progressBar.setProgress(0);
            progressTextView.setText(String.format(Locale.US, t("%,d probadas", "%,d tested"), tested));
        }
        if (result != null && !result.isEmpty()) {
            resultCard.setVisibility(View.VISIBLE);
            resultView.setText(result);
            startBtn.setEnabled(true);
            startBtn.setAlpha(1f);
            stopBtn.setEnabled(false);
            stopBtn.setAlpha(0.45f);
        } else {
            resultCard.setVisibility(View.GONE);
        }
        if ("Terminado sin resultado".equals(status) || "Detenido".equals(status)
                || status.startsWith("Error")) {
            startBtn.setEnabled(true);
            startBtn.setAlpha(1f);
            stopBtn.setEnabled(false);
            stopBtn.setAlpha(0.45f);
        }
        if (status.startsWith("Crackeando") || status.startsWith("Preparando")
                || status.startsWith("Copiando") || status.startsWith("Iniciando")
                || status.startsWith("Reanudando")) {
            startBtn.setEnabled(false);
            startBtn.setAlpha(0.45f);
            stopBtn.setEnabled(true);
            stopBtn.setAlpha(1f);
        }
        log(displayStatus(status) + (speed > 0 ? String.format(Locale.US, " · %.0f keys/s", speed) : ""));
    }

    private String fmt(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    private void log(String s) {
        logHistory.add(s);
        while (logHistory.size() > 500) {
            logHistory.remove(0);
        }
        renderLog();
    }
}
