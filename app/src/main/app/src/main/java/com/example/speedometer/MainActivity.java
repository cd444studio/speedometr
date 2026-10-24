package com.example.speedometer;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.os.Build;
import android.content.Context;
import java.util.Locale;

public class MainActivity extends Activity implements LocationListener {
    private static final int REQ_LOCATION = 100;
    private LocationManager locationManager;
    private TextView speedText, statusText, maxText, avgText, distanceText, timeText;
    private Button unitButton, startButton;
    private GaugeView gauge;
    private boolean useMph = false, measuring = false;
    private float maxSpeedKmh = 0f, currentSpeedKmh = 0f;
    private double distanceMeters = 0, totalSpeedKmh = 0;
    private int samples = 0;
    private Location lastLocation;
    private long startTime = 0, elapsedBefore = 0;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        buildUi();
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) t.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        else t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return t;
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout statCard(String heading, String initial, String id) {
        LinearLayout card = vertical();
        card.setPadding(16, 12, 12, 12);
        card.setBackgroundColor(Color.rgb(12,12,12));
        TextView h = label(heading, 12, Color.rgb(155,155,155), true);
        card.addView(h);
        TextView v = label(initial, 22, Color.WHITE, true);
        card.addView(v);
        if (id.equals("max")) maxText = v;
        if (id.equals("avg")) avgText = v;
        if (id.equals("dist")) distanceText = v;
        if (id.equals("time")) timeText = v;
        return card;
    }

    private void buildUi() {
        LinearLayout root = vertical();
        root.setPadding(18, 8, 18, 8);
        root.setBackgroundColor(Color.BLACK);

        TextView title = label("S P E E D O M E T E R", 20, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, 44));
        TextView subtitle = label("G P S   T R A C K E R", 10, Color.rgb(160,160,160), true);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, 24));

        gauge = new GaugeView(this);
        root.addView(gauge, new LinearLayout.LayoutParams(-1, 0, 1.25f));

        speedText = label("0 km/h", 36, Color.WHITE, true);
        speedText.setGravity(Gravity.CENTER);
        root.addView(speedText, new LinearLayout.LayoutParams(-1, 48));

        LinearLayout units = new LinearLayout(this);
        units.setGravity(Gravity.CENTER);
        unitButton = new Button(this);
        unitButton.setText("KM/H  ✓");
        styleButton(unitButton);
        units.addView(unitButton, new LinearLayout.LayoutParams(140, 48));
        Button spacerButton = new Button(this);
        spacerButton.setText("MPH");
        styleButton(spacerButton);
        units.addView(spacerButton, new LinearLayout.LayoutParams(120, 48));
        unitButton.setOnClickListener(v -> { useMph = false; updateStats(currentSpeedKmh); });
        spacerButton.setOnClickListener(v -> { useMph = true; updateStats(currentSpeedKmh); });
        root.addView(units, new LinearLayout.LayoutParams(-1, 54));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, 80, 1);
        cell.setMargins(3, 5, 3, 5);
        row1.addView(statCard("MAX. RYCHLOST", "0 km/h", "max"), cell);
        row1.addView(statCard("PRŮMĚRNÁ RYCHLOST", "0 km/h", "avg"), cell);
        root.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(statCard("VZDÁLENOST", "0.00 km", "dist"), new LinearLayout.LayoutParams(0, 80, 1));
        row2.addView(statCard("DOBA JÍZDY", "00:00:00", "time"), new LinearLayout.LayoutParams(0, 80, 1));
        root.addView(row2);

        statusText = label("○  GPS ČEKÁ NA PŘIPOJENÍ", 13, Color.rgb(190,190,190), true);
        statusText.setPadding(12, 8, 12, 8);
        root.addView(statusText, new LinearLayout.LayoutParams(-1, 38));

        startButton = new Button(this);
        startButton.setText("▶  START MĚŘENÍ");
        styleButton(startButton);
        root.addView(startButton, new LinearLayout.LayoutParams(-1, 54));
        startButton.setOnClickListener(v -> { if (measuring) stopMeasuring(); else requestAndStart(); });

        TextView note = label("ČERNOBÍLÝ DIGITÁLNÍ TACHOMETR  •  GPS", 9, Color.rgb(115,115,115), false);
        note.setGravity(Gravity.CENTER);
        root.addView(note, new LinearLayout.LayoutParams(-1, 24));
        setContentView(root);
        updateStats(0);
    }

    private void styleButton(Button b) {
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(25,25,25)));
    }

    private void requestAndStart() {
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        startMeasuring();
    }

    private void startMeasuring() {
        try {
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                statusText.setText("GPS VYPNUTO • ZAPNI POLOHU");
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0, this);
            try { locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, this); } catch (Exception ignored) {}
            measuring = true;
            startTime = System.currentTimeMillis();
            startButton.setText("■  ZASTAVIT MĚŘENÍ");
            statusText.setText("●  GPS PŘIPOJENO • ČEKÁM NA DATA");
        } catch (SecurityException e) {
            statusText.setText("POVOL PŘÍSTUP K POLOZE");
        } catch (Exception e) {
            statusText.setText("GPS NENÍ DOSTUPNÉ");
        }
    }

    private void stopMeasuring() {
        try { locationManager.removeUpdates(this); } catch (SecurityException ignored) {}
        measuring = false;
        elapsedBefore += System.currentTimeMillis() - startTime;
        startButton.setText("▶  START MĚŘENÍ");
        statusText.setText("○  MĚŘENÍ ZASTAVENO");
        lastLocation = null;
        updateStats(currentSpeedKmh);
    }

    @Override public void onLocationChanged(Location location) {
        float kmh = location.hasSpeed() ? location.getSpeed() * 3.6f : 0f;
        currentSpeedKmh = kmh;
        if (kmh > maxSpeedKmh) maxSpeedKmh = kmh;
        if (lastLocation != null) {
            float delta = lastLocation.distanceTo(location);
            if (delta < 100 && location.getAccuracy() < 50) distanceMeters += delta;
        }
        lastLocation = location;
        totalSpeedKmh += kmh;
        samples++;
        statusText.setText("●  GPS PŘIPOJENO • PŘESNOST " + Math.round(location.getAccuracy()) + " m");
        updateStats(kmh);
    }

    private void updateStats(float speedKmh) {
        float factor = useMph ? 0.621371f : 1f;
        String unit = useMph ? "mph" : "km/h";
        if (speedText != null) speedText.setText(String.format(Locale.getDefault(), "%.0f %s", speedKmh * factor, unit));
        if (maxText != null) maxText.setText(String.format(Locale.getDefault(), "%.0f %s", maxSpeedKmh * factor, unit));
        if (avgText != null) avgText.setText(String.format(Locale.getDefault(), "%.0f %s", (samples == 0 ? 0 : totalSpeedKmh / samples) * factor, unit));
        if (distanceText != null) distanceText.setText(String.format(Locale.getDefault(), "%.2f km", distanceMeters / 1000.0));
        if (timeText != null) {
            long millis = elapsedBefore + (measuring ? System.currentTimeMillis() - startTime : 0);
            long sec = millis / 1000;
            timeText.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", sec/3600, (sec%3600)/60, sec%60));
        }
        if (gauge != null) { gauge.speed = speedKmh * factor; gauge.unit = unit; gauge.invalidate(); }
        if (unitButton != null) unitButton.setText(useMph ? "KM/H" : "KM/H  ✓");
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            boolean granted = false;
            for (int result : grantResults) if (result == PackageManager.PERMISSION_GRANTED) granted = true;
            if (granted) startMeasuring();
            else statusText.setText("BEZ POLOHY NELZE MĚŘIT RYCHLOST");
        }
    }
    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) {}
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override protected void onPause() {
        super.onPause();
        if (measuring) stopMeasuring();
    }

    private static class GaugeView extends View {
        Paint p = new Paint(3);
        float speed = 0;
        String unit = "km/h";
        GaugeView(Context c) { super(c); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth(), h = getHeight();
            float cx = w/2f, cy = h*0.58f;
            float r = Math.min(w*0.43f, h*0.47f);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(Color.rgb(80,80,80));
            RectF oval = new RectF(cx-r, cy-r, cx+r, cy+r);
            c.drawArc(oval, 135, 270, false, p);
            p.setColor(Color.WHITE); p.setStrokeWidth(7); p.setStrokeCap(Paint.Cap.SQUARE);
            float progress = Math.min(270, Math.max(0, speed/240f*270f));
            c.drawArc(oval, 135, progress, false, p);
            p.setStrokeWidth(2);
            for (int i=0;i<=48;i++) {
                float a = (float)Math.toRadians(135+i*270f/48f);
                float r1 = r-14, r2 = r-(i%4==0?32:23);
                p.setColor(i*5<=speed?Color.WHITE:Color.rgb(95,95,95));
                c.drawLine(cx+(float)Math.cos(a)*r1, cy+(float)Math.sin(a)*r1,
                           cx+(float)Math.cos(a)*r2, cy+(float)Math.sin(a)*r2, p);
            }
            p.setStyle(Paint.Style.FILL); p.setColor(Color.WHITE);
            p.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            p.setTextAlign(Paint.Align.CENTER); p.setTextSize(Math.min(w*0.21f, 74));
            c.drawText(String.format(Locale.getDefault(), "%.0f", speed), cx, cy+18, p);
            p.setTextSize(18); p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            c.drawText(unit, cx, cy+48, p);
            p.setTextSize(12); p.setColor(Color.GRAY);
            c.drawText("0          60         120        180        240", cx, cy+r+24, p);
        }
    }
}
