package com.ispiral.verid;

import android.content.Context;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import cz.msebera.android.httpclient.entity.StringEntity;

public class iKYC_Client {

    //Implementation of iKYC Client endpoints et cetera
    private static final String BASE_URL = "https://demoapi.ikyc.eu";

    private static AsyncHttpClient client = new AsyncHttpClient();

    public static void clientAuthorization(RequestParams params, AsyncHttpResponseHandler responseHandler) {
        client.post(BASE_URL + "/token", params, responseHandler);
    }

    public static void livenessRequest(Context applicationContext, String access_token, StringEntity jsonBody, AsyncHttpResponseHandler responseHandler) {
        client.addHeader("Authorization", "Bearer " + access_token);
        client.post(applicationContext, BASE_URL + "/api/liveness/request", jsonBody, "application/json", responseHandler);
    }

    public static void updateLivenessRecord(Context applicationContext, String access_token, StringEntity jsonBody, AsyncHttpResponseHandler responseHandler) {
        client.addHeader("Authorization", "Bearer " + access_token);
        client.put(applicationContext, BASE_URL + "/api/liveness/updateLivenessRecord", jsonBody, "application/json", responseHandler);
    }

    public static void faceMatchingRequest(String access_token, RequestParams params, AsyncHttpResponseHandler responseHandler) {
        client.addHeader("Authorization", "Bearer " + access_token);
        client.post(BASE_URL + "/api/service/compareFaces", params, responseHandler);
    }

}
