package com.mobilehack.findmystuff;


import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;


/**
 * Bluetooth manager to communicate with the bot
 * @author Thomas Binu
 * @author Anitha Ramaswamy
 * @author Ashuthosh Giri
 */

public class BlueToothManager {

    private BluetoothAdapter mBTAdapter;
    private ConnectedThread mConnectedThread; // bluetooth background worker thread to send and receive data
    private BluetoothSocket mBTSocket = null; // bi-directional client-to-client data path
    private BluetoothDevice device = null;
    private Set<BluetoothDevice> mPairedDevices;

    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // "random" unique identifier


    public BlueToothManager(AppCompatActivity appCompatActivity) {

        mBTAdapter = BluetoothAdapter.getDefaultAdapter();
        if (ContextCompat.checkSelfPermission(appCompatActivity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(appCompatActivity, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);


        if (mBTAdapter == null) {
            // Device does not support Bluetooth
            Toast.makeText(appCompatActivity, "Bluetooth device not found!", Toast.LENGTH_SHORT).show();
        } else {


            boolean fail = false;
            mPairedDevices = mBTAdapter.getBondedDevices();
            if (mPairedDevices.isEmpty()) {
                Toast.makeText(appCompatActivity, "Please pair the device first", Toast.LENGTH_SHORT).show();
            }

            for (BluetoothDevice iter : mPairedDevices) {
                Log.d("", iter.getName());
                if (iter.getAddress().equals("20:15:06:09:66:44")) {
                    device = iter;
                    break;
                }
            }

            try {
                mBTSocket = createBluetoothSocket(device);
            } catch (IOException e) {
                fail = true;
                Toast.makeText(appCompatActivity, "Socket creation failed", Toast.LENGTH_SHORT).show();
            }

            // Establish the Bluetooth socket connection.
            try {
                mBTSocket.connect();
            } catch (IOException e) {
                try {
                    fail = true;
                    mBTSocket.close();

                } catch (IOException e2) {
                    //insert code to deal with this
                    Toast.makeText(appCompatActivity, "Socket creation failed", Toast.LENGTH_SHORT).show();
                }
            }

            if (!fail) {
                mConnectedThread = new ConnectedThread(mBTSocket);
                mConnectedThread.start();

            }


        }


    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        try {
            final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", UUID.class);
            return (BluetoothSocket) m.invoke(device, BTMODULEUUID);
        } catch (Exception e) {
            Log.e("text", "Could not create Insecure RFComm Connection", e);
        }
        return device.createRfcommSocketToServiceRecord(BTMODULEUUID);
    }

    public void sendMessage(String x) {

        if (mConnectedThread != null) //First check to make sure thread created
        {
            mConnectedThread.write(x);
        }

    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }


        /* Call this from the main activity to send data to the remote device */
        public void write(String input) {
            byte[] bytes = input.getBytes();           //converts entered String into bytes
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
            }
        }
    }
}
