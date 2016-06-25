package org.dekoboko.datagatherprototype;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;

import org.dekoboko.datagatherprototype.model.DataPoint;
import org.dekoboko.datagatherprototype.model.DataPoints;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.BufferedSource;
import okio.Okio;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_CODE_READ_LOCATION = 42;
    private static final int REQUEST_ENABLE_BT = 43;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    private Location currentLocation;

    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private JsonAdapter<DataPoints> dataPointsJsonAdapter;
    private OkHttpClient okHttpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "start");
        Moshi build = new Moshi.Builder().build();
        dataPointsJsonAdapter = build.adapter(DataPoints.class);
        okHttpClient = new OkHttpClient.Builder().build();
        createLocationListener();
        connectToBluetooth();
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private ConnectedThread mmConnectedThread;
        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;
            mmDevice = device;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                // MY_UUID is the app's UUID string, also used by the server code
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
                //device.createInsecureRfcommSocketToServiceRecord()
            } catch (IOException e) {
                Log.d(TAG, e.getMessage());
            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            //mBluetoothAdapter.cancelDiscovery();

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exceptio
                Log.d(TAG, "Connecting to device");
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                Log.d(TAG, connectException.getMessage());
                Log.d(TAG, "Connection failed");
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                }
                return;
            }

            // Do work to manage the connection (in a separate thread
            mmConnectedThread = new ConnectedThread(mmSocket);
            mmConnectedThread.start();
        }

        /**
         * Will cancel an in-progress connection, and close the socket
         */
        public void cancel() {
            try {
                mmSocket.close();
                mmConnectedThread.cancel();
            } catch (IOException e) {
            }
        }
    }

    class SendThread extends Thread {
        private DataPoints dataPoints;

        public SendThread(DataPoints dataPoints) {
            this.dataPoints = dataPoints;
        }

        public void run() {
            String jsonDataPoints = dataPointsJsonAdapter.toJson(dataPoints);
            RequestBody body = RequestBody.create(MediaType.parse("application/json"), jsonDataPoints);
            Request request = new Request.Builder()
                    .url("http://someurl.herokuapp.com/api/new").post(body).build();
            //okHttpClient.newCall(request);
            Log.d(TAG, jsonDataPoints);
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

        public void run() {
            BufferedSource buffer1 = Okio.buffer(Okio.source(mmInStream));
            String currentValue = "";
            DataPoints currentValues = new DataPoints();
            while (true) {
                try {
                    // Read from the InputStream
                    byte b = buffer1.readByte();
                    if (b == 10) {
                        String[] split = currentValue.split("\t");
                        if (currentLocation != null) {
                            DataPoint dataPoint = new DataPoint(currentLocation,
                                    Integer.parseInt(split[0]), Integer.parseInt(split[1]));
                            currentValues.dataPoints.add(dataPoint);
                            if (currentValues.dataPoints.size() >= 10) {
                                new SendThread(currentValues).run();
                                currentValues = new DataPoints();
                            }
                        }
                        currentValue = "";
                    } else if (b != 13) {
                        currentValue += (char)b;
                    }

                    // Send the obtained bytes to the UI activity
                    //mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
                    //        .sendToTarget();
                } catch (IOException e) {
                    Log.d(TAG, e.getMessage());
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
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

    private void connectToBluetooth() {
        Log.d(TAG, "Finding devices");
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        BluetoothDevice sensor = null;
        Log.d(TAG, "Found " + pairedDevices.size() + " paired devices.");
        // If there are paired devices
        if (pairedDevices.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {
                Log.d(TAG, device.getName() + "\n" + device.getAddress());
                if (device.getName().equals("HC-06")) {
                    sensor = device;
                    break;
                }
            }
        }
        if (sensor != null) {
            Log.d(TAG, "Connecting");
            ConnectThread connectThread = new ConnectThread(sensor);
            connectThread.start();
        }
    }

    private void createLocationListener() {
        // Acquire a reference to the system Location Manager
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        // Define a listener that responds to location updates
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                currentLocation = location;
                //Log.d(TAG, "accuracy = " + location.getAccuracy());
                //Log.d(TAG, "latitude = " + location.getLatitude());
                //Log.d(TAG, "longitude = " + location.getLongitude());
                //Log.d(TAG, "speed = " + location.getSpeed());
                //Log.d(TAG, "time = " + location.getTime());
                //Log.d(TAG, "provider = " + location.getProvider());
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
                Log.d(TAG, "status changed: " + status);
            }

            public void onProviderEnabled(String provider) {
            }

            public void onProviderDisabled(String provider) {
            }
        };

        // Register the listener with the Location Manager to receive location updates
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSION_CODE_READ_LOCATION);

            Log.d(TAG, "No location permission");
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_CODE_READ_LOCATION: {
                Log.d(TAG, "Location Permission granted");
                createLocationListener();
            }
        }
    }
}
