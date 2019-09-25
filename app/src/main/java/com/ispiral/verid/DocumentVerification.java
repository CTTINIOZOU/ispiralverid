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
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.format.DateFormat;
import android.text.method.ScrollingMovementMethod;
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
import java.util.Date;

import cz.msebera.android.httpclient.Header;

public class DocumentVerification extends AppCompatActivity {
    //Set Request Codes for Activity Results
    static final int REQUEST_CODE_SCAN_FRONT_DOCUMENT = 1;
    static final int REQUEST_CODE_SCAN_BACK_DOCUMENT = 2;
    static final int REQUEST_CODE_WRITE_PERMISSION = 3;
    static final int REQUEST_CODE_CAMERA = 4;

    //Globally Initialize variables that will be used for liveness testing and face matching
    //Path to save scanned document locally
    final String documentImageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/Folder/";

    //URIs of the images
    Uri frontPhotoUri;
    Uri backPhotoUri;

    Bitmap frontImageBitmap;
    Bitmap backImageBitmap;

    //Layout elements
    ImageView frontImageIV;
    ImageView backImageIV;

    ImageView logoIV;

    Button scanFrontBtn;
    Button scanBackBtn;
    Button documentVerificationBtn;

    TextView frontTitleTV;
    TextView backTitleTV;

    TextView jsonResultsTV;

    //Helper Variables to ensure permissions exist
    boolean cameraPermission = false;
    boolean writePermission = false;

    boolean hasCompletedDocumentVerification = false;

    boolean isLoading = false;

    //Variables for Loading Panel
    AlphaAnimation inAnimation;
    AlphaAnimation outAnimation;

    FrameLayout progressBarHolder;

