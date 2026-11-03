package com.example.afluente.ui;

import androidx.core.content.ContextCompat;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.afluente.R;
import com.example.afluente.model.Alerta;
import com.google.android.material.card.MaterialCardView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AlertaAdapter extends RecyclerView.Adapter<AlertaAdapter.Holder> {
    public interface OnAlertClick { void onClick(Alerta alerta); }
    private final List<Alerta> items;
    private final OnAlertClick listener;
    private final Locale locale = new Locale("pt", "BR");
    public AlertaAdapter(List<Alerta> items, OnAlertClick listener) { this.items = items; this.listener = listener; }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notificacao, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        Alerta item = items.get(position);
        int[] colors = colors(holder.itemView, item.tipo);
        holder.card.setCardBackgroundColor(colors[0]);
        holder.card.setStrokeColor(colors[1]);
        holder.title.setTextColor(colors[1]);
        holder.title.setText(item.titulo);
        holder.description.setText(item.descricao);
        holder.date.setText(formatDate(item.data));
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(colors[1]);
        holder.dot.setBackground(dot);
        holder.dot.setVisibility(item.lido ? View.INVISIBLE : View.VISIBLE);
        holder.card.setContentDescription(item.titulo + ", " + item.descricao + ", " + formatDate(item.data) + (item.lido ? ", lido" : ", não lido"));
        holder.card.setOnClickListener(v -> listener.onClick(item));
    }
    @Override public int getItemCount() { return items.size(); }

    private int[] colors(View view, String type) {
        String t = type == null ? "" : type.toUpperCase(Locale.ROOT);
        if (t.equals("CONSUMO_ELEVADO") || t.equals("AUMENTO_ANORMAL") || t.equals("META_ULTRAPASSADA")) return new int[]{ContextCompat.getColor(view.getContext(), R.color.alert_red_background), ContextCompat.getColor(view.getContext(), R.color.alert_red_text)};
        if (t.equals("META_ULTRAPASSADA") || t.equals("META_ATINGIDA")) return new int[]{ContextCompat.getColor(view.getContext(), R.color.alert_yellow_background), ContextCompat.getColor(view.getContext(), R.color.alert_yellow_text)};
        if (t.equals("SISTEMA") || t.equals("RELATORIO_DISPONIVEL")) return new int[]{ContextCompat.getColor(view.getContext(), R.color.alert_green_background), ContextCompat.getColor(view.getContext(), R.color.alert_green_text)};
        return new int[]{ContextCompat.getColor(view.getContext(), R.color.alert_blue_background), ContextCompat.getColor(view.getContext(), R.color.alert_blue_text)};
    }

    private String formatDate(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", locale);
            parser.setLenient(false);
            Date date = parser.parse(raw);
            if (date == null) return raw;
            Calendar today = Calendar.getInstance();
            Calendar value = Calendar.getInstance(); value.setTime(date);
            String time = new SimpleDateFormat("HH:mm", locale).format(date);
            if (today.get(Calendar.YEAR) == value.get(Calendar.YEAR) && today.get(Calendar.DAY_OF_YEAR) == value.get(Calendar.DAY_OF_YEAR)) return "Hoje, " + time;
            today.add(Calendar.DAY_OF_YEAR, -1);
            if (today.get(Calendar.YEAR) == value.get(Calendar.YEAR) && today.get(Calendar.DAY_OF_YEAR) == value.get(Calendar.DAY_OF_YEAR)) return "Ontem, " + time;
            return new SimpleDateFormat("dd/MM/yyyy, HH:mm", locale).format(date);
        } catch (ParseException ex) { return raw; }
    }
    static class Holder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView title, description, date;
        final View dot;
        Holder(View view) {
            super(view);
            card = view.findViewById(R.id.cardNotificacao);
            title = view.findViewById(R.id.tvTitulo);
            description = view.findViewById(R.id.tvDescricao);
            date = view.findViewById(R.id.tvData);
            dot = view.findViewById(R.id.indicador);
        }
    }
}
