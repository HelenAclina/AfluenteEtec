package com.example.afluente.ui;

import android.content.Context;
import android.widget.TextView;
import com.example.afluente.R;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;
import java.text.NumberFormat;
import java.util.Locale;

/** Mostra o valor original, inclusive quando a série visual está escalada. */
public class ChartMarker extends MarkerView {
    public static class Value {
        final double value; final String unit;
        public Value(double value, String unit) { this.value = value; this.unit = unit; }
    }
    private final TextView text;
    private final Locale locale = new Locale("pt", "BR");
    public ChartMarker(Context context) { super(context, R.layout.chart_marker); text = findViewById(R.id.tvChartMarker); }
    public static void attach(Chart<?> chart) {
        ChartMarker marker = new ChartMarker(chart.getContext());
        marker.setChartView(chart);
        chart.setMarker(marker);
    }
    @Override public void refreshContent(Entry entry, Highlight highlight) {
        Value item = entry.getData() instanceof Value ? (Value) entry.getData() : new Value(entry.getY(), "");
        NumberFormat format = "R$".equals(item.unit) ? NumberFormat.getCurrencyInstance(locale) : NumberFormat.getNumberInstance(locale);
        if (!"R$".equals(item.unit)) format.setMaximumFractionDigits(3);
        text.setText(format.format(item.value) + (item.unit.isEmpty() || "R$".equals(item.unit) ? "" : " " + item.unit));
        super.refreshContent(entry, highlight);
    }
    @Override public MPPointF getOffset() { return new MPPointF(-getWidth() / 2f, -getHeight() - 8f); }
}
