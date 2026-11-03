package com.example.afluente.ui;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.afluente.R;

public class PerfilActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_perfil);
        if (state == null) getSupportFragmentManager().beginTransaction().replace(R.id.perfilContainer, new PerfilFragment()).commit();
    }
}
