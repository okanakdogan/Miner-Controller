package com.gtu.akdogan.minerinterface;

import android.graphics.Point;
import android.graphics.PointF;
import android.hardware.SensorManager;
import android.media.Image;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Handler;

public class MainActivity extends AppCompatActivity {
    public static final int TIME_CONSTANT = 30;

    private EditText ip_txt;
    private EditText port_txt;
    private TextView state_txtview;
    private TextView azimuth_txtview;
    private TextView roll_txtview;
    private TextView pitch_txtview;
    private Button connect_btn;
    private Button sendMSG_btn;

    //sensor vals
    private SensorFusion mSensorFus;
    private SensorManager mSensorManager;

    private float azimuth;
    private float roll;
    private float pitch;

    private String sAzimuth;
    private String sRoll;
    private String sPitch;

    private Timer sensorTimer;
    private Handler mHandler;

    private boolean conn_state;
    //client values
    private Socket clientSocket;
    private String ip;
    private int port;
    private PrintWriter clientOut;
    private BufferedReader clientIn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //init
        ip_txt = (EditText) findViewById(R.id.ip_edttxt);
        port = 7777;
        //port_txt = (EditText) findViewById(R.id.editText2);
/*
        state_txtview = (TextView) findViewById(R.id.textView4);
        azimuth_txtview = (TextView) findViewById(R.id.azimuth_view);
        roll_txtview = (TextView) findViewById(R.id.roll_view);
        pitch_txtview = (TextView) findViewById(R.id.pitch_view);
*/
        connect_btn = (Button) findViewById(R.id.connect_btn);
        //sendMSG_btn = (Button) findViewById(R.id.button2);

        conn_state = false;

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensorFus = new SensorFusion(getApplicationContext(),mSensorManager);


        sensorTimer = new Timer();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //new Thread( new sensorUpdater()).start();

        //button init

        connect_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ip = ip_txt.getText().toString();
                //port = Integer.parseInt(port_txt.getText().toString());
                new Thread(new ClientThread()).start();
                if (conn_state) {
                    state_txtview.setText("connected");
                }
            }
        });


