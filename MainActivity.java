package com.ian.caravanleveler;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.hardware.*;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.Locale;
import org.json.JSONObject;

public class MainActivity extends Activity implements SensorEventListener {
    private SensorManager sm;
    private Sensor accelerometer;
    private float pitch, roll, zeroPitch, zeroRoll;
    private boolean calibrated = false;
    private LevelView levelView;
    private TextView pitchText, rollText, frontText, rearText, leftText, rightText, statusText;
    private EditText wheelbaseEdit, trackEdit;
    private ServerThread server;
    private volatile String remoteIp = "";
    private volatile boolean remoteOnly = false;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sm = (SensorManager)getSystemService(SENSOR_SERVICE);
        accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        buildUi();
        if (accelerometer == null) statusText.setText("No accelerometer found on this phone");
    }

    private TextView tv(String s, int size) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(Color.WHITE); t.setPadding(10,6,10,6); return t;
    }
    private Button btn(String s) { Button b = new Button(this); b.setText(s); return b; }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(16,12,16,8); root.setBackgroundColor(Color.rgb(16,20,24));
        TextView title = tv("CARAVAN LEVELER", 22); title.setGravity(Gravity.CENTER); root.addView(title, new LinearLayout.LayoutParams(-1,60));

        levelView = new LevelView(this); root.addView(levelView, new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout vals = new LinearLayout(this); vals.setOrientation(LinearLayout.VERTICAL);
        pitchText=tv("Pitch: 0.00°",18); rollText=tv("Roll: 0.00°",18); vals.addView(pitchText); vals.addView(rollText);
        LinearLayout corr = new LinearLayout(this); corr.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout c1=new LinearLayout(this); c1.setOrientation(LinearLayout.VERTICAL); frontText=tv("Front 0.0 cm",16); rearText=tv("Rear 0.0 cm",16); c1.addView(frontText); c1.addView(rearText);
        LinearLayout c2=new LinearLayout(this); c2.setOrientation(LinearLayout.VERTICAL); leftText=tv("Left 0.0 cm",16); rightText=tv("Right 0.0 cm",16); c2.addView(leftText); c2.addView(rightText);
        corr.addView(c1,new LinearLayout.LayoutParams(0,-2,1)); corr.addView(c2,new LinearLayout.LayoutParams(0,-2,1)); vals.addView(corr); root.addView(vals,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout buttons = new LinearLayout(this); buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button cal=btn("CALIBRATE"); cal.setOnClickListener(v->{zeroPitch=pitch;zeroRoll=roll;calibrated=true;statusText.setText("Calibrated. Put the phone on the caravan floor in the same position each time.");});
        Button remote=btn("REMOTE"); remote.setOnClickListener(v->showRemoteDialog());
        Button settings=btn("SETUP"); settings.setOnClickListener(v->showSetupDialog());
        buttons.addView(cal,new LinearLayout.LayoutParams(0,-2,1)); buttons.addView(remote,new LinearLayout.LayoutParams(0,-2,1)); buttons.addView(settings,new LinearLayout.LayoutParams(0,-2,1)); root.addView(buttons);
        statusText=tv("Place phone flat on the caravan floor, then calibrate.",14); statusText.setGravity(Gravity.CENTER); root.addView(statusText,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);
    }

    private void showSetupDialog() {
        LinearLayout l=new LinearLayout(this); l.setPadding(30,10,30,10); l.setOrientation(LinearLayout.VERTICAL);
        wheelbaseEdit=new EditText(this); wheelbaseEdit.setHint("Wheelbase / axle to hitch, cm"); wheelbaseEdit.setInputType(2|8192); wheelbaseEdit.setText("400");
        trackEdit=new EditText(this); trackEdit.setHint("Track width, cm"); trackEdit.setInputType(2|8192); trackEdit.setText("230"); l.addView(wheelbaseEdit); l.addView(trackEdit);
        new AlertDialog.Builder(this).setTitle("Caravan dimensions").setMessage("Used to estimate correction distance from tilt angle.").setView(l).setPositiveButton("SAVE",(d,w)->statusText.setText("Dimensions saved for this session.")).setNegativeButton("CANCEL",null).show();
    }

    private void showRemoteDialog() {
        LinearLayout l=new LinearLayout(this); l.setPadding(30,10,30,10); l.setOrientation(LinearLayout.VERTICAL);
        TextView info=tv("HOST: Start a local hotspot or connect both phones to the same Wi-Fi. The host phone stays on the caravan.\n\nREMOTE: On the second phone, enter the host IP and tap Connect.",14); l.addView(info);
        EditText ip=new EditText(this); ip.setHint("Host IP, e.g. 192.168.43.1"); l.addView(ip);
        new AlertDialog.Builder(this).setTitle("Second screen").setView(l).setPositiveButton("START HOST",(d,w)->startServer()).setNeutralButton("CONNECT REMOTE",(d,w)->{remoteIp=ip.getText().toString().trim(); connectRemote();}).setNegativeButton("CANCEL",null).show();
    }

    private void startServer(){ if(server!=null)return; server=new ServerThread(); server.start(); statusText.setText("Remote host running on TCP port 45678. IP: "+localIp()); }
    private String localIp(){ try { for(Enumeration<NetworkInterface> en=NetworkInterface.getNetworkInterfaces();en.hasMoreElements();){NetworkInterface ni=en.nextElement();for(Enumeration<InetAddress> a=ni.getInetAddresses();a.hasMoreElements();){InetAddress x=a.nextElement();if(!x.isLoopbackAddress()&&x instanceof Inet4Address)return x.getHostAddress();}}}catch(Exception ignored){} return "unknown"; }
    private void connectRemote(){ if(remoteIp.isEmpty())return; remoteOnly=true; statusText.setText("Connecting to "+remoteIp+":45678..."); new Thread(()->{try{Socket s=new Socket();s.connect(new InetSocketAddress(remoteIp,45678),3000);BufferedReader r=new BufferedReader(new InputStreamReader(s.getInputStream()));String line; while((line=r.readLine())!=null){JSONObject j=new JSONObject(line); final double p=j.getDouble("pitch"),ro=j.getDouble("roll"); handler.post(()->setRemoteValues((float)p,(float)ro));}}catch(Exception e){handler.post(()->statusText.setText("Remote connection failed: "+e.getMessage()));}}).start(); }
    private void setRemoteValues(float p,float r){pitch=p;roll=r;updateUi(false);statusText.setText("REMOTE DISPLAY");}

    @Override public void onResume(){super.onResume();if(accelerometer!=null)sm.registerListener(this,accelerometer,SensorManager.SENSOR_DELAY_GAME);}
    @Override public void onPause(){super.onPause();sm.unregisterListener(this);}
    @Override public void onSensorChanged(SensorEvent e){if(remoteOnly)return;if(e.sensor.getType()!=Sensor.TYPE_ACCELEROMETER)return;float x=e.values[0],y=e.values[1],z=e.values[2];float p=(float)Math.toDegrees(Math.atan2(-x,Math.sqrt(y*y+z*z)));float r=(float)Math.toDegrees(Math.atan2(y,z));pitch=p-(calibrated?zeroPitch:0);roll=r-(calibrated?zeroRoll:0);updateUi(true);}
    @Override public void onAccuracyChanged(Sensor s,int a){}

    private void updateUi(boolean broadcast){pitchText.setText(String.format(Locale.UK,"Pitch: %.2f°",pitch));rollText.setText(String.format(Locale.UK,"Roll: %.2f°",roll));
        double wb=400, tr=230; try{wb=Double.parseDouble(wheelbaseEdit==null?"400":wheelbaseEdit.getText().toString());tr=Double.parseDouble(trackEdit==null?"230":trackEdit.getText().toString());}catch(Exception ignored){}
        double front=Math.max(0,Math.tan(Math.toRadians(Math.abs(pitch)))*wb);double rear=front;double side=Math.max(0,Math.tan(Math.toRadians(Math.abs(roll)))*tr); String dirP=pitch>0?"Rear":"Front";String dirR=roll>0?"Left":"Right";
        frontText.setText(String.format(Locale.UK,"Front %s %.1f cm",dirP.equals("Front")?"raise":"lower",dirP.equals("Front")?front:0));rearText.setText(String.format(Locale.UK,"Rear %s %.1f cm",dirP.equals("Rear")?"raise":"lower",dirP.equals("Rear")?rear:0));leftText.setText(String.format(Locale.UK,"Left %s %.1f cm",dirR.equals("Left")?"raise":"lower",dirR.equals("Left")?side:0));rightText.setText(String.format(Locale.UK,"Right %s %.1f cm",dirR.equals("Right")?"raise":"lower",dirR.equals("Right")?side:0));levelView.invalidate();
        if(broadcast&&server!=null)server.send(pitch,roll);
    }

    private class ServerThread extends Thread { ServerSocket ss; final java.util.List<PrintWriter> clients=new java.util.concurrent.CopyOnWriteArrayList<>(); public void run(){try{ss=new ServerSocket(45678);while(true){Socket s=ss.accept();PrintWriter out=new PrintWriter(new BufferedWriter(new OutputStreamWriter(s.getOutputStream())),true);clients.add(out);}}catch(Exception ignored){}} void send(float p,float r){String q=String.format(Locale.US,"{\"pitch\":%.3f,\"roll\":%.3f}\n",p,r);for(PrintWriter w:clients){w.print(q);w.flush();}}}

    private class LevelView extends View { Paint p=new Paint(1); public LevelView(Context c){super(c);p.setTypeface(Typeface.DEFAULT);} protected void onDraw(Canvas c){super.onDraw(c);float cx=getWidth()/2f,cy=getHeight()/2f;float rad=Math.min(getWidth(),getHeight())*.34f;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setColor(Color.WHITE);c.drawCircle(cx,cy,rad,p);p.setStrokeWidth(2);c.drawLine(cx-rad,cy,cx+rad,cy,p);c.drawLine(cx,cy-rad,cx,cy+rad,p);p.setStyle(Paint.Style.FILL);p.setColor(Math.abs(pitch)<0.5&&Math.abs(roll)<0.5?Color.GREEN:Color.rgb(255,180,0));float bx=cx+(roll/10f)*rad,by=cy+(pitch/10f)*rad;c.drawCircle(bx,by,22,p);p.setColor(Color.WHITE);p.setTextSize(24);p.setTextAlign(Paint.Align.CENTER);c.drawText(Math.abs(pitch)<0.5&&Math.abs(roll)<0.5?"LEVEL":"ADJUST",cx,cy+rad+45,p);}}
}