    //Helper Variables for iKYC Client

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_document_verification);

        //Request the permissions for file writing and camera usage
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
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
        frontImageIV = findViewById(R.id.frontImageIV);
        backImageIV = findViewById(R.id.backImageIV);
        logoIV = findViewById(R.id.logoIV);

        scanFrontBtn = findViewById(R.id.scanFrontBtn);
        scanBackBtn = findViewById(R.id.scanBackBtn);
        documentVerificationBtn = findViewById(R.id.documentVerificationBtn);

        frontTitleTV = findViewById(R.id.frontTitleTv);
        backTitleTV = findViewById(R.id.backTitleTv);

        jsonResultsTV = findViewById(R.id.jsonResultTV);
        jsonResultsTV.setMovementMethod(new ScrollingMovementMethod());
        //POINT 1 - Add Listeners

        //Add listener to 'Perform Liveness Test' button click
        scanFrontBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (frontImageBitmap != null) {
                    Toast.makeText(DocumentVerification.this, "You have already performed a front testing. If you wish to perform it again, please reset the application.", Toast.LENGTH_LONG).show();
                } else {
                    startScanFrontDocumentSession();
                }
            }
        });

        //Add listener to 'Scan Document' button click
        scanBackBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (backImageBitmap != null) {
                    Toast.makeText(DocumentVerification.this, "You have already performed a back document scan. If you wish to perform it again, please reset the application.", Toast.LENGTH_LONG).show();
                } else {
                    startScanBackDocumentSession();
                }
            }
        });

        //Add listener to 'Scan Document' button click
        documentVerificationBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (frontImageBitmap == null || backImageBitmap == null) {
                    Toast.makeText(DocumentVerification.this, "Please perform a front and back scans first.", Toast.LENGTH_LONG).show();
                } else {
                    try {
                        if (hasCompletedDocumentVerification != true) {
                            startDocumentVerification();
                        } else {
                            Toast.makeText(DocumentVerification.this, "You have already performed a face matching. If you wish to perform it again, please reset the application.", Toast.LENGTH_LONG).show();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        //Hide elements
        hideDocumentVerificationDetails();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        //POINT 5 - Document Scan Completed. Get Photo
        if (requestCode == REQUEST_CODE_SCAN_FRONT_DOCUMENT && resultCode == RESULT_OK) {
            //Show the image on sample bitmap
            //Show image on sample bitmap
            try {
                Bitmap documentPhotoBitmap = handleSamplingAndRotationBitmap(getApplicationContext(), frontPhotoUri);
                frontImageBitmap = documentPhotoBitmap;
                frontImageIV.setImageBitmap(documentPhotoBitmap);
                showDocumentVerificationDetails();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (requestCode == REQUEST_CODE_SCAN_BACK_DOCUMENT && resultCode == RESULT_OK) {
            Log.d("Scan Document Result", "Success");
            //Show the image on sample bitmap
            //Show image on sample bitmap
            try {
                Bitmap documentPhotoBitmap = handleSamplingAndRotationBitmap(getApplicationContext(), backPhotoUri);
                backImageBitmap = documentPhotoBitmap;
                backImageIV.setImageBitmap(documentPhotoBitmap);
                showDocumentVerificationDetails();
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

    //Start Document Scan
    private void startScanFrontDocumentSession() {
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

            frontPhotoUri = Uri.fromFile(newfile);

            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, frontPhotoUri);

            startActivityForResult(cameraIntent, REQUEST_CODE_SCAN_FRONT_DOCUMENT);

        } else {
            // Do something else on failure
        }

    }

    //Start Document Scan
    private void startScanBackDocumentSession() {
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

            backPhotoUri = Uri.fromFile(newfile);

            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, backPhotoUri);

            startActivityForResult(cameraIntent, REQUEST_CODE_SCAN_BACK_DOCUMENT);

        } else {
            // Do something else on failure
        }

    }

    private void startDocumentVerification() throws IOException {

        enableLoadingPanel();

        //Set Authorization Variables from manifest file
        RequestParams params = new RequestParams();

        try {
            ApplicationInfo ai = getApplicationContext().getPackageManager().getApplicationInfo(getApplicationContext().getPackageName(), PackageManager.GET_META_DATA);
            params.put("grant_type", "client_credentials");
            params.put("client_id", ai.metaData.getString("com.ispiral.client_id"));
            params.put("client_secret", ai.metaData.getString("com.ispiral.client_secret"));
        } catch (PackageManager.NameNotFoundException ex) {
            Toast.makeText(getApplicationContext(), "Error reading manifest file metadata. Ensure you have added client id and secret", Toast.LENGTH_LONG).show();
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
                    Bitmap fileImageBitmap = frontImageBitmap;
                    Bitmap fileSupportImageBitmap = backImageBitmap;

                    ByteArrayOutputStream fileStream = new ByteArrayOutputStream();
                    ByteArrayOutputStream fileSupportStream = new ByteArrayOutputStream();

                    fileImageBitmap.compress(Bitmap.CompressFormat.PNG, 100, fileStream);
                    fileSupportImageBitmap.compress(Bitmap.CompressFormat.PNG, 100, fileSupportStream);

                    byte[] fileImage = fileStream.toByteArray();
                    byte[] fileSupportImage = fileSupportStream.toByteArray();

                    fileImageBitmap.recycle();
                    fileSupportImageBitmap.recycle();

                    //Convert the byte arrays into a file form since API is accepting IFormFile data types for source and target images
                    File fileFile = new File(DocumentVerification.this.getCacheDir(), "File.jpg");
                    fileFile.createNewFile();
                    File fileSupportFile = new File(DocumentVerification.this.getCacheDir(), "FileSupport.jpg");
                    fileSupportFile.createNewFile();

                    OutputStream osSource = new FileOutputStream(fileFile);
                    osSource.write(fileImage);
                    OutputStream osTarget = new FileOutputStream(fileSupportFile);
                    osTarget.write(fileSupportImage);

                    //Set the two files as the parameters for the API Call
                    RequestParams requestParams = new RequestParams();

                    requestParams.put("firstName", "-");
                    requestParams.put("lastName", "-");
                    requestParams.put("country", "-");
                    requestParams.put("documentType", "-");
                    requestParams.put("file", fileFile);
                    requestParams.put("fileSupport", fileSupportFile);

                    //Call compare faces from the API
                    iKYC_Client.documentVerification(access_token, requestParams, new JsonHttpResponseHandler() {
                        @Override
                        public void onSuccess(int statusCode, Header[] headers, final JSONObject response) {
                            //Call was successful
                            //Handle the JSON Response, so as to get match status, similarity and confidence.
                            jsonResultsTV.setText(response.toString());
                            disableLoadingPanel();
                            hasCompletedDocumentVerification = true;
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
    private void enableLoadingPanel() {
        progressBarHolder = (FrameLayout) findViewById(R.id.progressBarHolder);
        progressBarHolder.bringToFront();
        inAnimation = new AlphaAnimation(0f, 1f);
        inAnimation.setDuration(200);
        progressBarHolder.setAnimation(inAnimation);
        progressBarHolder.setVisibility(View.VISIBLE);

        //Disable Controls here
        scanFrontBtn.setEnabled(false);
        scanBackBtn.setEnabled(false);
        documentVerificationBtn.setEnabled(false);
    }

    private void disableLoadingPanel() {
        progressBarHolder = (FrameLayout) findViewById(R.id.progressBarHolder);
        outAnimation = new AlphaAnimation(1f, 0f);
        outAnimation.setDuration(200);
        progressBarHolder.setAnimation(outAnimation);
        progressBarHolder.setVisibility(View.GONE);

        //Enable Controls here
        scanFrontBtn.setEnabled(true);
        scanBackBtn.setEnabled(true);
        documentVerificationBtn.setEnabled(true);
    }

    private void showDocumentVerificationDetails() {
        frontTitleTV.setVisibility(View.VISIBLE);
        frontImageIV.setVisibility(View.VISIBLE);
        backTitleTV.setVisibility(View.VISIBLE);
        backImageIV.setVisibility(View.VISIBLE);
        jsonResultsTV.setVisibility(View.VISIBLE);
        logoIV.setVisibility(View.INVISIBLE);
    }

    private void hideDocumentVerificationDetails() {
        frontTitleTV.setVisibility(View.INVISIBLE);
        frontImageIV.setVisibility(View.INVISIBLE);
        backTitleTV.setVisibility(View.INVISIBLE);
        backImageIV.setVisibility(View.INVISIBLE);
        jsonResultsTV.setVisibility(View.INVISIBLE);
        logoIV.setVisibility(View.VISIBLE);
    }
}
