package com.remoteandroid.client;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.graphics.drawable.Drawable;

import java.util.TimerTask;
import java.util.Timer;
import com.couchbase.lite.*;
import com.couchbase.lite.android.AndroidContext;
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.lang.System;
import java.net.URL;



public class MainActivity extends ActionBarActivity {

    private final static String debugName = "Remote Android"; //Name of the application
    public static short userID = 1; //This number specificies the controller or slave, 1 = controller, 2 = slave, selected in settings & login
    public static String emailAddress = "itsmomito@gmail.com"; //This is the email address used in the database to correlate to a channel
    private Database db;
    private MotionEvent me;
    private Replication push;
    private Replication pull;
    private Map<String, Object> screenContent = new HashMap<String, Object>();

    class MyTimerTask extends TimerTask {
        public void run() {
            if (userID == 1) { //Controller
                screenContent.put("type", "screen");
                screenContent.put("motionEvent", me);
                screenContent.put("email", emailAddress);
                screenContent.put("image", Image);
                screenContent.put("time", getSeconds());
                // Load an JPEG attachment from a document into a Drawable:
                Document doc = db.getDocument("Robin");
                Revision rev = doc.getCurrentRevision();
                Attachment att = rev.getAttachment("photo.jpg");
                if (att != null) {
                    InputStream is = att.getContent();
                    Drawable d = Drawable.createFromStream(is, "src name");
                    is.close();
                }
            }
            else { //Slave
                screenContent.put("type", "screen");
                screenContent.put("motionEvent", me);
                screenContent.put("email", emailAddress);
                screenContent.put("image", null);
                screenContent.put("time", getSeconds());
            }



            Document doc = db.createDocument();

            try {
                doc.putProperties(screenContent);
                Log.e(debugName, "Document written to database named " + "Sync_Gateway" + " with ID = " + doc.getId());
            } catch (CouchbaseLiteException e) {
                Log.e(debugName, "Cannot write document to database", e);
            }
            screenContent.clear();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initDB();

        //push.addChangeListener(this);
        //pull.addChangeListener(this);

        push.start();
        pull.start();

        Map<String, Object> connectorContent = new HashMap<String, Object>();

        connectorContent.put("type", "connector");
        connectorContent.put("email", emailAddress);
        connectorContent.put("userid", userID);

        Document doc = db.createDocument();
        // add content to document and write the document to the database
        try {
            doc.putProperties(connectorContent);
            Log.e(debugName, "Document written to database named " + "Sync_Gateway" + " with ID = " + doc.getId());
        } catch (CouchbaseLiteException e) {
            Log.e(debugName, "Cannot write document to database", e);
        }
        com.couchbase.lite.View viewItemsByTime = db.getView(String.format("%s/%s", designDocName, byDateViewName));
        viewItemsByTime.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                Object o = document.get("time");
                if (o != null) {
                    emitter.emit(o, null);
                }
            }
        }, "1.0");

        MyTimerTask yourTask = new MyTimerTask();
        Timer t = new Timer();
        t.scheduleAtFixedRate(yourTask, 0, 100);  // .1 sec interval
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

    public static int getSeconds() {
        return (int)(System.currentTimeMillis()/1000.0 - 1422175000);
    }

    private void initDB () {

        Manager manager;
        try {
            manager = new Manager(new AndroidContext(this), Manager.DEFAULT_OPTIONS);
            Log.e (debugName, "Manager created");
        } catch (IOException e) {
            Log.e(debugName, "Cannot create manager object");
            return;
        }

        Database database;
        try {
            database = manager.getDatabase("gateway_sync");
            Log.e (debugName, "Database created");
        } catch (CouchbaseLiteException e) {
            Log.e(debugName, "Cannot get database");
            return;
        }
        db = database;
        initReplication();
    }

    private void initReplication()  {
        URL url = new URL("http://172.31.103.140:4984");
        Replication push = db.createPushReplication(url);
        Replication pull = db.createPullReplication(url);
        pull.setContinuous(true);
        push.setContinuous(true);
    }

}
