package com.eve.atcf;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.mikepenz.iconics.IconicsColor;
import com.mikepenz.iconics.IconicsDrawable;
import com.mikepenz.iconics.IconicsSize;
import com.mikepenz.iconics.typeface.library.fontawesome.FontAwesome;

import org.json.JSONException;
import org.osmdroid.config.Configuration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import fr.quentinklein.slt.LocationTracker;
import fr.quentinklein.slt.TrackerSettings;

import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.toDegrees;
import static java.lang.Math.toRadians;

public class MainActivity extends AppCompatActivity {
    public Location getDestination() {
        return destination;
    }

    private static Context context;

    private Compass compass;

    private Location wayPoint = new Location("wayPoint");
    private Location destination = new Location("destination");
    private Location recentDestination = new Location("recentDestination");
    private double distance = 0;

    private LocationTracker tracker;
    private Location recentLocation = new Location("location");

    private static final String TAG = "CompassActivity";

    private ImageView arrowView;
    private TextView distLabel;  // SOTW is for "side of the world"

    private float currentAzimuth;
    private float[] currentQuadrantAzimuths;

    private List<UserHistory> userHistories;
    private boolean recordSession;

    private Graph graph;
    private List<Node> jsonNodeList;
    private int previousJSONLocation;

    private List<Node> allVisitedNodes;
    private Socket mSocket;

    private ArrayList<Location> locationList;

    private ArrayList<Location> oldLocationList;
    private ArrayList<Location> noAccuracyLocationList;
    private ArrayList<Location> inaccurateLocationList;
    private ArrayList<Location> kalmanNGLocationList;

    private float currentSpeed = 0.0f; // meters/second

