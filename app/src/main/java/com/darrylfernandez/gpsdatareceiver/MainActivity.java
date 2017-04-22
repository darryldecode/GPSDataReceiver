package com.darrylfernandez.gpsdatareceiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;


public class MainActivity extends ActionBarActivity {

    public static boolean listenStarted = false;
    public static String endpoint;
    public static String endpointFinal;

    public static String body;

    // origin data (mobile|kit)
    public static String from;

    // for alert or data?
    public static String intention;

    // alert
    public static String type;
    public static String providerName;

    // data
    public static String deviceId;
    public static String deviceLat;
    public static String deviceLng;

    // device
    public static String deviceSpeed;
    public static String deviceToken;

    public static String TAG = "DD";

    public static class SmsListener extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            if( MainActivity.listenStarted ) {

                if(intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED")) {

                    Bundle bundle = intent.getExtras();
                    SmsMessage[] msgs = null;
                    String msg_from;

                    if (bundle != null){

                        try{
                            Object[] pdus = (Object[]) bundle.get("pdus");
                            msgs = new SmsMessage[pdus.length];
                            for(int i=0; i<msgs.length; i++){
                                msgs[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
                                msg_from = msgs[i].getOriginatingAddress();
                                body = msgs[i].getMessageBody();

                                Toast.makeText(context, "Receiving Data from: " + msg_from, Toast.LENGTH_SHORT).show();

                                // web socket
                                // AsyncSocketConnect webSocketConnection = new AsyncSocketConnect(context);
                                // webSocketConnection.execute();

                                // http req here
                                AsyncSendPost sendPostTask = new AsyncSendPost(context);
                                sendPostTask.execute();
                            }

                        }catch(Exception e) {}
                    }
                }

            }
        }
    }

    public void btnStartListening(View view) {
        EditText inputWebHookUrl = (EditText) findViewById(R.id.inputWebHookUrl);
        Button btnStartListening = (Button) findViewById(R.id.btnStartListening);

        endpoint = inputWebHookUrl.getText().toString();
        listenStarted = true;
        inputWebHookUrl.setEnabled(false);
        btnStartListening.setEnabled(false);
    }


    private static class AsyncSocketConnect extends AsyncTask<Void, Void, Void> {

        Context c;

        public AsyncSocketConnect(Context context) {
            c = context;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Void... params) {

            try {

                final Socket socket = IO.socket("http://"+endpoint);

                socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {

                    @Override
                    public void call(Object... args) {
                        Log.i(TAG, "Web socket successfully connected..");

                        JSONObject obj = new JSONObject();
                        try {
                            obj.put("id", deviceId);
                            obj.put("lat", deviceLat);
                            obj.put("lng", deviceLng);
                            obj.put("speed", deviceSpeed);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        socket.emit("device-connection", obj);
                        socket.disconnect();
                    }

                }).on("event", new Emitter.Listener() {

                    @Override
                    public void call(Object... args) {}

                }).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {

                    @Override
                    public void call(Object... args) {
                        Log.i(TAG, "Web socket disconnected..");
                    }

                });

                socket.connect();

            } catch (URISyntaxException e) {
                Log.i(TAG, e.getMessage());
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            Log.i(TAG, "AsyncSocketConnect::onPostExecute triggered.");
            Toast.makeText(c, "Data successfully forwarded to: " + endpoint, Toast.LENGTH_SHORT).show();
            super.onPostExecute(result);
        }
    }


    private static class AsyncSendPost extends AsyncTask<Void, Void, Void> {

        Context c;

        public AsyncSendPost(Context context) {
            c = context;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Void... params) {

            Uri.Builder urlBuilder = new Uri.Builder();

            urlBuilder.scheme("http")
                    .authority(endpoint)
                    .appendPath("maps-server")
                    .appendPath("public")
                    .appendPath("api")
                    .appendPath("general-data")
                    .appendQueryParameter("d", body);

            endpointFinal = urlBuilder.build().toString();

            URL url;
            HttpURLConnection connection;

            try {

                Log.i(TAG, "Async end point: " + endpointFinal);
                Log.i(TAG, "start connection..");

                url = new URL(endpointFinal);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.connect();

                if(connection.getResponseCode() == HttpURLConnection.HTTP_OK){
                    Log.i(TAG, "Connection successful..");
                    connection.disconnect();
                }
                else {
                    Log.i(TAG, "Connection failed..");
                }

            } catch (Exception e) {
                Log.i(TAG, e.getMessage());
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            Toast.makeText(c, "Data successfully forwarded to: " + endpointFinal, Toast.LENGTH_SHORT).show();
            super.onPostExecute(result);
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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



