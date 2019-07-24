package com.ispiral.verid;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.appliedrec.verid.core.LivenessDetectionSessionSettings;
import com.appliedrec.verid.core.VerID;
import com.appliedrec.verid.core.VerIDFactory;
import com.appliedrec.verid.core.VerIDFactoryDelegate;
import com.appliedrec.verid.core.VerIDSessionResult;
import com.appliedrec.verid.ui.VerIDSessionActivity;
import com.appliedrec.verid.ui.VerIDSessionIntent;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.entity.StringEntity;

public class MainActivity extends AppCompatActivity {
    //Set Request Codes for Activity Results
    static final int REQUEST_CODE_LIVENESS_DETECTION = 0;
    static final int REQUEST_CODE_SCAN_DOCUMENT = 1;
    static final int REQUEST_CODE_WRITE_PERMISSION = 2;
    static final int REQUEST_CODE_CAMERA = 3;

    //Globally Initialize variables that will be used for liveness testing and face matching
    //Path to save scanned document locally
    final String documentImageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/Folder/";

    //URIs of the images
    Uri livenessPhotoUri;
    Uri documentPhotoUri;

    Bitmap livenessImageBitmap;
    Bitmap documentImageBitmap;

    //Layout elements
    ImageView livenessImageIV;
    ImageView documentImageIV;

    ImageView logoIV;

    Button livenessTestBtn;
    Button scanDocumentBtn;
    Button faceMatchingBtn;

    TextView livenessTitleTV;
    TextView documentTitleTV;

    TextView livenessTestResultTV;
    TextView faceMatchingResultTV;

    //Helper Variables to ensure permissions exist
    boolean cameraPermission = false;
    boolean writePermission = false;

    boolean hasCompletedFaceMatching = false;

    boolean isLoading = false;

    //Variables for Loading Panel
    AlphaAnimation inAnimation;
    AlphaAnimation outAnimation;

    FrameLayout progressBarHolder;