    private KalmanLatLong kalmanFilter;
    private long runStartTimeInMillis;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //hide notificationbar
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); // Make to run your application only in portrait mode


        setContentView(R.layout.activity_main);
        MainActivity.context = getApplicationContext();

        //get Destination
        try {
            Bundle b = getIntent().getExtras();

            wayPoint.setLatitude(b.getDouble("latWayPoint"));
            wayPoint.setLongitude(b.getDouble("lngWayPoint"));
            destination.setLatitude(b.getDouble("latDestination"));
            destination.setLongitude(b.getDouble("lngDestination"));
            recentLocation.setLatitude(b.getDouble("latLocation"));
            recentLocation.setLongitude(b.getDouble("lngLocation"));


            recentDestination = wayPoint;
            //if destination is in range, navigation can be automatically finished
            if (testIfArrivedAtDestination(recentLocation, destination)) {
                buildAlertForFinishedNavigation();
            }
        } catch (NullPointerException e) {
            finish();
        }

        try {
            mSocket = IO.socket("https://TRACKERURL/");
        } catch (URISyntaxException e) {
        }
        mSocket.connect();

        arrowView = findViewById(R.id.compass);
        distLabel = findViewById(R.id.distance);

        locationList = new ArrayList<>();
        noAccuracyLocationList = new ArrayList<>();
        oldLocationList = new ArrayList<>();
        inaccurateLocationList = new ArrayList<>();
        kalmanNGLocationList = new ArrayList<>();
        kalmanFilter = new KalmanLatLong(3);

        //update userPosition
        setLocation();


        currentQuadrantAzimuths = new float[4];
        compass = new Compass(this, this);


        jsonNodeList = new ArrayList<Node>();
        try {
            graph = new Graph(this);
            jsonNodeList = graph.getNodes();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        allVisitedNodes = new ArrayList<>();
        previousJSONLocation = -1; //random value which cant be triggered by map json
        userHistories = new ArrayList<>();
        recordSession = false;

        setupCompass();//with recentlocation from osmactivity


        //load/initialize the osmdroid configuration, this can be done
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        //setting this before the layout is inflated is a good idea
        //it 'should' ensure that the map has a writable location for the map cache, even without permissions
        //if no tiles are displayed, you can try overriding the cache path using Configuration.getInstance().setCachePath
        //see also StorageUtils
        //note, the load method also sets the HTTP User Agent to your application's package name, abusing osm's tile servers will get you banned based on this string


        String folder_main = "Riding my Bike";
        File f = new File(Environment.getExternalStorageDirectory(), folder_main);
        if (!f.exists()) {
            f.mkdirs();
        }

        FloatingActionButton startRecording = findViewById(R.id.startRecording);

        startRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recordSession = true;
                saveUserHistory(recentLocation);
                startRecording.hide();
            }
        });
    }

    public void statusCheck() {
        final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            buildAlertMessageNoGps();
        }
    }

    //https://medium.com/@smjaejin/easily-reading-and-writing-files-to-internal-storage-with-gson-bdba821ca7de
    private void writeToFile() {
        if (recordSession) {
            saveUserHistory(recentLocation);//save end
            Gson gson = new Gson();
            String yourObjectJson = gson.toJson(userHistories);
            try {
                //Get your FilePath and use it to create your File
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MM_dd_HH-mm-ss");
                String filename = sdf.format(new Date()) + ".json";
                String yourFilePath = Environment.getExternalStorageDirectory() + "/" + "Riding my Bike" + "/" + filename;
                File yourFile = new File(yourFilePath);
                //Create your FileOutputStream, yourFile is part of the constructor
                FileOutputStream fileOutputStream = new FileOutputStream(yourFile);
                //Convert your JSON String to Bytes and write() it
                fileOutputStream.write(yourObjectJson.getBytes());
                //Finally flush and close your FileOutputStream
                fileOutputStream.flush();
                fileOutputStream.close();

//            Toast.makeText(this, "saved to " + Environment.getExternalStorageDirectory() + "/" + "Riding my Bike" + "/" + filename, Toast.LENGTH_LONG).show();
            } catch (IOException e) {
                Log.e("Exception", "File write failed: " + e.toString());
            }
        }
    }

    private void buildAlertForArrivingAtWayPoint() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Waypoint erreicht!");
        builder.setIcon(new IconicsDrawable(context)
                .icon(FontAwesome.Icon.faw_star1)
                .color(IconicsColor.colorInt(Color.GREEN))
                .size(IconicsSize.dp(24)));
        builder.setMessage("Du hast den Waypoint erreicht! \nBitte setze deine Fahrt zum Ziel fort!\nDer Kompass sowie die Distanz werden nun auf das Ziel angepasst.")
                .setCancelable(false)
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    private void buildAlertForFinishedNavigation() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Ziel erreicht!");
        builder.setIcon(new IconicsDrawable(context)
                .icon(FontAwesome.Icon.faw_laugh_beam)
                .color(IconicsColor.colorInt(Color.GREEN))
                .size(IconicsSize.dp(24)));
        builder.setMessage("Du hast das Ziel erreicht! \nHoffentlich hattest du eine gute Fahrt!\nFalls notwendig warte bitte, bis die Experimentierenden erscheinen.")
                .setCancelable(false)
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        finish();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    private void buildAlertForExitingNavigation() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Navigation abbrechen");
        builder.setIcon(new IconicsDrawable(context)
                .icon(FontAwesome.Icon.faw_compass)
                .color(IconicsColor.colorInt(Color.RED))
                .size(IconicsSize.dp(24)));
        builder.setMessage("Du bist im Begriff die Navigation zu beenden und somit den Versuch abzubrechen. \nMöchtest du fortfahren?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        writeToFile();
                        finish();
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        dialog.cancel();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }


    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("GPS deaktiviert!");
        builder.setIcon(new IconicsDrawable(context)
                .icon(FontAwesome.Icon.faw_map_marked)
                .color(IconicsColor.colorInt(Color.RED))
                .size(IconicsSize.dp(24)));
        builder.setMessage("Dein GPS scheint deaktiviert zu sein. Diese App benötigt GPS, um zu funktionieren. \nMöchtest du GPS aktivieren?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        dialog.cancel();
                        statusCheck();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    public static Context getAppContext() {
        return MainActivity.context;
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "start compass");
        compass.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        compass.stop();
//        map.onPause();  //needed for compass, my location overlays, v6.0.0 and up
        mSocket.disconnect();
//        writeToFile();

        if (tracker != null) {
            tracker.stopListening();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        statusCheck(); //check if gps is still enabled
        compass.start();
        mSocket.connect();

        if (tracker != null) {
            if (ContextCompat.checkSelfPermission(getAppContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(getAppContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // You need to ask the user to enable the permissions TODO
            } else {
                tracker.startListening();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "stop compass");
        compass.stop();
        mSocket.disconnect();

        if (tracker != null) {
            tracker.stopListening();
        }

        writeToFile();
    }

    @Override
    public void onBackPressed() {
        buildAlertForExitingNavigation();
    }

    private void setupCompass() {
        Compass.CompassListener cl = getCompassListener();
        compass.setListener(cl);
        compass.setLocation(recentLocation);
    }

    private void adjustArrow(float azimuth) {
        double bearing = computeHeading(recentLocation.getLatitude(), recentLocation.getLongitude(), recentDestination.getLatitude(), recentDestination.getLongitude());
        azimuth -= bearing;

        Animation an = new RotateAnimation(-currentAzimuth, -azimuth,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
                0.5f);

        currentAzimuth = azimuth;

        an.setDuration(500);
        an.setRepeatCount(0);
        an.setFillAfter(true);

        arrowView.startAnimation(an);
    }


    private Compass.CompassListener getCompassListener() {
        return new Compass.CompassListener() {
            @Override
            public void onNewAzimuth(final float azimuth) {
                // UI updates only in UI thread
                // https://stackoverflow.com/q/11140285/444966
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        adjustArrow(azimuth);
                        adjustDistLabel();
//                        adjustQuadrants(azimuth);
                    }
                });
            }
        };
    }

    private void adjustDistLabel() {
        distance = recentLocation.distanceTo(recentDestination);
        int distanceInMeters = Math.toIntExact(Math.round(distance));
        String result = Math.toIntExact(Math.round(distance)) + " m";
        if (distance >= 1000) {
            double res = distanceInMeters;
            DecimalFormat value = new DecimalFormat("#.#");
            result = value.format(res / 1000) + " km";
        }
        distLabel.setText(result);
    }

    public void setLocation() {
        if (ContextCompat.checkSelfPermission(getAppContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(getAppContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // You need to ask the user to enable the permissions TODO
        } else {
            TrackerSettings settings =
                    new TrackerSettings()
                            .setUseGPS(true)
                            .setUseNetwork(true)
                            .setUsePassive(true)
                            .setTimeBetweenUpdates(500)
                            .setMetersBetweenUpdates(1);
            tracker = new LocationTracker(getApplicationContext(), settings) {
                @Override
                public void onLocationFound(Location location) {
                    if (filterAndAddLocation(location)) {
                        //test whether user arrived at recentdestination & if recentDestination is the same as the last destination
                        if (!testIfArrivedAtDestination(location, destination)) {

                            //just trigger waypoint function if recentdestination isnt the final destination
                            if(!testIfInRange(recentDestination, destination)) {
                                if (testIfArrivedAtDestination(location, wayPoint)) {
                                    recentDestination = destination;
                                    buildAlertForArrivingAtWayPoint();
                                }
                            }
                            recentLocation = location;
                            adjustDistLabel();
                            setupCompass();

                            startQuadrantTriggerFunction(location);
//                            centerMapToUsersPosition(location);

                            sendLiveLocation(location);
                        } else {
                            writeToFile();
                            buildAlertForFinishedNavigation();
                        }
                    }
                }

                @Override
                public void onTimeout() {

                }
            };
            tracker.startListening();
        }
    }

    private boolean filterAndAddLocation(Location location) {

        long age = getLocationAge(location);

        if (age > 10 * 1000) { //more than 10 seconds
            Log.d(TAG, "Location is old");
            oldLocationList.add(location);
            return false;
        }

        if (location.getAccuracy() <= 0) {
            Log.d(TAG, "Latitidue and longitude values are invalid.");
            noAccuracyLocationList.add(location);
            return false;
        }

        //setAccuracy(newLocation.getAccuracy());
        float horizontalAccuracy = location.getAccuracy();
        if (horizontalAccuracy > 10) {
            Log.d(TAG, "Accuracy is too low.");
            inaccurateLocationList.add(location);
            return false;
        }


        /* Kalman Filter */
        float Qvalue;

        long locationTimeInMillis = (long) (location.getElapsedRealtimeNanos() / 1000000);
        long elapsedTimeInMillis = locationTimeInMillis - runStartTimeInMillis;

        if (currentSpeed == 0.0f) {
            Qvalue = 3.0f; //3 meters per second
        } else {
            Qvalue = currentSpeed; // meters per second
        }

        kalmanFilter.Process(location.getLatitude(), location.getLongitude(), location.getAccuracy(), elapsedTimeInMillis, Qvalue);
        double predictedLat = kalmanFilter.get_lat();
        double predictedLng = kalmanFilter.get_lng();

        Location predictedLocation = new Location("");//provider name is unecessary
        predictedLocation.setLatitude(predictedLat);//your coords of course
        predictedLocation.setLongitude(predictedLng);
        float predictedDeltaInMeters = predictedLocation.distanceTo(location);

        if (predictedDeltaInMeters > 60) {
            Log.d(TAG, "Kalman Filter detects mal GPS, we should probably remove this from track");
            kalmanFilter.consecutiveRejectCount += 1;

            if (kalmanFilter.consecutiveRejectCount > 3) {
                kalmanFilter = new KalmanLatLong(3); //reset Kalman Filter if it rejects more than 3 times in raw.
            }

            kalmanNGLocationList.add(location);
            return false;
        } else {
            kalmanFilter.consecutiveRejectCount = 0;
        }

        /* Notifiy predicted location to UI */
        Intent intent = new Intent("PredictLocation");
        intent.putExtra("location", predictedLocation);
        LocalBroadcastManager.getInstance(this.getApplication()).sendBroadcast(intent);

        Log.d(TAG, "Location quality is good enough.");
        currentSpeed = location.getSpeed();
        locationList.add(location);


        return true;
    }

    @SuppressLint("NewApi")
    private long getLocationAge(Location newLocation) {
        long locationAge;
        if (android.os.Build.VERSION.SDK_INT >= 17) {
            long currentTimeInMilli = (long) (SystemClock.elapsedRealtimeNanos() / 1000000);
            long locationTimeInMilli = (long) (newLocation.getElapsedRealtimeNanos() / 1000000);
            locationAge = currentTimeInMilli - locationTimeInMilli;
        } else {
            locationAge = System.currentTimeMillis() - newLocation.getTime();
        }
        return locationAge;
    }

    public void sendLiveLocation(Location location) {
        mSocket.emit("chat message", location.getLatitude() + "," + location.getLongitude() + "," + currentAzimuth);
    }


    public void startQuadrantTriggerFunction(Location location) {

        //test if any location from json array in Graph is in range of recent location
        Location jsonDestination = new Location("jsonDestination");
        for (Node n : jsonNodeList) {
            jsonDestination.setLatitude(n.getLat());
            jsonDestination.setLongitude(n.getLng());

            if (checkIfLocationIsInRange(location, jsonDestination, 30)) {
                System.out.println(n.name + " is in range of Location, lat: " + jsonDestination.getLatitude() + "long " + jsonDestination.getLongitude());
                //dont run code again if location hasn't changed
                if (previousJSONLocation != jsonNodeList.indexOf(n)) {
                    previousJSONLocation = jsonNodeList.indexOf(n);
                    //check if user turnt  => this means deadends and slow paths arent on his way => behind user (can be shown in other direction)
                    if (!allVisitedNodes.contains(n)) {
                        allVisitedNodes.add(n);
                    }
                }
                break;//just check the next Location which is nearby
            }
        }
        saveUserHistory(recentLocation);
    }


    public Boolean checkIfLocationIsInRange(Location location, Location recentJSONDestination, int radius) {
        double distance = location.distanceTo(recentJSONDestination);
        return distance <= radius;
    }


    /**
     * https://github.com/osmdroid/osmdroid/blob/master/osmdroid-geopackage/src/main/java/org/osmdroid/gpkg/overlay/features/SphericalUtil.java
     * Returns the heading from one LatLng to another LatLng. Headings are
     * expressed in degrees clockwise from North within the range [-180,180).
     *
     * @return The heading in degrees clockwise from north.
     */
    public static double computeHeading(double startLat, double startLng, double endLat, double endLng) {
        // http://williams.best.vwh.net/avform.htm#Crs
        double fromLat = toRadians(startLat);
        double fromLng = toRadians(startLng);
        double toLat = toRadians(endLat);
        double toLng = toRadians(endLng);
        double dLng = toLng - fromLng;
        double heading = atan2(
                sin(dLng) * cos(toLat),
                cos(fromLat) * sin(toLat) - sin(fromLat) * cos(toLat) * cos(dLng));
        return wrap(toDegrees(heading), -180, 180);
    }

    /**
     * Wraps the given value into the inclusive-exclusive interval between min and max.
     *
     * @param n   The value to wrap.
     * @param min The minimum.
     * @param max The maximum.
     */
    static double wrap(double n, double min, double max) {
        return (n >= min && n < max) ? n : (mod(n - min, max - min) + min);
    }

    /**
     * Returns the non-negative remainder of x / m.
     *
     * @param x The operand.
     * @param m The modulus.
     */
    static double mod(double x, double m) {
        return ((x % m) + m) % m;
    }

    public Boolean testIfInRange(Location location, Location dest) {
        double distance = location.distanceTo(dest);
        return distance <= 4;
    }

    public Boolean testIfArrivedAtDestination(Location location, Location dest) {
        double distance = location.distanceTo(dest);
        return distance <= 25;
    }

    public void saveUserHistory(Location location) {
        if (recordSession) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd_HH:mm:ss");
            String date = sdf.format(new Date());
            userHistories.add(new UserHistory(date, location.getLatitude(), location.getLongitude(), currentAzimuth, distance, allVisitedNodes));
        }
    }

}
