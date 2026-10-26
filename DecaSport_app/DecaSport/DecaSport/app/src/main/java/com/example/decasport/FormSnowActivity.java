package com.example.decasport;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull; // <--- Importante para onSaveInstanceState
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class FormSnowActivity extends AppCompatActivity {

    // 1. Declaramos el texto de envío a nivel de clase para poder guardarlo
    private TextView textoEstadoEnvio;
    private static final String KEY_ESTADO_ENVIO = "clave_estado_envio";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.form_snow);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 2. Recogemos el saludo de bienvenida de snowboard
        int idMensaje = getIntent().getIntExtra("TAG_BIENVENIDA", 0);
        TextView textoBienvenida = findViewById(R.id.mensaje_bienvenida);
        if (idMensaje != 0 && textoBienvenida != null) {
            textoBienvenida.setText(getString(idMensaje));
        }

        // 3. Inicializamos el texto de estado y el botón de enviar
        textoEstadoEnvio = findViewById(R.id.mensaje_formulario_enviado);
        Button boton_enviar = findViewById(R.id.button2);

        // 4. RECUPERAR EL ESTADO SI LA PANTALLA ROTÓ
        if (savedInstanceState != null) {
            String estadoGuardado = savedInstanceState.getString(KEY_ESTADO_ENVIO, "");
            if (textoEstadoEnvio != null) {
                textoEstadoEnvio.setText(estadoGuardado);
            }
        }

        if (boton_enviar != null) {
            boton_enviar.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View view){
                    // Avisamos a MainActivity de que se ha enviado este formulario
                    setResult(RESULT_OK);

                    if (textoEstadoEnvio != null) {
                        textoEstadoEnvio.setText(getString(R.string.formulario_enviado));
                    }
                }
            });
        }

        // 5. Imagen para volver al menú principal
        ImageView imagenVolver = findViewById(R.id.imagen_volver);
        if (imagenVolver != null) {
            imagenVolver.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }
    }

    // 6. GUARDAR EL TEXTO DE ENVÍO ANTES DE QUE SE DESTRUYA AL ROTAR
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (textoEstadoEnvio != null) {
            outState.putString(KEY_ESTADO_ENVIO, textoEstadoEnvio.getText().toString());
        }
    }
}