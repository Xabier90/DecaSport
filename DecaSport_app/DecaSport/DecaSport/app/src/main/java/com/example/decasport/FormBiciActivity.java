package com.example.decasport;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class FormBiciActivity extends AppCompatActivity {

    private TextView textoEstadoEnvio;
    private static final String KEY_ESTADO_ENVIO = "clave_estado_envio";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.form_bici); // (Cargará el normal o el -land automáticamente)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 1. Recogemos el saludo de bienvenida
        int idMensaje = getIntent().getIntExtra("TAG_BIENVENIDA", 0);
        TextView textoBienvenida = findViewById(R.id.mensaje_bienvenida);
        if (idMensaje != 0) {
            textoBienvenida.setText(getString(idMensaje));
        }

        // 2. Preparamos el texto de estado y el botón
        textoEstadoEnvio = findViewById(R.id.mensaje_formulario_enviado);
        Button boton_enviar = findViewById(R.id.button2);

        // 3. RECUPERAR EL ESTADO SI LA PANTALLA ROTÓ
        if (savedInstanceState != null) {
            String estadoGuardado = savedInstanceState.getString(KEY_ESTADO_ENVIO, "");
            textoEstadoEnvio.setText(estadoGuardado);
        }

        boton_enviar.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                textoEstadoEnvio.setText(getString(R.string.formulario_enviado));
                setResult(RESULT_OK);
            }
        });

        ImageView imagenVolver = findViewById(R.id.imagen_volver);
        imagenVolver.setOnClickListener(v -> finish());
    }

    // 4. GUARDAR EL TEXTO DEL FORMULARIO ANTES DE QUE SE DESTRUYA AL ROTAR
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_ESTADO_ENVIO, textoEstadoEnvio.getText().toString());
    }
}