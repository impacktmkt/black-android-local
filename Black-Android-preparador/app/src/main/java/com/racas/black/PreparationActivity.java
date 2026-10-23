package com.racas.black;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import java.io.File;
import java.util.Locale;

/** The only launcher: native code starts only after preparation completes. */
public class PreparationActivity extends Activity {
    private static final int BG = Color.rgb(10, 12, 13), SURFACE = Color.rgb(21, 24, 25);
    private static final int GOLD = Color.rgb(224, 189, 111), TEXT = Color.rgb(246, 243, 235);
    private static final int MUTED = Color.rgb(177, 183, 182), ERROR = Color.rgb(255, 164, 152);
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView heading, detail, status, percentage, transfer, error;
    private TextView[] steps;
    private Button primary, pause, manual;
    private ProgressBar progress;
    private GameDownload.Cancellation cancellation;
    private boolean busy, opening, destroyed;
    private GameImporter importer;
    private SharedPreferences prefs;
    private String source;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        prefs = getSharedPreferences("preparation", MODE_PRIVATE);
        source = getString(R.string.game_download_url).trim();
        importer = new GameImporter(this);
        if (isReady()) { openGame(); return; }
        buildScreen();
        if (source.isEmpty()) {
            primary.setEnabled(false); primary.setText("Preparar jogo"); primary.setAlpha(.45f);
            error.setText("Solicite a versão final ao responsável pelo app. O download ainda não está disponível nesta versão.");
            error.setVisibility(View.VISIBLE);
        }
    }

    private File assets() {
        File root = getExternalFilesDir(null);
        return new File(root != null ? root : getFilesDir(), "assets");
    }
    private boolean isReady() {
        String name = prefs.getString("ready_name", "");
        if (name.isEmpty() || !new File(name).getName().equals(name)) return false;
        File image = new File(assets(), name);
        return image.isFile() && image.length() > 0 && image.length() == prefs.getLong("ready_bytes", -1);
    }
    private void prepared(File file) {
        prefs.edit().putString("ready_name", file.getName()).putLong("ready_bytes", file.length()).apply();
        busy = false; status.setText("Tudo pronto"); percentage.setText("100%");
        progress.setProgress(1000); heading.setText("Seu jogo está pronto.");
        detail.setText("Abrindo BLACK…");
        for (TextView step : steps) step.setTextColor(GOLD);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        primary.setText("Jogar agora"); primary.setEnabled(true); primary.setOnClickListener(v -> openGame());
        pause.setVisibility(View.GONE); manual.setVisibility(View.GONE);
        main.postDelayed(() -> { if (!destroyed && !isFinishing() && hasWindowFocus()) openGame(); }, 400);
    }
    private void openGame() {
        if (opening || destroyed) return;
        opening = true;
        startActivity(new Intent(this, BlackActivity.class));
        finish();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setLineSpacing(dp(3), 1.1f); t.setIncludeFontPadding(false); return t;
    }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private LinearLayout.LayoutParams row(int height) { return new LinearLayout.LayoutParams(-1, height < 0 ? height : dp(height)); }
    private void gap(LinearLayout parent, int size) { View spacer = new View(this); parent.addView(spacer, row(size)); }
    private Button button(String label, boolean filled) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(16);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); b.setMinHeight(dp(56));
        b.setTextColor(filled ? BG : TEXT); b.setPadding(dp(16), dp(8), dp(16), dp(8));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(filled ? 0x33000000 : 0x33FFFFFF),
                shape(filled ? GOLD : SURFACE, 12), null));
        b.setOnFocusChangeListener((v, focused) -> b.setAlpha(focused ? .85f : 1f));
        return b;
    }
    private void buildScreen() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout outer = new LinearLayout(this); outer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(outer, new ScrollView.LayoutParams(-1, -2));
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(dp(24), dp(20), dp(24), dp(8));
        TextView brand = text("BLACK", 18, TEXT, true); brand.setLetterSpacing(.16f);
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView edition = text("ANDROID", 11, GOLD, true); edition.setLetterSpacing(.16f); top.addView(edition);
        outer.addView(top, row(-2));
        ImageView art = new ImageView(this); art.setImageResource(R.drawable.ic_launcher_art);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP); art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        outer.addView(art, row(220));
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(24), dp(4), dp(24), dp(28));
        outer.addView(body, row(-2));
        TextView eyebrow = text("UMA VEZ. DEPOIS, É SÓ JOGAR.", 11, GOLD, true); eyebrow.setLetterSpacing(.10f); body.addView(eyebrow);
        gap(body, 12);
        heading = text("Prepare o jogo.\nEntre em ação.", 30, TEXT, true); heading.setAccessibilityHeading(true); body.addView(heading);
        gap(body, 12);
        detail = text("Toque no botão e deixe a preparação com o app. Sem extrair arquivos ou escolher pastas.", 16, MUTED, false); body.addView(detail);
        gap(body, 24);
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setBackground(shape(SURFACE, 16)); body.addView(card, row(-2));
        status = text("Preparação inicial", 16, TEXT, true); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout title = new LinearLayout(this); title.setGravity(Gravity.CENTER_VERTICAL);
        title.addView(status, new LinearLayout.LayoutParams(0, -2, 1)); percentage = text("0%", 14, GOLD, true); title.addView(percentage); card.addView(title);
        gap(card, 16);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setMax(1000);
        progress.setProgressTintList(ColorStateList.valueOf(GOLD)); progress.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF353C3D));
        progress.setContentDescription("Progresso da preparação"); card.addView(progress, row(8));
        gap(card, 12); transfer = text("2,22 GB de dados • Use uma conexão estável", 13, MUTED, false); card.addView(transfer);
        gap(card, 20);
        LinearLayout stageRow = new LinearLayout(this); steps = new TextView[3];
        String[] labels = {"01  Baixar", "02  Verificar", "03  Jogar"};
        for (int i = 0; i < 3; i++) { steps[i] = text(labels[i], 12, i == 0 ? GOLD : MUTED, true); stageRow.addView(steps[i], new LinearLayout.LayoutParams(0, -2, 1)); }
        card.addView(stageRow);
        gap(body, 16);
        error = text("", 14, ERROR, false); error.setVisibility(View.GONE); error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); body.addView(error);
        gap(body, 16);
        primary = button("Preparar jogo", true); primary.setOnClickListener(v -> startPreparation()); body.addView(primary, row(-2));
        gap(body, 8);
        pause = button("Pausar preparação", false); pause.setVisibility(View.GONE); pause.setOnClickListener(v -> pausePreparation()); body.addView(pause, row(-2));
        manual = button("Já tenho a ISO · Import game", false); manual.setOnClickListener(v -> { busy = importer.pick(); refreshBusy(); }); body.addView(manual, row(-2));
        gap(body, 20);
        TextView footer = text("Mantenha o app aberto durante a preparação.\nDepois, o jogo abre direto pelo ícone BLACK.", 12, MUTED, false); footer.setGravity(Gravity.CENTER); body.addView(footer);
        setContentView(scroll);
    }
    private void refreshBusy() {
        primary.setEnabled(!busy && !source.isEmpty()); manual.setEnabled(!busy);
        pause.setVisibility(busy && cancellation != null ? View.VISIBLE : View.GONE);
    }
    private void startPreparation() {
        if (busy || source.isEmpty()) return;
        busy = true; cancellation = new GameDownload.Cancellation(); error.setVisibility(View.GONE);
        primary.setText("Preparando…"); pause.setText("Pausar preparação"); pause.setEnabled(true);
        refreshBusy(); getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        new Thread(() -> {
            try {
                File image = new GameDownload().prepare(source, assets(), cancellation, (stage, done, total) ->
                    main.post(() -> {
                        if (destroyed || !busy) return;
                        if (!stage.contentEquals(status.getText())) status.setText(stage); percentage.setText(Math.round(done * 100f / total) + "%");
                        progress.setProgress((int)(done * 1000 / total));
                        transfer.setText(String.format(Locale.forLanguageTag("pt-BR"), "%.2f GB de %.2f GB", done / 1e9, total / 1e9));
                        steps[1].setTextColor(stage.startsWith("Verificando") ? GOLD : MUTED);
                    }));
                main.post(() -> { if (!destroyed && !isFinishing()) prepared(image); });
            } catch (Exception e) {
                String message = e.getMessage();
                main.post(() -> {
                    if (destroyed || isFinishing()) return;
                    busy = false; cancellation = null;
                    status.setText("Preparação interrompida");
                    error.setText(message != null ? message : "Não foi possível concluir. Confira a conexão e tente novamente."); error.setVisibility(View.VISIBLE);
                    primary.setText(new File(assets(), "BLACK.download.part").length() > 0 ? "Continuar preparação" : "Tentar novamente");
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); refreshBusy();
                });
            }
        }, "BLACK-Preparation").start();
    }
    private void pausePreparation() {
        if (cancellation != null) { pause.setText("Pausando…"); pause.setEnabled(false); cancellation.cancel(); }
    }
    @Override protected void onStop() { super.onStop(); if (busy && cancellation != null) cancellation.cancel(); }
    @Override protected void onDestroy() { destroyed = true; if (cancellation != null) cancellation.cancel(); main.removeCallbacksAndMessages(null); super.onDestroy(); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != GameImporter.REQUEST) return;
        Uri uri = result == RESULT_OK && data != null ? data.getData() : null;
        if (uri == null) { busy = false; refreshBusy(); return; }
        File dir = assets(); busy = true; refreshBusy();
        importer.copy(uri, dir, ok -> {
            busy = false;
            if (destroyed || isFinishing()) return;
            if (ok) {
                File[] images = dir.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".iso"));
                if (images != null && images.length > 0) { prepared(images[0]); return; }
            }
            refreshBusy();
        });
    }
}
