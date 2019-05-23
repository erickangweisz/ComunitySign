package com.agenda.jonatan.comunitysign;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;

import butterknife.Bind;
import butterknife.ButterKnife;

public class ListSigns extends Activity {

    private static int PHOTO_SESSION = 001;

    @Bind(R.id.listViewLista)
    ListView listViewLista;
    @Bind(R.id.btnAddImage)
    Button btnAddImage;
    @Bind(R.id.etInputSearch)
    EditText etInputSearch;

    private String nombreImagen = "";
    private String filtro = "";
    private String imagePath;

    public static String NOMBRE_PREF = "misPreferencias";
    public static String imageName;
    public static int pos_list;
    public static ArrayAdapter<String> arrayAdapter;
    public static EditText etImageName;

    private SharedPreferences preferences;
    private Dialog dialog;

    public Button btnLoadImage;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list_signs);
        ButterKnife.bind(this);

        arrayAdapter = new ArrayAdapter<>(this, android.R.layout.preference_category, MainActivity.image_name_string);
        listViewLista.setAdapter(arrayAdapter);
        listViewLista.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                pos_list = position;
                createAlertDialog();
                return true;
            }
        });

        btnAddImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog = new Dialog(ListSigns.this);
                dialog.setTitle("AÑADIR NUEVO SIGNO");
                dialog.setContentView(R.layout.save_image);
                dialog.show();

                etImageName = (EditText) dialog.findViewById(R.id.etSearch);
                btnLoadImage = (Button) dialog.findViewById(R.id.btnLoadImage);

                nombreImagen = etImageName.getText().toString();
                btnLoadImage.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        photoPickerIntent();
                        dialog.dismiss();
                    }
                });
            }
        });

        etInputSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filtro = etInputSearch.getText().toString();
                set_refresh_data();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    public void set_refresh_data() {
        ArrayList<String> sign_from_db = new ArrayList<>();
        for (int i = 0; i < MainActivity.image_name_string_default.length; i++) {
            if (filtro.length() > 0) {
                if (MainActivity.image_name_string.get(i).contains(filtro))
                    sign_from_db.add(MainActivity.image_name_string.get(i));
            } else
                sign_from_db = MainActivity.image_name_string;
        }

        ListAdapter adapter = new ArrayAdapter<>(this, android.R.layout.preference_category, sign_from_db);
        listViewLista.setAdapter(adapter);
    }

    // Creamos un AlertDialog para confirmar si deseamos borrar o no.
    private void createAlertDialog() {
        final AlertDialog.Builder alertDialog = new AlertDialog.Builder(ListSigns.this);
        alertDialog.setTitle("Borrar imagen");
        alertDialog.setMessage("¿Desea eliminar de la lista?");
        alertDialog.setPositiveButton("Si", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //arrayAdapter.remove(arrayAdapter.getItem(pos_list));

                arrayAdapter.remove(pos_list + "");
                arrayAdapter.notifyDataSetChanged();

                deleteImage();
            }
        });
        alertDialog.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        alertDialog.setIcon(R.drawable.ic_launcher);
        alertDialog.create();
        alertDialog.show();
    }

    // Eliminamos el elemento de la base de datos y del archivo xml.
    public void deleteImage() {
        MainActivity.database.delete(MainActivity.TABLE_NAME, MainActivity.KEY_ID + "=" + (ListSigns.pos_list + 1), null);

        preferences = getSharedPreferences(ListSigns.NOMBRE_PREF, MODE_PRIVATE);

        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(MainActivity.image_name_string.get((ListSigns.pos_list)).toString());
        editor.commit();

        Toast.makeText(ListSigns.this, "Imagen eliminada correctamente.", Toast.LENGTH_SHORT).show();
    }

    // Intent para abrir la galeria y filtrar solo las imagenes.
    private void photoPickerIntent() {
        Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
        photoPickerIntent.setType("image/*");
        startActivityForResult(photoPickerIntent, PHOTO_SESSION);
    }

    @Override
    // Controla el resultado del Intent de abrir la galeria y rellena las variables de la ruta y el nombre de la ruta para introducirlas en la BD.
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == PHOTO_SESSION && data != null) {
            Uri selectedImage = data.getData();
            imagePath = getRealPathFromURI(getApplicationContext(), selectedImage);

            imageName = etImageName.getText().toString();

            MainActivity.InsertDefaultImagesIntoDataBase(imagePath, imageName); // Guarda la imagen seleccionada y el nombre en la BD.
            MainActivity.image_name_string.add(imageName); // Guarda la String en el ArrayList de palabras para el ListView.

            // Obtener el objeto SharedPreferences object
            preferences = getSharedPreferences(NOMBRE_PREF, MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            // Salvar los valores del ArrayList en preferencias
            for (int i = 0; i < MainActivity.image_name_string.size(); i++) {
                editor.putString(MainActivity.image_name_string.get(i), MainActivity.image_name_string.get(i));
            }
            // salvar los valores
            editor.commit();
            // mostramos mensaje de salvado correctamente
            Toast.makeText(getApplicationContext(), "Imagen salvada correctamente.", Toast.LENGTH_LONG).show();
        }
    }

    // Convierte una ruta Uri en una String.
    public String getRealPathFromURI(Context context, Uri contentUri) {
        Cursor cursor = null;
        try {
            String[] proj = {MediaStore.Images.Media.DATA};
            cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } finally {
            if (cursor != null)
                cursor.close();
        }
    }
}
