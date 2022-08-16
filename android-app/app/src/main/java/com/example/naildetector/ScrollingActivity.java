package com.example.naildetector;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.ml.common.FirebaseMLException;
import com.google.firebase.ml.common.modeldownload.FirebaseModelDownloadConditions;
import com.google.firebase.ml.common.modeldownload.FirebaseModelManager;
import com.google.firebase.ml.common.modeldownload.FirebaseRemoteModel;
import com.google.firebase.ml.custom.*;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


import android.provider.MediaStore;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class ScrollingActivity extends AppCompatActivity {

    ImageView imageView;
    TextView resultView;

    private final int CAM_GAL_INTRO = 007;
    private final int CAM_IN = 111;
    private final int GAL_IN = 112;
    private List<String> mLabelList;

    private String MODEL_PATH = "file:///android_asset/nailit.tflite";
    private FirebaseCustomLocalModel localModel;
    private FirebaseCustomRemoteModel remoteModel;

    private FirebaseModelInterpreter firebaseInterpreter;
    private FirebaseModelInputOutputOptions inputOutputOptions;

    public ScrollingActivity() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scrolling);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        imageView = findViewById(R.id.imageview);
        resultView = findViewById(R.id.results);


        try {
            mLabelList = ImageUtils.getLabels(getAssets().open("labels.json"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        getPermissions(ScrollingActivity.this);


        // Build a remote model source object by specifying the name you assigned the model
        // when you uploaded it in the Firebase console.
        remoteModel = new FirebaseCustomRemoteModel.Builder("nail-detector")
                .build();

        FirebaseModelDownloadConditions conditions = new FirebaseModelDownloadConditions.Builder()
                .requireWifi()
                .build();
        FirebaseModelManager.getInstance().download(remoteModel, conditions)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        System.out.println("Model successfully updated from firebase.");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        System.out.println(e);
                    }
        });

        localModel = new FirebaseCustomLocalModel.Builder()
                .setAssetFilePath("nailit.tflite")
                .build();



        //code If we dont have to use remoteModel