/*
        sendMSG_btn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (clientSocket != null && clientSocket.isConnected()) {
                    new sendMessageTask().execute("message");
                }
            }
        });*/

    }

    @Override
    protected void onStop() {
        super.onStop();
        mSensorFus.unregisterListeners();
        sensorTimer.cancel();

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        try {
            if(clientSocket!=null){
                clientSocket.close();
                clientSocket=null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorFus.initListeners();
    }

    private class sendMessageTask extends AsyncTask<String, Void, Void> {


        @Override
        protected Void doInBackground(String... params) {
            String msg = "b/msg";
            try {

                PrintWriter out = new PrintWriter(new BufferedWriter(
                        new OutputStreamWriter(clientSocket.getOutputStream())),
                        true);

                System.out.println(msg);
                out.println(msg);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private class sendOrientation extends AsyncTask<String,Void,Void>{

        @Override
        protected Void doInBackground(String... params) {

            if(clientSocket.isConnected() && clientOut !=null){

                //handle sensor data
                PointF p=handleSensorData(pitch,roll);
                //send sensor inf
                //clientOut.println("g/"+sAzimuth + "/" + sRoll +"/" + sPitch );
                clientOut.println("d/"+p.x + "/" + p.y );

            }
            return null;
        }
    }

    class ClientThread implements Runnable {

        @Override

        public void run() {


            try {

                InetAddress ia = InetAddress.getByName(ip);

                if (clientSocket == null) {

                    clientSocket = new Socket(ia, port);

                    if (clientSocket.isConnected()) {

                        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                        PrintWriter out = new PrintWriter(new BufferedWriter(
                                new OutputStreamWriter(clientSocket.getOutputStream())),
                                true);

                        out.println("addP");
                        System.out.println("waiting new port");
                        for(int i=0 ; i<3; ++i){
                            String msg = in.readLine();
                            System.out.println("server response: "+msg);
                            if(msg.charAt(0)=='s'){

                                int newPort = Integer.parseInt(msg.substring(1));

                                clientSocket.close();

                                clientSocket = new Socket(ia,newPort);
                                System.out.println("port" + newPort);
                                if(clientSocket.isConnected()){
                                    clientOut = new PrintWriter(new BufferedWriter(
                                            new OutputStreamWriter(clientSocket.getOutputStream())),
                                            true);
                                    clientIn = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                                    sensorTimer.scheduleAtFixedRate(new sensorUpdater(),1000,TIME_CONSTANT);
                                }
                                conn_state = true;

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        setContentView(R.layout.activity_controler);
                                        setHoldButton();
                                    }
                                });
                                break;
                            }
                        }


                    }
                }
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

    }

    class sensorUpdater extends TimerTask {

        @Override
        public void run() {

                if(mSensorFus!=null){
                    azimuth = mSensorFus.azimuthF;
                    roll = mSensorFus.rollF;
                    pitch = mSensorFus.pitchF;

                    sAzimuth= mSensorFus.azimuth;
                    sRoll = mSensorFus.roll;
                    sPitch = mSensorFus.pitch;

                    Log.d("orient", "a:"+sAzimuth+" r:"+sRoll+" p:"+sPitch);
                    /*
                    int rotation = getWindowManager().getDefaultDisplay().getRotation();
                    System.out.println(rotation);

                    if (rotation == Surface.ROTATION_90) { // Landscape
                        float pitchTemp = pitch;
                        pitch = roll;
                        roll = -pitchTemp;
                        System.out.println("surface rot90");
                    } else if (rotation == Surface.ROTATION_180) { // Reverse Portrait
                        pitch = -pitch;
                        roll = -roll;
                        System.out.println("surface rot180");
                    } else if (rotation == Surface.ROTATION_270) { // Reverse Landscape
                        float pitchTemp = pitch;
                        pitch = -roll;
                        roll = pitchTemp;
                        System.out.println("surface rot270");
                    }

                    DecimalFormat d = (DecimalFormat) NumberFormat.getNumberInstance(Locale.ENGLISH);
                    d.setRoundingMode(RoundingMode.HALF_UP);
                    d.setMaximumFractionDigits(2);
                    d.setMinimumFractionDigits(2);

                    sAzimuth = d.format(azimuth);
                    sRoll = d.format(roll);
                    sPitch = d.format(pitch);*/
/*
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            azimuth_txtview.setText(sAzimuth);
                            roll_txtview.setText(sRoll);
                            pitch_txtview.setText(sPitch);
                        }
                    });*/
                    new sendOrientation().execute();
                }

        }
    }

    private PointF handleSensorData(float roll,float pitch){

        float inner_radius=5f;
        float outter_radius=20f;

        float x=0,y=0;
        if( Math.abs(roll)>5f && Math.abs(roll)<20f){
            x=roll;
        }
        else if(Math.abs(roll)>=20f){
            x=Math.signum(roll)*20f;
        }

        if( Math.abs(pitch)>5f && Math.abs(pitch)<20f){
            y=pitch;
        }
        else if(Math.abs(pitch)>=20f){
            y=Math.signum(pitch)*20f;
        }

        return new PointF(x,y);
    }

    private void setHoldButton(){
        ImageButton holdButton = (ImageButton)findViewById(R.id.hold_btn);

        holdButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction()==MotionEvent.ACTION_DOWN){
                    if (clientSocket != null && clientSocket.isConnected()) {
                        new sendMessageTask().execute("bp/1");
                    }
                    return true;
                }
                else if(event.getAction()==MotionEvent.ACTION_UP){
                    if (clientSocket != null && clientSocket.isConnected()) {
                        new sendMessageTask().execute("br/1");
                    }
                    return true;
                }
                return false;
            }
        });

    }
}
