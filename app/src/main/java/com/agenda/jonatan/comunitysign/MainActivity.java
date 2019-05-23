package com.agenda.jonatan.comunitysign;

import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Vibrator;
import android.speech.RecognizerIntent;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

//006-914-6552-100//
import butterknife.Bind;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity {

    public static SQLiteDatabase database;
    @Bind(R.id.ivResult)
    ImageView ivResult;
    @Bind(R.id.etSearch)
    EditText etSearch;
    @Bind(R.id.btnMicro)
    ImageButton btnMicro;
    @Bind(R.id.tvResult)
    TextView tvResult;
    @Bind(R.id.btnListadoSignos)
    ImageButton btnListadoSignos;
    @Bind(R.id.btnDactilologico)
    ImageButton btnDactilologico;

    public static final String TABLE_NAME = "csign_table";
    public static final String KEY_ID = "id_image";
    public static final String KEY_NAME = "image_name";
    public static final String KEY_IMAGE = "path_image";
    public static final Integer IS_CORRECT = 100;

    private Bitmap bitmap;

    private static final char[] CHARS_WITH_TILDE = {'á', 'é', 'í', 'ó', 'ú', 'Á', 'É', 'Í', 'Ó', 'Ú'};
    private static final char[] CHARS_WITHOUT_TILDE = {'a', 'e', 'i', 'o', 'u', 'A', 'E', 'I', 'O', 'U'};

    public static String[] image_name_string_default = new String[664]; // COLECCIÓN QUE GUARDA LOS NOMBRES DE LAS IMAGENES POR DEFECTO.
    public static ArrayList<String> image_name_string = new ArrayList<>(); // INTRODUCIDAS POR EL USUARIO + POR DEFECTO.

    private String wordFound = "";

    public static ContentValues values;

    private Vibrator vb;
    private boolean isActivatedVibration = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        etSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                showImageFoundOnImageViewObtainedFromEtSearch();
                tvResult.setText(etSearch.getText().toString().toUpperCase());
                return false;
            }
        });

        btnMicro.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptSpeechInput();
            }
        });

        btnListadoSignos.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                vb = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (isActivatedVibration)
                    vb.vibrate(80);
                Intent intent = new Intent(getApplicationContext(), ListSigns.class);
                startActivity(intent);
            }
        });

        btnDactilologico.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                vb = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (isActivatedVibration)
                    vb.vibrate(80);
                String dagtilologicalText = "dagtilologico";
                String resource = "drawable";

                int res_image = getResources().getIdentifier(dagtilologicalText, resource, getPackageName());
                if (ivResult.getDrawable() == null)
                    ivResult.setImageResource(res_image);
                else
                    ivResult.setImageResource(0);
            }
        });

        loadDefaultArray();
        createOrOpenDB();
        loadListDefault();
        if (dataBaseIsEmpty())
            InsertDefaultImagesIntoDataBase();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    //TODO Actualizar controlador
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                Toast.makeText(MainActivity.this, "Desarrollado por Jonatan Arrocha Kang", Toast.LENGTH_LONG).show();
                break;

            case R.id.vibration_mode:
                if (isActivatedVibration) {
                    isActivatedVibration = false;
                    item.setTitle(R.string.vibration_mode_on);
                    Toast.makeText(MainActivity.this, "Vibrador desactivado.", Toast.LENGTH_SHORT).show();
                } else {
                    isActivatedVibration = true;
                    item.setTitle(R.string.vibration_mode_off);
                    Toast.makeText(MainActivity.this, "Vibrador activado.", Toast.LENGTH_SHORT).show();
                }
        }

        return super.onOptionsItemSelected(item);
    }

    // Devuelve la wordFound filtrandole la extension.
    private String methodSubstring(String str) {
        String newstr = "";
        if (str.contains("."))
            newstr = str.substring(0, str.length() - 4);

        return newstr;
    }

    // Entra en la base de datos, si no existe, la crea.
    private void createOrOpenDB() {
        database = this.openOrCreateDatabase("comunitysign.database", Context.MODE_PRIVATE, null);

        String CREATE_TABLE_SIGN = "CREATE TABLE if not exists " + TABLE_NAME + "("
                + KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " + KEY_NAME + " TEXT, "
                + KEY_IMAGE + " BLOB" + ")";

        database.execSQL(CREATE_TABLE_SIGN);
    }

    // Carga la lista por defecto si no hay añadidas, si las hay, llama al método loadList.
    private void loadListDefault() {
        SharedPreferences preferences = getSharedPreferences(ListSigns.NOMBRE_PREF, MODE_PRIVATE);
        Map<String, ?> allEntries = preferences.getAll();
        int count = allEntries.size();

        if (count == 0) {
            image_name_string.clear();
            for (int i = 0; i < image_name_string_default.length; i++) {
                image_name_string.add(image_name_string_default[i]);
            }
        } else
            loadList();
    }

    // Con esto iteramos la carpeta signos en la sdcard y cogemos sus nombres para rellenar el array por defecto;
    private void loadDefaultArray() {
        AssetManager assetManager = getAssets();
        try {
            String[] filelist = assetManager.list("sign");
            for (int i = 0; i < image_name_string_default.length; i++) {
                if (filelist[i].contains("."))
                    image_name_string_default[i] = methodSubstring(filelist[i]);
            }
            if (image_name_string_default == null)
                Toast.makeText(MainActivity.this, "Problemas al cargar imagenes", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Cargamos la lista con lo obtenido del objeto SharedPreferences.
    private void loadList() {
        // cargar el objeto SharedPreferences
        SharedPreferences preferences = getSharedPreferences(ListSigns.NOMBRE_PREF, MODE_PRIVATE);
        Map<String, ?> allEntries = preferences.getAll();

        image_name_string.clear();
        Iterator<String> it = allEntries.keySet().iterator();

        String n = "";
        while (it.hasNext()) {
            n = it.next();
            image_name_string.add(n);
        }
    }

    // Guarda las imagenes en la BD.
    public void InsertDefaultImagesIntoDataBase() {
        try {
            AssetManager assetManager = getAssets();
            for (int i = 0; i < image_name_string.size(); i++) {
                InputStream is = assetManager.open("sign/" + image_name_string.get(i) + ".png");
                byte[] image = new byte[is.available()];
                is.read(image);

                values = new ContentValues();
                values.put(KEY_NAME, image_name_string.get(i));
                values.put(KEY_IMAGE, image);
                database.insert(TABLE_NAME, null, values);

                is.close();
            }
            Toast.makeText(this, "insert sucess", Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), "Los datos no se han insertado correctamente.", Toast.LENGTH_LONG).show();
        }
    }

    // Guarda la imagen en la BD en base a una ruta y un nombre que recoge como parámetros.
    public static void InsertDefaultImagesIntoDataBase(String imagePath, String imageName) {
        try {
            FileInputStream fileInputStream = new FileInputStream(imagePath);
            byte[] imageInBytes = new byte[fileInputStream.available()];
            fileInputStream.read(imageInBytes);
            ContentValues values = new ContentValues();
            values.put(KEY_NAME, imageName);
            values.put(KEY_IMAGE, imageInBytes);
            database.insert(TABLE_NAME, null, values);

            fileInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void showImageFoundOnImageView() {
        wordFound = tvResult.getText().toString().toLowerCase();
        String wordWithoutSpecialCharacters = replaceSpecialCharactersOfaWord(wordFound);
        Cursor databaseCursor = database.rawQuery("select * from " + TABLE_NAME + " where " + KEY_NAME + " = " + "'" + wordWithoutSpecialCharacters + "'", null);
        if (databaseCursor.moveToNext()) {
            byte[] imageInBytes = databaseCursor.getBlob(2);
            bitmap = BitmapFactory.decodeByteArray(imageInBytes, 0, imageInBytes.length);
            ivResult.setImageBitmap(bitmap);
            Toast.makeText(this, "select success", Toast.LENGTH_LONG).show();
        } else {
            showImageNotFound();
        }
    }

    private void showImageNotFound() {
        ivResult.setImageResource(R.drawable.file_not_found);
    }

    public void showImageFoundOnImageViewObtainedFromEtSearch() {
        wordFound = etSearch.getText().toString().toLowerCase();
        String imageName = replaceSpecialCharactersOfaWord(wordFound);
        Cursor dataBaseCursor = database.rawQuery("select * from " + TABLE_NAME + " where " + KEY_NAME + " = " + "'" + imageName + "'", null);
        if (dataBaseCursor.moveToNext()) {
            byte[] imageInBytes = dataBaseCursor.getBlob(2);
            bitmap = BitmapFactory.decodeByteArray(imageInBytes, 0, imageInBytes.length);
            ivResult.setImageBitmap(bitmap);
            Toast.makeText(this, "select success", Toast.LENGTH_LONG).show();
        } else {
            showImageNotFound();
        }
    }

    private boolean dataBaseIsEmpty() {
        String countRegistersFromDataBase = "SELECT count(*) FROM " + TABLE_NAME;
        Cursor dataBaseCursor = database.rawQuery(countRegistersFromDataBase, null);
        dataBaseCursor.moveToFirst();

        int numberOfRegisters = dataBaseCursor.getInt(0);

        if (numberOfRegisters == 0) return true;
        else return false;
    }

    public void promptSpeechInput() {
        Intent recognizeSpeechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizeSpeechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizeSpeechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        recognizeSpeechIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Dí algo!");

        try {
            startActivityForResult(recognizeSpeechIntent, IS_CORRECT);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(MainActivity.this, "Lo sentimos! tu dispositivo no es compatible con el lenguaje hablado.", Toast.LENGTH_LONG).show();
        }
    }

    // Controla el Intent del micrófono y almacenamos lo obtenido en una colección.
    public void onActivityResult(int request_code, int result_code, Intent intent) {
        super.onActivityResult(request_code, result_code, intent);

        if (request_code == IS_CORRECT && result_code == RESULT_OK && intent != null) {
            ArrayList<String> capturedSpeech = intent.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            tvResult.setText(capturedSpeech.get(0).toUpperCase());
            wordFound = replaceSpecialCharactersOfaWord(capturedSpeech.get(0).toLowerCase());
            showImageFoundOnImageView();
            etSearch.setText("");
        }
    }

    private String replaceSpecialCharactersOfaWord(String word) {
        for (int i = 0; i < word.length(); i++) {
            for (int j = 0; j < CHARS_WITH_TILDE.length; j++) {
                if (word.charAt(i) == CHARS_WITH_TILDE[j])
                    word = word.replace(CHARS_WITH_TILDE[j], CHARS_WITHOUT_TILDE[j]);
                else if (word.charAt(i) == ' ')
                    word = word.replace(' ', '_');
                else if (word.charAt(i) == 'ñ')
                    word = word.replace('ñ', 'n');
            }
        }
        return word;
    }

}