//        try {
//            FirebaseModelInterpreterOptions options =
//                    new FirebaseModelInterpreterOptions.Builder(localModel).build();
//            interpreter = FirebaseModelInterpreter.getInstance(options);
//        } catch (FirebaseMLException e) {
//            e.printStackTrace();
//        }

        FirebaseModelManager.getInstance().isModelDownloaded(remoteModel)
                .addOnSuccessListener(new OnSuccessListener<Boolean>() {
                    @Override
                    public void onSuccess(Boolean isDownloaded) {
                        FirebaseModelInterpreterOptions options;
                        if (isDownloaded) {
                            options = new FirebaseModelInterpreterOptions.Builder(localModel).build();
                            System.out.println("remote model");
                        } else {
                            options = new FirebaseModelInterpreterOptions.Builder(localModel).build();
                            System.out.println("local model");
                        }

                        try {
                            firebaseInterpreter = FirebaseModelInterpreter.getInstance(options);
                        }
                        catch (FirebaseMLException e) {
                            e.printStackTrace();
                        }
                    }
                });


        try {
            inputOutputOptions = new FirebaseModelInputOutputOptions.Builder()
                    .setInputFormat(0, FirebaseModelDataType.FLOAT32, new int[]{1, 224, 224, 3})
                    .setOutputFormat(0, FirebaseModelDataType.FLOAT32, new int[]{1, 3})
                    .build();
        } catch (FirebaseMLException e) {
            e.printStackTrace();
        }

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.predict);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                final CharSequence[] items = { "Camera", "Choose from Library", "Cancel" };
                AlertDialog.Builder builder = new AlertDialog.Builder(ScrollingActivity.this);
                builder.setTitle("Select Image:");

                builder.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int item) {
                        if (items[item].equals("Camera")) {
                            cameraIntent();
                        } else if (items[item].equals("Choose from Library")) {
                            galleryIntent();
                        } else if (items[item].equals("Cancel")) {
                            dialog.dismiss();
                        }
                    }
                });
                builder.show();

            }
        });
    }

    public void cameraIntent(){
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, 0);
    }

    public void galleryIntent(){

        Intent pickPhoto = new Intent(Intent.ACTION_GET_CONTENT,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(pickPhoto , 1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && data != null) {
            if(requestCode == 1) {
                Uri selectedImage = data.getData();
                try {
                    Bitmap imageBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImage);
                    imageView.setImageBitmap(imageBitmap);
                    predict(imageBitmap);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                imageView.setImageURI(selectedImage);
            }
            else if(requestCode == 0){
                Bitmap imageBitmap = (Bitmap) data.getExtras().get("data");
                imageView.setImageBitmap(imageBitmap);
                predict(imageBitmap);
            }
        }
    }

    private void predict(Bitmap bitmap){
        bitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true);

        int batchNum = 0;
        float[][][][] input = new float[1][224][224][3];
        for (int x = 0; x < 224; x++) {
            for (int y = 0; y < 224; y++) {
                int pixel = bitmap.getPixel(x, y);
                // Normalize channel values to [-1.0, 1.0]. This requirement varies by
                // model. For example, some models might require values to be normalized
                // to the range [0.0, 1.0] instead.
                int a = 127;
                float b = 128.0f;
//                int a = 0;
//                float b = 1;
                input[batchNum][x][y][0] = (Color.red(pixel) - a) / b;
                input[batchNum][x][y][1] = (Color.green(pixel) - a) / b;
                input[batchNum][x][y][2] = (Color.blue(pixel) - a) / b;
            }
        }



        imageView.setImageBitmap(bitmap);

//        for (int j = 0; j < original.Height; j++)
//        {
//            for (int i = 0; i < original.Width; i++)
//            {
//                Color newColor = Color.FromArgb((int)grayScale[i + j * original.Width], (int)grayScale[i + j * original.Width], (int)grayScale[i + j * original.Width]);
//
//                newBitmap.SetPixel(i, j, newColor);
//            }
//        }




        try {
            FirebaseModelInputs inputs = new FirebaseModelInputs.Builder()
                    .add(input)  // add() as many input arrays as your model requires
                    .build();
            resultView.setText("processing...");
            firebaseInterpreter.run(inputs, inputOutputOptions)
                    .addOnSuccessListener(
                            new OnSuccessListener<FirebaseModelOutputs>() {
                                @Override
                                public void onSuccess(FirebaseModelOutputs result) {
                                    float[][] output = result.getOutput(0);
                                    float[] probabilities = output[0];
                                    String op = "";
                                    int maxInd = 1;
                                    float maxProb = -1;
                                    for(int i = 0; i < 3; i++){
                                        if(probabilities[i] > maxProb){
                                            maxProb = probabilities[i];
                                            maxInd = i;
                                        }
                                    }
                                    if(maxProb<0.6){
                                        maxInd = 3;
                                    }
                                    resultView.setText(mLabelList.get(maxInd));
                                }
                            })
                    .addOnFailureListener(
                            new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    Toast.makeText(ScrollingActivity.this, "Couldn't run model",Toast.LENGTH_LONG).show();
                                }
                            });
        }
        catch (FirebaseMLException e){
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        final Context context = ScrollingActivity.this;

        for(int i= 0; i < grantResults.length ; i++){
            if(permissions[i] == null){
                continue;
            }
            int res = grantResults[i];
            if(res == PackageManager.PERMISSION_DENIED){
                AlertDialog.Builder alertBuilder = new AlertDialog.Builder(context);
                alertBuilder.setCancelable(true);
                alertBuilder.setTitle("Permission necessary");
                alertBuilder.setMessage("App can't function without permissions.");
                alertBuilder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
                    public void onClick(DialogInterface dialog, int which) {
                        getPermissions(context);
                        dialog.dismiss();
                    }
                });
                AlertDialog alert = alertBuilder.create();
                alert.show();
            }
        }
    }

    private void getPermissions(final Context context){

        List<String> permissions = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED){
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA);
        }
        if(ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        if(permissions.size() > 0)
            ActivityCompat.requestPermissions((Activity) context,
                permissions.toArray(new String[3]), 0);



//        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE)
//                != PackageManager.PERMISSION_GRANTED ||
//                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
//                != PackageManager.PERMISSION_GRANTED) {
//            getPermissions(context);
//        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_scrolling, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