    //Helper Variables for iKYC Client

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Request the permissions for file writing and camera usage
        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            Log.v("Write Perm Req", "Granted");
            writePermission = true;
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE_WRITE_PERMISSION);
        }

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            Log.v("Camera Req", "Granted");
            cameraPermission = true;
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA);
        }

        //Instantiate layout objects
        livenessImageIV = findViewById(R.id.livenessImageIV);
        documentImageIV = findViewById(R.id.documentImageIV);
        logoIV = findViewById(R.id.logoIV);

        livenessTestBtn = findViewById(R.id.livenessTestBtn);
        scanDocumentBtn = findViewById(R.id.scanDocumentBtn);
        faceMatchingBtn = findViewById(R.id.faceMatchingBtn);

        livenessTitleTV = findViewById(R.id.livenessTitleTV);
        documentTitleTV = findViewById(R.id.documentTitleTV);

        livenessTestResultTV = findViewById(R.id.livenessTestResultTV);
        faceMatchingResultTV = findViewById(R.id.faceMatchingResultTV2);

        //POINT 1 - Add Listeners

        //Add listener to 'Perform Liveness Test' button click
        livenessTestBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(livenessImageBitmap != null){
                    Toast.makeText(MainActivity.this,"You have already performed a liveness testing. If you wish to perform it again, please reset the application.",Toast.LENGTH_LONG).show();
                }
                else{
                    startLivenessDetectionSession();
                }
                //performLivenessTesting();
            }
        });

        //Add listener to 'Scan Document' button click
        scanDocumentBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(documentImageBitmap != null){
                    Toast.makeText(MainActivity.this,"You have already performed a document scan. If you wish to perform it again, please reset the application.",Toast.LENGTH_LONG).show();
                }
                else{
                    startScanDocumentSession();
                }
            }
        });

        //Add listener to 'Scan Document' button click
        faceMatchingBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (livenessImageBitmap == null || documentImageBitmap == null) {
                    Toast.makeText(MainActivity.this, "Please perform a liveness testing and a document scan first.", Toast.LENGTH_LONG).show();
                } else {
                    try {
                        if(hasCompletedFaceMatching != true){
                            startFaceMatching();
                        }
                        else{
                            Toast.makeText(MainActivity.this, "You have already performed a face matching. If you wish to perform it again, please reset the application.", Toast.LENGTH_LONG).show();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        //Hide elements
        hideDocumentDetails();
        hideFaceMatchingDetails();
        hideLivenessDetails();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //POINT 4 - Liveness Session Completed. Store image
        if (requestCode == REQUEST_CODE_LIVENESS_DETECTION && resultCode == RESULT_OK && data != null) {
            VerIDSessionResult sessionResult = data.getParcelableExtra(VerIDSessionActivity.EXTRA_RESULT);
            if (sessionResult != null && sessionResult.getError() == null) {
                // Liveness detection session succeeded. Store image URI
                Log.d("Liveness Result", "Success");

                Uri[] livenessFaces = sessionResult.getImageUris();
                livenessPhotoUri = livenessFaces[livenessFaces.length - 1];

                //Show image on sample bitmap
                try {
                    Bitmap livenessPhotoBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), livenessPhotoUri);
                    livenessImageBitmap = livenessPhotoBitmap;
                    livenessImageIV.setImageBitmap(livenessPhotoBitmap);
                    showLivenessDetails();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                //Display success message
                livenessTestResultTV.setText("Liveness Result:\nSuccess");
            } else {
                Log.d("Liveness Result", "Fail");

                //Display error message
                livenessTestResultTV.setText("Liveness Result:\nFailed");
            }
        }

        //POINT 5 - Document Scan Completed. Get Photo
        if (requestCode == REQUEST_CODE_SCAN_DOCUMENT && resultCode == RESULT_OK) {
            Log.d("Scan Document Result", "Success");
            //Show the image on sample bitmap
            //Show image on sample bitmap
            try {
                Bitmap documentPhotoBitmap = handleSamplingAndRotationBitmap(getApplicationContext(), documentPhotoUri);
                documentImageBitmap = documentPhotoBitmap;
                documentImageIV.setImageBitmap(documentPhotoBitmap);
                showDocumentDetails();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.v("Perm Result", "Permission: " + permissions[0] + "was " + grantResults[0]);

            //If permissions are granted, set helper variables to true
            if (requestCode == REQUEST_CODE_WRITE_PERMISSION && grantResults[0] == 0) {
                writePermission = true;
            }

            if (requestCode == REQUEST_CODE_CAMERA && grantResults[0] == 0) {
                cameraPermission = true;
            }
        }
    }

    //POINT 2 - How to call SDK for Liveness Detection

    //Start Liveness Detection
    private void startLivenessDetectionSession() {
        enableLoadingPanel();
        //Liveness Detection Logic. Using iSpiral SDK
        VerIDFactory veridFactory = new VerIDFactory(this, new VerIDFactoryDelegate() {
            @Override
            public void veridFactoryDidCreateEnvironment(VerIDFactory verIDFactory, VerID verID) {
                // You can now start a Ver-ID session
                Log.d("Environment Creation", "Success");
                LivenessDetectionSessionSettings settings = new LivenessDetectionSessionSettings();
                settings.setNumberOfResultsToCollect(2);
                Intent intent = new VerIDSessionIntent(getApplicationContext(), verID, settings);
                disableLoadingPanel();
                startActivityForResult(intent, REQUEST_CODE_LIVENESS_DETECTION);
            }

            @Override
            public void veridFactoryDidFailWithException(VerIDFactory verIDFactory, Exception e) {
                // Failed to create an instance of Ver-ID
                Log.d("Environment Creation", "Fail");
            }
        });
        veridFactory.createVerID();
    }

    //POINT 3 - How to start camera intent to scan Document

    //Start Document Scan
    private void startScanDocumentSession() {
        // Scan Document Logic. Using phone camera

        File folder = new File(documentImageDirectory);

        boolean success = true;
        if (!folder.exists()) {
            success = folder.mkdirs();
        }
        if (success) {
            // Do something on success

            String file = documentImageDirectory + DateFormat.format("yyyy-MM-dd_hhmmss", new Date()).toString() + ".jpg";

            File newfile = new File(file);

            try {
                newfile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }

            StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
            StrictMode.setVmPolicy(builder.build());

            documentPhotoUri = Uri.fromFile(newfile);

            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, documentPhotoUri);

            startActivityForResult(cameraIntent, REQUEST_CODE_SCAN_DOCUMENT);

        } else {
            // Do something else on failure
        }

    }

    //POINT 6 - Call Face Matching from iKYC Client
    //Start Face Matching
    private void startFaceMatching() throws IOException {

        enableLoadingPanel();

        //Set Authorization Variables from manifest file
        RequestParams params = new RequestParams();

        try{
            ApplicationInfo ai = getApplicationContext().getPackageManager().getApplicationInfo(getApplicationContext().getPackageName(), PackageManager.GET_META_DATA);
            params.put("grant_type", "client_credentials");
            params.put("client_id", ai.metaData.getString("com.ispiral.client_id"));
            params.put("client_secret", ai.metaData.getString("com.ispiral.client_secret"));
        }catch(PackageManager.NameNotFoundException ex){
            Toast.makeText(getApplicationContext(),"Error reading manifest file metadata. Ensure you have added client id and secret",Toast.LENGTH_LONG).show();
            return;
        }

        //Call Authorization to get a bearer token. In reality, this will be either called as soon as the client requests to perform a liveness Testing, or during the loading of the VerID SDK.
        //Response also includes "expires_in" which indicates in how many seconds the token will expire.
        iKYC_Client.clientAuthorization(params, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, final JSONObject response) {
                //Call was successful
                System.out.print(response);
                try {
                    //Get Token
                    final String access_token = response.getString("access_token");

                    //Get the images to send
                    //Load both profile picture and selfie picture into bitmaps, so as to convert them into byte arrays
                    Bitmap sourceImageBitmap = livenessImageBitmap;
                    Bitmap targetImageBitmap = documentImageBitmap;

                    ByteArrayOutputStream sourceStream = new ByteArrayOutputStream();
                    ByteArrayOutputStream targetStream = new ByteArrayOutputStream();

                    sourceImageBitmap.compress(Bitmap.CompressFormat.PNG, 100, sourceStream);
                    targetImageBitmap.compress(Bitmap.CompressFormat.PNG, 100, targetStream);

                    byte[] sourceImage = sourceStream.toByteArray();
                    byte[] targetImage = targetStream.toByteArray();

                    sourceImageBitmap.recycle();
                    targetImageBitmap.recycle();

                    //Convert the byte arrays into a file form since API is accepting IFormFile data types for source and target images
                    File sourceFile = new File(MainActivity.this.getCacheDir(), "SourceFile.jpg");
                    sourceFile.createNewFile();
                    File targetFile = new File(MainActivity.this.getCacheDir(), "TargetFile.jpg");
                    targetFile.createNewFile();

                    OutputStream osSource = new FileOutputStream(sourceFile);
                    osSource.write(sourceImage);
                    OutputStream osTarget = new FileOutputStream(targetFile);
                    osTarget.write(targetImage);

                    //Set the two files as the parameters for the API Call
                    RequestParams requestParams = new RequestParams();

                    requestParams.put("SourceImage", sourceFile);
                    requestParams.put("TargetImage", targetFile);

                    //Call compare faces from the API
                    iKYC_Client.faceMatchingRequest(access_token, requestParams, new JsonHttpResponseHandler() {
                        @Override
                        public void onSuccess(int statusCode, Header[] headers, final JSONObject response) {
                            //Call was successful
                            JSONArray faceMatches;
                            try {
                                //Handle the JSON Response, so as to get match status, similarity and confidence.
                                faceMatches = response.getJSONArray("faceMatches");
                                for (int i = 0; i < faceMatches.length(); ++i) {
                                    JSONObject match = faceMatches.getJSONObject(i);
                                    String similarity = match.getString("similarity");
                                    JSONObject face = match.getJSONObject("face");
                                    String confidence = face.getString("confidence");
                                    faceMatchingResultTV.setText("Status: Matched\nConfidence: " + confidence + "\nSimilarity: " + similarity);
                                }

                                if(faceMatches.length() < 1){
                                    faceMatchingResultTV.setText("Status: Unmatched");
                                }
                            } catch (JSONException e) {
                                // No Matches Exist
                                faceMatchingResultTV.setText("Status: Unmatched");
                            }
                            finally {
                                disableLoadingPanel();
                                showFaceMatchingDetails();
                                hasCompletedFaceMatching = true;
                            }
                        }

                        //Handle any possible failures of the Face Matching API call
                        @Override
                        public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                            System.out.print(errorResponse);
                        }

                        @Override
                        public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONArray errorResponse) {
                            System.out.print(errorResponse);
                        }

                        @Override
                        public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                            System.out.print(responseString);
                        }
                    });


                } catch (JSONException e) {
                    e.printStackTrace();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            //Handle any possible failures of "Client Authorization"
            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                System.out.print(errorResponse);
            }
        });
    }

    //Start Liveness Authorization
    private void performLivenessTesting() {
        //Set Authorization Variables
        RequestParams params = new RequestParams();
        params.put("grant_type", "client_credentials");
        params.put("client_id", "postman");
        params.put("client_secret", "388D45FA-B36B-4988-1234-B187D329C207");

        //Call Authorization to get a bearer token. In reality, this will be either called as soon as the client requests to perform a liveness Testing, or during the loading of the VerID SDK.
        //Response also includes "expires_in" which indicates in how many seconds the token will expire.
        iKYC_Client.clientAuthorization(params, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, final JSONObject response) {
                //Call was successful
                System.out.print(response);
                try {
                    //Get Token
                    final String access_token = response.getString("access_token");

                    final int[] livenessRecordId = new int[1];

                    //Set the Json Body Request to authorize liveness testing
                    JSONObject jsonParams = new JSONObject();
                    StringEntity jsonParamsString = null;

                    try {
                        jsonParams.put("deviceOS", "Sample OS");
                        jsonParams.put("verIdSDK", "Sample SDK");
                        jsonParams.put("deviceMake", "Sample Make");
                        jsonParams.put("deviceModel", "Sample Model");
                        jsonParams.put("sdkVersion", "Sample SDK Version");

                        jsonParamsString = new StringEntity(jsonParams.toString());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }

                    //Request for Liveness Testing. In reality this will be called as soon as the user requests for a liveness testing.
                    iKYC_Client.livenessRequest(getApplicationContext(), access_token, jsonParamsString, new JsonHttpResponseHandler() {

                        @Override
                        public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                            //Call was successful
                            System.out.print(response);
                            try {
                                livenessRecordId[0] = response.getInt("livenessRecordId");

                                //Set the request json body for the updating of the record
                                JSONObject jsonParams = new JSONObject();
                                StringEntity jsonParamsString = null;

                                try {
                                    jsonParams.put("livenessRecordId", livenessRecordId[0]);
                                    jsonParams.put("status", "Success");
                                    jsonParams.put("reason", "Everything looks Ok!");

                                    jsonParamsString = new StringEntity(jsonParams.toString());
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                } catch (UnsupportedEncodingException e) {
                                    e.printStackTrace();
                                }

                                //Update the liveness record. In reality, this will not be called in here. Instead it should be placed on success/cancel/fail of the liveness SDK.
                                iKYC_Client.updateLivenessRecord(getApplicationContext(), access_token, jsonParamsString, new JsonHttpResponseHandler() {

                                    @Override
                                    public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                                        System.out.print(response);
                                    }

                                    //Handle any possible failures of "Update Liveness Record"
                                    @Override
                                    public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                                        System.out.print(errorResponse);
                                    }

                                });

                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }

                        //Handle any possible failures of "Request for Liveness Testing"
                        @Override
                        public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                            System.out.print(errorResponse);
                        }
                    });

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            //Handle any possible failures of "Client Authorization"
            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                System.out.print(errorResponse);
            }
        });
    }

    //Bitmap Helper Methods
    public static Bitmap handleSamplingAndRotationBitmap(Context context, Uri selectedImage)
            throws IOException {
        int MAX_HEIGHT = 1024;
        int MAX_WIDTH = 1024;

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        InputStream imageStream = context.getContentResolver().openInputStream(selectedImage);
        BitmapFactory.decodeStream(imageStream, null, options);
        imageStream.close();

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, MAX_WIDTH, MAX_HEIGHT);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        imageStream = context.getContentResolver().openInputStream(selectedImage);
        Bitmap img = BitmapFactory.decodeStream(imageStream, null, options);

        img = rotateImageIfRequired(context, img, selectedImage);
        return img;
    }

    private static int calculateInSampleSize(BitmapFactory.Options options,
                                             int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            // Calculate ratios of height and width to requested height and width
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);

            // Choose the smallest ratio as inSampleSize value, this will guarantee a final image
            // with both dimensions larger than or equal to the requested height and width.
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;

            // This offers some additional logic in case the image has a strange
            // aspect ratio. For example, a panorama may have a much larger
            // width than height. In these cases the total pixels might still
            // end up being too large to fit comfortably in memory, so we should
            // be more aggressive with sample down the image (=larger inSampleSize).

            final float totalPixels = width * height;

            // Anything more than 2x the requested pixels we'll sample down further
            final float totalReqPixelsCap = reqWidth * reqHeight * 2;

            while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
                inSampleSize++;
            }
        }
        return inSampleSize;
    }

    private static Bitmap rotateImageIfRequired(Context context, Bitmap img, Uri selectedImage) throws IOException {

        InputStream input = context.getContentResolver().openInputStream(selectedImage);
        ExifInterface ei;
        if (Build.VERSION.SDK_INT > 23)
            ei = new ExifInterface(input);
        else
            ei = new ExifInterface(selectedImage.getPath());

        int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return rotateImage(img, 90);
            case ExifInterface.ORIENTATION_ROTATE_180:
                return rotateImage(img, 180);
            case ExifInterface.ORIENTATION_ROTATE_270:
                return rotateImage(img, 270);
            default:
                return img;
        }
    }

    private static Bitmap rotateImage(Bitmap img, int degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        Bitmap rotatedImg = Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);
        img.recycle();
        return rotatedImg;
    }

    //Loading Panel Helper Methods
    private void enableLoadingPanel(){
        progressBarHolder = (FrameLayout) findViewById(R.id.progressBarHolder);
        progressBarHolder.bringToFront();
        inAnimation = new AlphaAnimation(0f, 1f);
        inAnimation.setDuration(200);
        progressBarHolder.setAnimation(inAnimation);
        progressBarHolder.setVisibility(View.VISIBLE);

        //Disable Controls here
        livenessTestBtn.setEnabled(false);
        scanDocumentBtn.setEnabled(false);
        faceMatchingBtn.setEnabled(false);
    }

    private void disableLoadingPanel(){
        progressBarHolder = (FrameLayout) findViewById(R.id.progressBarHolder);
        outAnimation = new AlphaAnimation(1f, 0f);
        outAnimation.setDuration(200);
        progressBarHolder.setAnimation(outAnimation);
        progressBarHolder.setVisibility(View.GONE);

        //Enable Controls here
        livenessTestBtn.setEnabled(true);
        scanDocumentBtn.setEnabled(true);
        faceMatchingBtn.setEnabled(true);
    }

    private void hideLivenessDetails(){
        livenessTitleTV.setVisibility(View.INVISIBLE);
        livenessImageIV.setVisibility(View.INVISIBLE);
        livenessTestResultTV.setVisibility(View.INVISIBLE);
    }

    private void showLivenessDetails(){
        livenessTitleTV.setVisibility(View.VISIBLE);
        livenessImageIV.setVisibility(View.VISIBLE);
        livenessTestResultTV.setVisibility(View.VISIBLE);
        logoIV.setVisibility(View.INVISIBLE);
    }

    private void hideDocumentDetails(){
        documentTitleTV.setVisibility(View.INVISIBLE);
        documentImageIV.setVisibility(View.INVISIBLE);
    }

    private void showDocumentDetails(){
        documentTitleTV.setVisibility(View.VISIBLE);
        documentImageIV.setVisibility(View.VISIBLE);
        logoIV.setVisibility(View.INVISIBLE);
    }

    private void hideFaceMatchingDetails(){
        faceMatchingResultTV.setVisibility(View.INVISIBLE);
    }

    private void showFaceMatchingDetails(){
        faceMatchingResultTV.setVisibility(View.VISIBLE);
    }

}
