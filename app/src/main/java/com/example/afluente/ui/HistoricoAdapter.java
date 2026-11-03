package com.example.afluente.ui;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.afluente.R;
import com.example.afluente.model.RegistroConsumo;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoricoAdapter extends RecyclerView.Adapter<HistoricoAdapter.Holder> {
    public interface Listener { void editar(RegistroConsumo registro); void excluir(RegistroConsumo registro); }
    private final List<RegistroConsumo> items;
    private final Listener listener;
    private final Locale locale = new Locale("pt", "BR");
    public HistoricoAdapter(List<RegistroConsumo> items, Listener listener) { this.items = items; this.listener = listener; }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_historico, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        RegistroConsumo item = items.get(position);
        boolean water = "AGUA".equals(item.tipo);
        NumberFormat n = NumberFormat.getNumberInstance(locale); n.setMaximumFractionDigits(3);
        h.title.setText((water ? "Água — " : "Energia — ") + n.format(item.valor) + (water ? " m³" : " kWh"));
        h.cost.setText(item.custo == null ? "Sem tarifa" : NumberFormat.getCurrencyInstance(locale).format(item.custo));
        h.icon.setText(water ? "💧" : "⚡");
        GradientDrawable background = new GradientDrawable();
        background.setColor(ContextCompat.getColor(h.itemView.getContext(), water ? R.color.water_background : R.color.energy_background));
        background.setCornerRadius(12 * h.itemView.getResources().getDisplayMetrics().density);
        h.icon.setBackground(background);
        String note = item.observacao == null ? "" : item.observacao.trim();
        h.details.setText(formatDate(item.data) + (note.isEmpty() ? "" : " · " + note));
        h.itemView.findViewById(R.id.btnEditarRegistro).setOnClickListener(v -> listener.editar(item));
        h.itemView.findViewById(R.id.btnExcluirRegistro).setOnClickListener(v -> listener.excluir(item));
    }
    private String formatDate(String value) {
        if (value == null) return "";
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT); parser.setLenient(false);
            Date parsed = parser.parse(value);
            return parsed == null ? value : new SimpleDateFormat("dd/MM/yyyy", locale).format(parsed);
        } catch (ParseException e) { return value; }
    }
    @Override public int getItemCount() { return items.size(); }
    static class Holder extends RecyclerView.ViewHolder {
        TextView icon, title, details, cost;
        Holder(View v) { super(v); icon = v.findViewById(R.id.tvIconeRegistro); title = v.findViewById(R.id.tvTituloRegistro); details = v.findViewById(R.id.tvDetalhesRegistro); cost = v.findViewById(R.id.tvCustoRegistro); }
    }
}
