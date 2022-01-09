package com.eve.atcfplus;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.mikepenz.iconics.IconicsColor;
import com.mikepenz.iconics.IconicsDrawable;
import com.mikepenz.iconics.IconicsSize;
import com.mikepenz.iconics.typeface.library.fontawesome.FontAwesome;
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic;

import org.json.JSONException;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
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
    private Location previousJSONLocationLocation;
    private boolean visitedRecentJSONLocation;

    private ImageView quadrant0;
    private ImageView quadrant1;
    private ImageView quadrant2;
    private ImageView quadrant3;
    private List<IndicatedStreet> indicatedStreets;
    private int currentIndicator;
    private int lastIndicator;
    private IndicatedStreet recentlyEnteredStreetByUser;
    private double recentlyEnteredStreetDistance;

    private List<Node> allVisitedNodes;
    private MapView map = null;
    private IMapController mapController;
    private View mapCardView;
    private Marker locationMarker;

    private FrameLayout currentLayout;
    private int recentBackgroundColor;
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

        currentIndicator = 0;
        lastIndicator = 0;

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

        quadrant0 = findViewById(R.id.quadrant0);
        quadrant1 = findViewById(R.id.quadrant1);
        quadrant2 = findViewById(R.id.quadrant2);
        quadrant3 = findViewById(R.id.quadrant3);
        indicatedStreets = new ArrayList<>();
        allVisitedNodes = new ArrayList<>();
        previousJSONLocation = -1; //random value which cant be triggered by map json
        visitedRecentJSONLocation = false;
        recentlyEnteredStreetByUser = new IndicatedStreet(null, null, 0);
        recentlyEnteredStreetDistance = -1;

        currentLayout = findViewById(R.id.frameLayout);
        recentBackgroundColor = ResourcesCompat.getColor(getResources(), R.color.defaultBG, null);

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


        map = findViewById(R.id.map);
        final String[] tileURLs = {"MAPSERVERURL"}; //own dark themed map style
        final ITileSource Eve =
                new XYTileSource("Dark theme",
                        16,
                        19,
                        256,
                        ".png",
                        tileURLs,
                        "from OpenMapTiles Map Server");
        map.setTileSource(Eve);

        map.setMultiTouchControls(false);
        map.setBuiltInZoomControls(false);
        mapController = map.getController();
        mapController.setZoom(16.5);
        map.setClickable(false);
        //disable scrolling or interacting with minimap
        map.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    buildAlertForExitingNavigation();
                }
                return true;
            }
        });
        map.setHasTransientState(false);

        mapCardView = findViewById(R.id.mapCardView);

        locationMarker = new Marker(map);
        locationMarker.setIcon(new IconicsDrawable(context)
                .icon(MaterialDesignIconic.Icon.gmi_gps_dot)
                .color(IconicsColor.colorInt(Color.WHITE))
                .size(IconicsSize.dp(20)));
        locationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        map.getOverlays().add(locationMarker);
        locationMarker.setInfoWindow(null);

        centerMapToUsersPosition(recentLocation);

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
//        new Timer().schedule(new TimerTask() {
//            @Override
//            public void run() {
//                writeToFile();
//            }
//        }, 30 * 1000);
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

    public void centerMapToUsersPosition(Location location) {
        updateMarkerOnMinimap(location.getLatitude(), location.getLongitude());

        GeoPoint startPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
        mapController.animateTo(startPoint);
        mapController.setCenter(startPoint);

        updateMinimap();
    }

    public void rotateMinimap(double currentBearing, float currentAzimuth) {
        float t = (360 - (float) currentBearing - currentAzimuth);
        if (t < 0) {
            t += 360;
        }
        if (t > 360) {
            t -= 360;
        }

        map.setMapOrientation(t);

        updateMinimap();
    }

    public void updateMinimap() {
        //        map.getOverlayManager().getTilesOverlay().setColorFilter(TilesOverlay.INVERT_COLORS);
        RoundedBitmapDrawable recentMapRounded = RoundedBitmapDrawableFactory.create(getResources(), getBitmapFromView(map));
        recentMapRounded.setCornerRadius(110);

        mapCardView.setBackground(recentMapRounded);
    }

    /**
     * Get bitmap of a view
     *
     * @param view source view
     * @return generated bitmap object
     */
    public static Bitmap getBitmapFromView(View view) {
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        Bitmap bitmap = Bitmap.createBitmap(view.getMeasuredWidth(), view.getMeasuredHeight(),
                Bitmap.Config.RGB_565);
        return bitmap;
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
        map.onPause();  //needed for compass, my location overlays, v6.0.0 and up
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
        map.onResume(); //needed for compass, my location overlays, v6.0.0 and up
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

        rotateMinimap(bearing, currentAzimuth);

        checkIfCompassTurnsIntoWrongDirection(currentAzimuth);
    }

    public void checkIfCompassTurnsIntoWrongDirection(float currentAzimuth) {
        float value = currentAzimuth;
        if (value < 0) {
            value = value + 360; //azimuth can be negative. This way the value turns out to be positive even so
        }

        if (value > 115 && value < 235) {
            //other indicators do have a higher priority than the color green has
            if (currentIndicator > 0) {
                setColorOfBackground(currentIndicator);
            } else {
                setColorOfBackground(3);
            }
        } else {
            setColorOfBackground(currentIndicator);
        }
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
                        adjustQuadrants(azimuth);
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
                            if (!testIfInRange(recentDestination, destination)) {
                                if (testIfArrivedAtDestination(location, wayPoint)) {
                                    recentDestination = destination;
                                    buildAlertForArrivingAtWayPoint();
                                }
                            }
                            recentLocation = location;
                            adjustDistLabel();
                            setupCompass();

                            startQuadrantTriggerFunction(location);
                            centerMapToUsersPosition(location);

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

    public void updateMarkerOnMinimap(double latitude, double longitude) {
        locationMarker.setPosition(new GeoPoint(latitude, longitude));
        map.invalidate();
    }

    public void startQuadrantTriggerFunction(Location location) {
        if (previousJSONLocationLocation != null && !visitedRecentJSONLocation) {
            if (testUserIsAtRecentJSONDestination(location, previousJSONLocationLocation)) {
                visitedRecentJSONLocation = true;
            }
        }

        checkIfUserDroveIntoIndicatedStreet(location); // first color background before removing indicatedstreets from list

        checkIfSavedIndicatedStreetLocationStillInRange(location); //clean up old saved locations

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
                    previousJSONLocationLocation = jsonDestination;
                    currentIndicator = n.getIndicator();
                    visitedRecentJSONLocation = false;

                    //check if user turnt  => this means deadends and slow paths arent on his way => behind user (can be shown in other direction)
                    if (allVisitedNodes.contains(n)) {
                        currentIndicator = 0;
                    } else {
                        allVisitedNodes.add(n);
                    }

                    setColorOfBackground(currentIndicator);


                    List<Node> jsonIndicatedStreets = graph.returnAllIndicatedStreetsNearby(jsonNodeList.indexOf(n), previousJSONLocation);

                    for (Node ne : jsonIndicatedStreets) {
                        Location jsonIndicatedStreet = new Location("");
                        jsonIndicatedStreet.setLatitude(ne.getLat());
                        jsonIndicatedStreet.setLongitude(ne.getLng());

                        System.out.println("Derzeitige Indicated Straße " + jsonIndicatedStreet);
                        saveRecentIndicatorLocation(jsonDestination, jsonIndicatedStreet, ne.getIndicator());
                    }
                    break;//just check the next Location which is nearby
                } else {
                    checkIfUserDroveIntoIndicatedStreet(location);
                    break;
                }
            }
        }
        saveUserHistory(recentLocation);
    }

    /*
     * If user drives into indicated street.
     * It can be measured by the distance between the user and the indicated street.
     * Therefore if the users last location was the triggerstreet and the distance is shorter than the original distance between triggerstreet and indicated street
     * => the user entered it
     *
     * note: no previousdestination needed because indicatedStreets list already has recent state
     */
    public void checkIfUserDroveIntoIndicatedStreet(Location userLocation) {
        //street already entered... need to measure distance if user turned around
        if (recentlyEnteredStreetByUser.getTriggerStreet() != null) {
            Location recentTriggerStreet = recentlyEnteredStreetByUser.getTriggerStreet();
            Location recentIndicatedStreet = recentlyEnteredStreetByUser.getIndicatedStreet();
            double distanceBetweenPoints = recentTriggerStreet.distanceTo(recentIndicatedStreet);
            double distanceOfUserToIndicatedStreet = userLocation.distanceTo(recentIndicatedStreet);

            double difference = distanceBetweenPoints - distanceOfUserToIndicatedStreet;


            if (difference < recentlyEnteredStreetDistance) {
                currentIndicator = 0; // deadend or slowstreet color not applied anymore
                previousJSONLocation = -1; //reset previousjsonlocation to reactivate triggering quadrants on the way, if the user returns
                recentlyEnteredStreetDistance = -1;
                recentlyEnteredStreetByUser = new IndicatedStreet(null, null, 0);
                visitedRecentJSONLocation = false;
            } else {
                recentlyEnteredStreetDistance = difference;
            }
        } else {
            for (IndicatedStreet iIndstr : indicatedStreets) {
                Location recentTriggerStreet = iIndstr.getTriggerStreet();
                Location recentIndicatedStreet = iIndstr.getIndicatedStreet();
                double distanceBetweenPoints = recentTriggerStreet.distanceTo(recentIndicatedStreet);
                double distanceOfUserToIndicatedStreet = userLocation.distanceTo(recentIndicatedStreet);

                double difference = distanceBetweenPoints - distanceOfUserToIndicatedStreet;
                //10 meter difference will be ignored due to fluctuations of gps location
                if (difference > 15) {
                    recentlyEnteredStreetByUser = iIndstr;
                    recentlyEnteredStreetDistance = difference;
                    setColorOfBackground(iIndstr.getIndicator());
                    currentIndicator = iIndstr.getIndicator();
                    break;
                }
            }
        }
    }

    //if recent street has a indicator, the background is color specifically
    public void setColorOfBackground(int indicator) {
        int color;

        //don't repeat function if value is already set
        if (lastIndicator != indicator) {

            ////https://stackoverflow.com/a/55648103/9824424 ?

            //if indicator is 1 it's a deadend => red color, if 2 it's a slow path
            switch (indicator) {
                case 0:
                    color = ResourcesCompat.getColor(getResources(), R.color.defaultBG, null);
                    break;
                case 1:
                    color = ResourcesCompat.getColor(getResources(), R.color.TSred, null);
                    break;
                case 2:
                    color = ResourcesCompat.getColor(getResources(), R.color.yellow, null);
                    break;
                case 3:
                    color = ResourcesCompat.getColor(getResources(), R.color.TSgreen, null);
                    break;
                default:
                    color = ResourcesCompat.getColor(getResources(), R.color.defaultBG, null);
                    break;
            }
            ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), recentBackgroundColor, color);
            colorAnimation.setDuration(250); // milliseconds
            colorAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animator) {
                    currentLayout.setBackgroundColor((int) animator.getAnimatedValue());
                }

            });
            colorAnimation.start();

            lastIndicator = indicator;
            recentBackgroundColor = color;
        }
    }


    private synchronized Boolean indicatorStreetsContains(final Location indstr) {
        for (IndicatedStreet iIndstr : indicatedStreets) {
            //equals doesn't work here
            if (testIfInRange(iIndstr.getIndicatedStreet(), indstr)) {
                return true;
            }
        }
        return false;
    }

    private synchronized void indicatorLocationAdd(final Location triggerStreet, final Location indst, final int indicator) {
        if (indicatedStreets.size() <= 4) {
            int index = indicatedStreets.size(); //last index + 1 for new element
            IndicatedStreet instreet = new IndicatedStreet(triggerStreet, indst, indicator);
            indicatedStreets.add(instreet);
            setQuadrant(index, indicator, true);
        } else {
            checkIfSavedIndicatedStreetLocationStillInRange(recentLocation);
            indicatorLocationAdd(triggerStreet, indst, indicator); //TODO Catch Loop
            //TODO Aufruf der Funktion ? Oder gibs dann nur einen LOOP ?
        }
    }


    public synchronized void saveRecentIndicatorLocation(final Location triggerStreet, final Location indst, final int indicator) {
        if (!indicatorStreetsContains(indst)) {
            indicatorLocationAdd(triggerStreet, indst, indicator);
        } else {
            System.out.println("triggerStreet already placed as quadrant");
        }
    }

    //checks if saved dead end locations are still in range, if not Quadrant is made invisible and savedLocation gets 0.0 values
    public void checkIfSavedIndicatedStreetLocationStillInRange(Location location) {
        ArrayList<IndicatedStreet> toRemove = new ArrayList<IndicatedStreet>();

        //if uservisited recentJSONLocation then the item has to be hidden after 15 meters. Otherwise it will be kept active for 30 meters
        int radius = visitedRecentJSONLocation ? 15 : 30;
        for (IndicatedStreet indstr : indicatedStreets) {

            //check if triggerstreet of indicated street isn't in range of location
            if (!checkIfLocationIsInRange(location, indstr.getTriggerStreet(), radius)) {
                int index = indicatedStreets.indexOf(indstr);
                setQuadrant(index, 0, false);
                toRemove.add(indstr);
            }
        }
        //remove streets afterwards. (the index can't be manipulated before, Reason: quadrants index)
        if (toRemove.size() > 0) {
            Iterator<IndicatedStreet> i = indicatedStreets.iterator();
            while (i.hasNext()) {
                IndicatedStreet indstr = i.next();
                //check if triggerstreet of indicated street isn't in range of location
                if (toRemove.contains(indstr)) {
                    i.remove();
                    System.out.println("Street removed " + indstr);
                }
            }
        }
    }

    public Boolean checkIfLocationIsInRange(Location location, Location recentJSONDestination, int radius) {
        double distance = location.distanceTo(recentJSONDestination);
        return distance <= radius;
    }

    public void adjustQuadrants(float azim) {
        for (int i = 0; i < indicatedStreets.size(); i++) {
            tiltNotificationImage(azim, recentLocation, indicatedStreets.get(i).indicatedStreet, i);
        }
    }

    /*
     * Use variablenumber to refer to last saved azimuth in @currentQuadrantAzimuths array
     */
    public synchronized void tiltNotificationImage(float azim, Location location, Location jsonIndicatedStreet, int variableNumber) {
        float circleAzimuth = azim;
        circleAzimuth -= computeHeading(location.getLatitude(), location.getLongitude(), jsonIndicatedStreet.getLatitude(), jsonIndicatedStreet.getLongitude());

        Animation an = new RotateAnimation(-currentQuadrantAzimuths[variableNumber], -circleAzimuth,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
                0.5f);

        currentQuadrantAzimuths[variableNumber] = circleAzimuth;
//        Animation an = new RotateAnimation(-newCurrentAzimuth, -circleAzimuth,
//                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
//                0.5f);
//        testAzimuth = circleAzimuth;
//
//        newCurrentAzimuth = circleAzimuth;
        an.setDuration(500);
        an.setRepeatCount(0);
        an.setFillAfter(true);

        switch (variableNumber) {
            case 0:
                if (quadrant0.getVisibility() == View.VISIBLE) {
                    quadrant0.startAnimation(an);
                }
                break;
            case 1:
                if (quadrant1.getVisibility() == View.VISIBLE) {
                    quadrant1.startAnimation(an);
                }
                break;
            case 2:
                if (quadrant2.getVisibility() == View.VISIBLE) {
                    quadrant2.startAnimation(an);
                }
                break;
            case 3:
                if (quadrant3.getVisibility() == View.VISIBLE) {
                    quadrant3.startAnimation(an);
                }
                break;
            default:
                break;
        }
    }

    public void setQuadrant(int variableNumber, int indicator, boolean showQuadrant) {
        int color;
        if (showQuadrant) {
            color = (indicator == 1) ? ResourcesCompat.getColor(getResources(), R.color.TSred, null) : ResourcesCompat.getColor(getResources(), R.color.yellow, null); //if indicator is 1 it's a deadend => red color, if 2 it's a slow path
            System.out.println("This indicator " + indicator + " has color " + color);
        } else {
            color = ResourcesCompat.getColor(getResources(), R.color.white, null);
        }
        int visibleStatus = showQuadrant ? View.VISIBLE : View.INVISIBLE;
        String isQuadrantShown = showQuadrant ? "visible" : "invisible";

        switch (variableNumber) {
            case 0:
                quadrant0.clearAnimation(); // to change rotation and visibility after animation was applied

                quadrant0.setVisibility(visibleStatus);
                quadrant0.setColorFilter(color);
                if (!showQuadrant) {
//                    quadrant0.setRotation(0);
                    currentQuadrantAzimuths[0] = 0;
                }
                System.out.println("quadrant0 " + isQuadrantShown);
                break;
            case 1:
                quadrant1.clearAnimation(); // to change rotation and visibility after animation was applied

                quadrant1.setVisibility(visibleStatus);
                quadrant1.setColorFilter(color);
                if (!showQuadrant) {
//                    quadrant1.setRotation(0);
                    currentQuadrantAzimuths[1] = 0;

                }

                System.out.println("quadrant1 " + isQuadrantShown);
                break;
            case 2:
                quadrant2.clearAnimation(); // to change rotation and visibility after animation was applied

                quadrant2.setVisibility(visibleStatus);
                quadrant2.setColorFilter(color);
                if (!showQuadrant) {
//                    quadrant2.setRotation(0);
                    currentQuadrantAzimuths[2] = 0;
                }

                System.out.println("quadrant2 " + isQuadrantShown);
                break;
            case 3:
                quadrant3.clearAnimation(); // to change rotation and visibility after animation was applied

                quadrant3.setVisibility(visibleStatus);
                quadrant3.setColorFilter(color);
                if (!showQuadrant) {
//                    quadrant3.setRotation(0);
                    currentQuadrantAzimuths[3] = 0;
                }


                System.out.println("quadrant3 " + isQuadrantShown);
                break;
            default:
                System.out.println("More than 4 streets saved in indicated Streets => impossible to show or hide quadrant");
                break;
        }
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

    public Boolean testUserIsAtRecentJSONDestination(Location location, Location dest) {
        double distance = location.distanceTo(dest);
        return distance <= 10;
    }

    public Boolean testIfArrivedAtDestination(Location location, Location dest) {
        double distance = location.distanceTo(dest);
        return distance <= 25;
    }

    public void saveUserHistory(Location location) {
        if (recordSession) {
            //save quadrants visibility and azimuth
            UserHistoryActivatedQuadrant[] activatedQuadrants = new UserHistoryActivatedQuadrant[4];
            for (int i = 0; i < activatedQuadrants.length; i++) {
                switch (i) {
                    case 0:
                        activatedQuadrants[0] = new UserHistoryActivatedQuadrant(currentQuadrantAzimuths[0], (quadrant0.getVisibility() == View.VISIBLE) ? "visible" : "invisible");
                        break;
                    case 1:
                        activatedQuadrants[1] = new UserHistoryActivatedQuadrant(currentQuadrantAzimuths[1], (quadrant1.getVisibility() == View.VISIBLE) ? "visible" : "invisible");
                        break;
                    case 2:
                        activatedQuadrants[2] = new UserHistoryActivatedQuadrant(currentQuadrantAzimuths[2], (quadrant2.getVisibility() == View.VISIBLE) ? "visible" : "invisible");
                        break;
                    case 3:
                        activatedQuadrants[3] = new UserHistoryActivatedQuadrant(currentQuadrantAzimuths[3], (quadrant3.getVisibility() == View.VISIBLE) ? "visible" : "invisible");
                        break;
                }
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd_HH:mm:ss");
            String date = sdf.format(new Date());


            int currentColorInt = ((ColorDrawable) currentLayout.getBackground()).getColor();
            String currentBackgrundColor = Integer.toHexString(currentColorInt);
            userHistories.add(new UserHistory(date, location.getLatitude(), location.getLongitude(), currentAzimuth, distance, currentIndicator, activatedQuadrants, allVisitedNodes, currentBackgrundColor));
        }
    }

}
