package com.example.decasport;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private int contadorBicis = 0;
    private int contadorSnow = 0;

    private ActivityResultLauncher<Intent> launcherBici;
    private ActivityResultLauncher<Intent> launcherSnow;

    private static final String KEY_BICIS = "clave_bicis";
    private static final String KEY_SNOW = "clave_snow";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 1. INICIALIZAR LOS LANZADORES PRIMERO
        // (Es muy importante crearlos antes de usarlos, justo aquí en el onCreate)
        launcherBici = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        contadorBicis++;
                        actualizarTextoContador(); // Usamos un método para no repetir código
                    }
                }
        );

        launcherSnow = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        contadorSnow++;
                        actualizarTextoContador(); // Usamos un método para no repetir código
                    }
                }
        );

        // 2. RECUPERAR LOS DATOS SI LA PANTALLA ROTÓ
        if (savedInstanceState != null) {
            contadorBicis = savedInstanceState.getInt(KEY_BICIS, 0);
            contadorSnow = savedInstanceState.getInt(KEY_SNOW, 0);
        }

        // 3. PINTAR EL TEXTO EN PANTALLA (sea 0 o el número recuperado tras girar)
        actualizarTextoContador();

        // 4. Clic en la imagen de la Bici
        ImageView bici = findViewById(R.id.imageView);
        if (bici != null) {
            bici.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, FormBiciActivity.class);
                intent.putExtra("TAG_BIENVENIDA", R.string.mensaje_bienvenida_form_bici);
                launcherBici.launch(intent);
            });
        }

        // 5. Clic en la imagen de Snowboard
        ImageView snow = findViewById(R.id.imageView2);
        if (snow != null) {
            snow.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, FormSnowActivity.class);
                intent.putExtra("TAG_BIENVENIDA", R.string.mensaje_bienvenida_form_snow);
                launcherSnow.launch(intent);
            });
        }
    }

    // Método auxiliar para actualizar el texto o dejarlo oculto si está a cero
    private void actualizarTextoContador() {
        TextView textoContador = findViewById(R.id.texto_contador);
        if (textoContador != null) {
            // Si ambos contadores están a 0, dejamos el texto vacío (no se ve nada)
            if (contadorBicis == 0 && contadorSnow == 0) {
                textoContador.setText("");
            } else {
                // Si hay al menos uno, cogemos el texto de strings.xml (cambia de idioma solo)
                String mensaje = getString(R.string.contador_formulario_enviado, contadorBicis, contadorSnow);
                textoContador.setText(mensaje);
            }
        }
    }
    // 6. GUARDAR LOS CONTADORES ANTES DE QUE SE DESTRUYA LA ACTIVIDAD AL ROTAR
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_BICIS, contadorBicis);
        outState.putInt(KEY_SNOW, contadorSnow);
    }

    public void salir_app(View view){
        finishAffinity();
    }
}