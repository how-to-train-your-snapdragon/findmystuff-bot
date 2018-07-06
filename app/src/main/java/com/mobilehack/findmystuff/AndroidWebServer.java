package com.mobilehack.findmystuff;

import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class AndroidWebServer extends NanoHTTPD {

    public AndroidWebServer(int port) {
        super(port);
    }

    public AndroidWebServer(String hostname, int port) {
        super(hostname, port);
    }

    @Override
    public Response serve(IHTTPSession session) {

        String msg = "";
        Map<String, String> parms = session.getParms();
        if (parms.get("message") != null) {
            msg += "Got the message";
        } else {
            msg += "Didn't get message";
        }
        return newFixedLengthResponse(msg);
    }


}