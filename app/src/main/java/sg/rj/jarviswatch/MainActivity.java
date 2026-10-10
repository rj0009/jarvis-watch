package sg.rj.jarviswatch;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private TextView pmValue, pmLabel, aqiLine, psiLine, jogLine, pillar, riskLine, colourLine, updatedLine;
    private volatile boolean fetching = false;

    // ---- BaZi: RJ day master 戊 Wu Yang Earth ----
    private static final String[] STEMS = {"甲","乙","丙","丁","戊","己","庚","辛","壬","癸"};
    private static final String[] BRANCHES = {"子","丑","寅","卯","辰","巳","午","未","申","酉","戌","亥"};
    private static final String[] STEM_ELEMENTS = {"Wood","Wood","Fire","Fire","Earth","Earth","Metal","Metal","Water","Water"};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(Color.BLACK);

        TextView title = mkText("JARVIS GLANCE", 11, Color.parseColor("#c9a227"), false);
        title.setAllCaps(true);
        title.setLetterSpacing(0.15f);
        root.addView(title);

        pmValue = mkText("—", 54, Color.WHITE, true);
        root.addView(pmValue);

        pmLabel = mkText("PM2.5 1H · WEST", 12, Color.WHITE, true);
        root.addView(pmLabel);

        aqiLine = mkText("", 28, Color.WHITE, true);
        root.addView(aqiLine);

        psiLine = mkText("", 15, Color.WHITE, false);
        root.addView(psiLine);

        jogLine = mkText("JOG: …", 13, Color.WHITE, true);
        root.addView(jogLine);

        View div = new View(this);
        div.setBackgroundColor(Color.parseColor("#333333"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 1);
        lp.setMargins(dp(8), dp(10), dp(8), dp(10));
        root.addView(div, lp);

        pillar = mkText("—", 20, Color.WHITE, true);
        root.addView(pillar);

        riskLine = mkText("", 13, Color.WHITE, true);
        root.addView(riskLine);

        colourLine = mkText("", 12, Color.WHITE, false);
        root.addView(colourLine);

        updatedLine = mkText("tap to refresh", 10, Color.GRAY, false);
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
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private int dp(int d) { return Math.round(d * getResources().getDisplayMetrics().density); }

    private void refresh() {
        if (fetching) return;
        fetching = true;
        pmValue.setText("…");
        updatedLine.setText("fetching…");
        exec.execute(() -> {
            Integer pm25 = null, psi = null; String ts = null;
            try { JSONObject d = getJson("https://api.data.gov.sg/v1/environment/pm25");
                pm25 = d.getJSONArray("items").getJSONObject(0).getJSONObject("readings")
                        .getJSONObject("pm25_one_hourly").optInt("west", -1);
                if (pm25 == -1) pm25 = null;
                ts = d.getJSONArray("items").getJSONObject(0).optString("timestamp", "");
            } catch (Exception ignored) {}
            try { JSONObject d = getJson("https://api.data.gov.sg/v1/environment/psi");
                psi = d.getJSONArray("items").getJSONObject(0).getJSONObject("readings")
                        .getJSONObject("psi_twenty_four_hourly").optInt("west", -1);
                if (psi == -1) psi = null;
            } catch (Exception ignored) {}

            final Integer fPm = pm25, fPsi = psi;
            final String fTs = ts, err = (pm25 == null ? "PM2.5 fetch failed" : null);
            runOnUiThread(() -> render(fPm, fPsi, fTs, err));
        });
    }

    private JSONObject getJson(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(9000); c.setReadTimeout(9000);
        c.setRequestProperty("accept", "application/json");
        if (c.getResponseCode() != 200) throw new RuntimeException("http " + c.getResponseCode());
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
            String l; while ((l = r.readLine()) != null) sb.append(l);
        }
        return new JSONObject(sb.toString());
    }

    // US EPA AQI estimate from 1-hr PM2.5 concentration
    private static final double[][] AQI_BP = {
        {0,12.0,0,50},{12.1,35.4,51,100},{35.5,55.4,101,150},
        {55.5,150.4,151,200},{150.5,250.4,201,300},{250.5,350.4,301,400},{350.5,500,401,500}};

    static int usAqi(int pm) {
        double c = pm;
        for (double[] b : AQI_BP) {
            if (c >= b[0] && c <= b[1]) {
                double v = b[2] + (c - b[0]) * (b[3] - b[2]) / (b[1] - b[0]);
                return (int) Math.round(v);
            }
        }
        return 500;
    }

    private void render(Integer pm, Integer psi, String ts, String err) {
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

        // ---- PM2.5 / AQI / PSI ----
        if (pm != null) {
            int aqi = usAqi(pm);
            String band; int c;
            if (aqi <= 50)       { band = "GOOD";          c = Color.parseColor("#22c55e"); }
            else if (aqi <= 100) { band = "MODERATE";      c = Color.parseColor("#eab308"); }
            else if (aqi <= 150) { band = "USG — sensitive caution"; c = Color.parseColor("#f97316"); }
            else if (aqi <= 200) { band = "UNHEALTHY";     c = Color.parseColor("#ef4444"); }
            else if (aqi <= 300) { band = "VERY UNHEALTHY"; c = Color.parseColor("#a21caf"); }
            else                 { band = "HAZARDOUS";    c = Color.parseColor("#7f1d1d"); }

            pmValue.setText(String.valueOf(pm));
            pmValue.setTextColor(c);
            pmLabel.setText("PM2.5 1H · WEST µg/m³");
            pmLabel.setTextColor(c);
            aqiLine.setText("US AQI " + aqi);
            aqiLine.setTextColor(c);

            String jog;
            if (aqi <= 100)       jog = "GO";
            else if (aqi <= 150)  jog = "SHORT + N95";
            else if (aqi <= 200)  jog = "NO — indoors or gym";
            else                  jog = "NO — indoors";
            jogLine.setText("JOG: " + jog);
            jogLine.setTextColor(aqi <= 100 ? Color.parseColor("#22c55e") : Color.parseColor("#f97316"));
        } else {
            pmValue.setText("—");
            pmValue.setTextColor(Color.WHITE);
            pmLabel.setText("PM2.5 1H · WEST");
            aqiLine.setText("");
            jogLine.setText("JOG: ? (no data)");
            jogLine.setTextColor(Color.WHITE);
        }

        psiLine.setText(psi != null ? ("PSI 24H: " + psi) : (pm == null ? "" : "PSI 24H: —"));
        psiLine.setTextColor(Color.parseColor("#9ca3af"));

        String upd = ts != null && ts.length() >= 16 ? ts.substring(11, 16) + " SGT" : "";
        updatedLine.setText("tap to refresh" + (upd.isEmpty() ? "" : "  ·  " + upd));
    }

    @Override
    protected void onDestroy() { exec.shutdownNow(); super.onDestroy(); }
}
