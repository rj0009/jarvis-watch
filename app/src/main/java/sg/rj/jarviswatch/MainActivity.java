package sg.rj.jarviswatch;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private TextView psiValue, psiBand, psiNote, jogLine, pillar, riskLine, colourLine, updatedLine;
    private volatile boolean fetching = false;

    // ---- BaZi: RJ day master 戊 Wu Yang Earth ----
    // favourable: Wood + Water, secondary: Metal, challenging: Fire + Earth
    private static final String[] STEMS = {"甲","乙","丙","丁","戊","己","庚","辛","壬","癸"};
    private static final String[] BRANCHES = {"子","丑","寅","卯","辰","巳","午","未","申","酉","戌","亥"};
    private static final String[] STEM_ELEMENTS = {"Wood","Wood","Fire","Fire","Earth","Earth","Metal","Metal","Water","Water"};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(30), dp(38), dp(30), dp(38));
        root.setBackgroundColor(Color.BLACK);

        TextView title = mkText("JARVIS GLANCE", 12, Color.parseColor("#c9a227"), false);
        title.setAllCaps(true);
        title.setLetterSpacing(0.15f);
        root.addView(title);

        psiValue = mkText("—", 46, Color.WHITE, true);
        root.addView(psiValue);

        psiBand = mkText("PSI · West", 15, Color.WHITE, true);
        root.addView(psiBand);

        psiNote = mkText("loading…", 12, Color.GRAY, false);
        root.addView(psiNote);

        jogLine = mkText("JOG: …", 14, Color.WHITE, true);
        root.addView(jogLine);

        View div = new View(this);
        div.setBackgroundColor(Color.parseColor("#333333"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 1);
        lp.setMargins(dp(10), dp(14), dp(10), dp(14));
        root.addView(div, lp);

        pillar = mkText("—", 22, Color.WHITE, true);
        root.addView(pillar);

        riskLine = mkText("", 14, Color.WHITE, true);
        root.addView(riskLine);

        colourLine = mkText("", 13, Color.WHITE, false);
        root.addView(colourLine);

        updatedLine = mkText("tap to refresh", 11, Color.GRAY, false);
        root.addView(updatedLine);

        setContentView(root);
        root.setOnClickListener(v -> refresh());

        refresh();
    }

    private TextView mkText(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private int dp(int d) { return Math.round(d * getResources().getDisplayMetrics().density); }

    private void refresh() {
        if (fetching) return;
        fetching = true;
        psiValue.setText("…");
        updatedLine.setText("fetching…");
        exec.execute(() -> {
            Integer psi = null; String ts = null;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL("https://api.data.gov.sg/v1/environment/psi").openConnection();
                c.setConnectTimeout(9000); c.setReadTimeout(9000);
                c.setRequestProperty("accept", "application/json");
                if (c.getResponseCode() == 200) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                        String l; while ((l = r.readLine()) != null) sb.append(l);
                    }
                    JSONObject d = new JSONObject(sb.toString());
                    JSONObject v = d.getJSONArray("items").getJSONObject(0).getJSONObject("readings")
                            .getJSONObject("psi_twenty_four_hourly");
                    psi = v.optInt("west", -1); if (psi == -1) psi = null;
                    ts = d.getJSONArray("items").getJSONObject(0).optString("timestamp", "");
                }
            } catch (Exception e) { /* offline: keep BaZi, show error below */ }

            final Integer fPsi = psi;
            final String fTs = ts, err = (psi == null ? "PSI fetch failed" : null);
            runOnUiThread(() -> render(fPsi, fTs, err));
        });
    }

    private void render(Integer psi, String ts, String err) {
        fetching = false;

        // ---- BaZi always renders, offline-safe ----
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Singapore"));
        long days = ChronoUnit.DAYS.between(LocalDate.of(2026, 9, 21), today); // anchor: 2026-09-21 = 戊戌 = idx 34
        int idx = (int) (((34 + days) % 60 + 60) % 60);
        String pillarStr = STEMS[idx % 10] + BRANCHES[idx % 12];
        String el = STEM_ELEMENTS[idx % 10];
        boolean fav = el.equals("Wood") || el.equals("Water");
        boolean sec = el.equals("Metal");
        String risk = fav ? "HIGH" : (sec ? "MEDIUM" : "LOW");
        String riskNote = fav ? "Favourable — move today" : (sec ? "Drain day — execute, don't launch" : "Heavy day — no new entries");
        String colours = el.equals("Fire") || el.equals("Earth") ? (el.equals("Fire") ? "Black · Navy" : "Green") : (fav ? (el.equals("Wood") ? "Green · Black · Navy" : "Black · Navy · Blue") : "White · Silver · Gold");
        int riskColor = fav ? Color.parseColor("#22c55e") : (sec ? Color.parseColor("#eab308") : Color.parseColor("#f97316"));

        pillar.setText(pillarStr + "  ·  " + el.toUpperCase() + " day");
        riskLine.setText("RISK " + risk + " — " + riskNote);
        riskLine.setTextColor(riskColor);
        colourLine.setText("Wear: " + colours);

        // ---- PSI ----
        if (psi != null) {
            String band; int c; String note;
            if (psi <= 50)      { band = "GOOD";       c = Color.parseColor("#22c55e"); note = "All activities OK"; }
            else if (psi <= 100){ band = "MODERATE";   c = Color.parseColor("#eab308"); note = "Normal for most"; }
            else if (psi <= 200){ band = "UNHEALTHY";  c = Color.parseColor("#f97316"); note = "Cut outdoor exertion"; }
            else if (psi <= 300){ band = "VERY UNHEALTHY"; c = Color.parseColor("#ef4444"); note = "Stay indoors"; }
            else                { band = "HAZARDOUS";  c = Color.parseColor("#a21caf"); note = "Masks · indoors"; }

            psiValue.setText(String.valueOf(psi));
            psiValue.setTextColor(c);
            psiBand.setText("PSI 24h · " + band + " · West");
            psiBand.setTextColor(c);
            psiNote.setText(note);

            String jog = psi <= 50 ? "GO" : psi <= 100 ? "GO (hydrate)" : psi <= 150 ? "SHORT + N95 route" : "NO — indoors";
            jogLine.setText("JOG: " + jog);
            jogLine.setTextColor(psi <= 100 ? Color.parseColor("#22c55e") : Color.parseColor("#f97316"));
        } else {
            psiValue.setText("—");
            psiValue.setTextColor(Color.WHITE);
            psiBand.setText("PSI · West");
            psiNote.setText(err != null ? err : "no data");
            jogLine.setText("JOG: ? (no PSI)");
            jogLine.setTextColor(Color.WHITE);
        }

        String upd = ts != null && ts.length() >= 16 ? ts.substring(11, 16) + " SGT" : "";
        updatedLine.setText("tap to refresh" + (upd.isEmpty() ? "" : "  ·  " + upd));
    }

    @Override
    protected void onDestroy() { exec.shutdownNow(); super.onDestroy(); }
}
