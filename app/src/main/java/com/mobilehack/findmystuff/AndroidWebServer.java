package com.mobilehack.findmystuff;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * Server class to communicate with the client
 * @author Thomas Binu
 * @author Anitha Ramaswamy
 * @author Ashuthosh Giri
 */
public class AndroidWebServer extends NanoHTTPD {

    AppCompatActivity context;
    AndroidServerInterface androidServerInterface;


    public AndroidWebServer(AppCompatActivity context, int port) {
        super(port);
        this.context = context;
    }


    public void setAndroidServerInterface(AndroidServerInterface androidServerInterface) {
        this.androidServerInterface = androidServerInterface;
    }


    public AndroidWebServer(String hostname, int port) {
        super(hostname, port);
    }

    @Override
    public Response serve(IHTTPSession session) {

        Log.d("hello", "world");


        Map<String, List<String>> parms = session.getParameters();
        String msg = "";


        if (parms.get("message") != null) {
            msg += parms.get("message").get(0);
        } else {
            msg += "Didn't get message";
        }

        JSONObject jsonObj = new JSONObject();

        try {
            jsonObj.put("message", msg);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        final String finalMessasge = msg;

        context.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(context, finalMessasge, Toast.LENGTH_SHORT).show();
                androidServerInterface.gotMessage(finalMessasge);
            }
        });

        return newFixedLengthResponse(Response.Status.OK, "text/json",jsonObj.toString());
    }

    interface AndroidServerInterface{

        public void gotMessage(String message);
    }


}