package com.eve.atcfplus;

import android.Manifest;
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
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;
import com.mikepenz.iconics.IconicsColor;
import com.mikepenz.iconics.IconicsDrawable;
import com.mikepenz.iconics.IconicsSize;
import com.mikepenz.iconics.typeface.library.fontawesome.FontAwesome;
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

import java.util.List;

import fr.quentinklein.slt.LocationTracker;
import fr.quentinklein.slt.TrackerSettings;

public class OSMActivity extends AppCompatActivity {
    MapView map = null;

    private static Context context;
    private Location destination = new Location("destination");
    private Location wayPoint = new Location("waypoint");
    private Location recentLocation = new Location("recentLocation");
    private IMapController mapController;
    private Marker locationMarker;
    private Marker wayPointMarker;
    private Marker destinationMarker;
    private int lastMarker;
    private LocationTracker tracker;
    private Boolean firstLoad;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OSMActivity.context = getApplicationContext();

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); // Make to run your application only in portrait mode

        //PERMISSIONS
        Dexter.withActivity(this)
                .withPermissions(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.INTERNET,
                        Manifest.permission.ACCESS_NETWORK_STATE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                ).withListener(new MultiplePermissionsListener() {
            @Override
            public void onPermissionsChecked(MultiplePermissionsReport report) {/* ... */}

            @Override
            public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {/* ... */}
        }).check();

        statusCheck();//check if gps is enabled

        firstLoad = true; //map is centered to users position

        startTracking(); //prompt to activate gps, if not enabled



        //load/initialize the osmdroid configuration, this can be done
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        //setting this before the layout is inflated is a good idea
        //it 'should' ensure that the map has a writable location for the map cache, even without permissions
        //if no tiles are displayed, you can try overriding the cache path using Configuration.getInstance().setCachePath
        //see also StorageUtils
        //note, the load method also sets the HTTP User Agent to your application's package name, abusing osm's tile servers will get you banned based on this string


        setContentView(R.layout.activity_osm);

        map = findViewById(R.id.map);
        final String[] tileURLs = {"MAPSERVERURL"};
        final ITileSource Eve =
                new XYTileSource("Tile Server",
                        1,
                        18,
                        256,
                        ".png",
                        tileURLs,
                        "from OpenMapTiles Map Server");
        map.setTileSource(Eve);


        map.setMultiTouchControls(true);
        map.setBuiltInZoomControls(false);


        mapController = map.getController();
        mapController.setZoom(17.0);
        map.setHasTransientState(false);


        lastMarker = 1;
        //Setting location and Destination marker for future usage
        locationMarker = new Marker(map);
        locationMarker.setIcon(new IconicsDrawable(context)
                .icon(MaterialDesignIconic.Icon.gmi_gps_dot)
                .color(IconicsColor.colorInt(Color.BLUE))
                .size(IconicsSize.dp(20)));
        locationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        locationMarker.setInfoWindow(null);
        wayPointMarker = new Marker(map);
        wayPointMarker.setIcon(new IconicsDrawable(context)
                .icon(FontAwesome.Icon.faw_map_marker_alt)
                .color(IconicsColor.colorInt(Color.BLUE))
                .size(IconicsSize.dp(20)));
        wayPointMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        wayPointMarker.setInfoWindow(null);
        destinationMarker = new Marker(map);
        destinationMarker.setIcon(new IconicsDrawable(context)
                .icon(FontAwesome.Icon.faw_map_marker_alt)
                .color(IconicsColor.colorInt(Color.RED))
                .size(IconicsSize.dp(20)));
        destinationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        destinationMarker.setInfoWindow(null);

        map.getOverlays().add(locationMarker);
        map.getOverlays().add(wayPointMarker);
        map.getOverlays().add(destinationMarker);

        //hard coded study destinationpoints
        wayPoint.setLatitude(53.091102);
        wayPoint.setLongitude(8.903242);
        destination.setLatitude(53.095969);
        destination.setLongitude(8.907539);
        wayPointMarker.setPosition(new GeoPoint(wayPoint.getLatitude(), wayPoint.getLongitude()));
        destinationMarker.setPosition(new GeoPoint(destination.getLatitude(), destination.getLongitude()));

        FloatingActionButton startNav = findViewById(R.id.startNav);
        startNav.setImageDrawable(new IconicsDrawable(context)
                .icon(FontAwesome.Icon.faw_bicycle)
                .color(IconicsColor.colorInt(Color.WHITE))
                .size(IconicsSize.dp(30)));
        startNav.setScaleType(ImageView.ScaleType.CENTER);
        startNav.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (wayPoint.getLatitude() != 0.0 && destination.getLatitude() != 0.0) {
                    Intent myIntent = new Intent(view.getContext(), MainActivity.class);
                    myIntent.putExtra("latWayPoint", wayPoint.getLatitude());
                    myIntent.putExtra("lngWayPoint", wayPoint.getLongitude());
                    myIntent.putExtra("latDestination", destination.getLatitude());
                    myIntent.putExtra("lngDestination", destination.getLongitude());
                    myIntent.putExtra("latLocation", recentLocation.getLatitude());
                    myIntent.putExtra("lngLocation", recentLocation.getLongitude());
                    startActivityForResult(myIntent, 0);
                } else {
                    AlertDialog alertDialog = new AlertDialog.Builder(OSMActivity.this).create();
                    alertDialog.setTitle("Wo gehts hin?");
                    alertDialog.setIcon(new IconicsDrawable(context)
                            .icon(FontAwesome.Icon.faw_smile_beam)
                            .color(IconicsColor.colorInt(Color.GREEN))
                            .size(IconicsSize.dp(24)));
                    alertDialog.setMessage("Es wurde kein Ziel gewählt");
                    alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                    alertDialog.show();
                }
            }

        });

        FloatingActionButton centerPosition = findViewById(R.id.centerPosition);
        centerPosition.setImageDrawable(new IconicsDrawable(context)
                .icon(MaterialDesignIconic.Icon.gmi_gps_dot)
                .color(IconicsColor.colorInt(Color.WHITE))
                .size(IconicsSize.dp(26)));
        centerPosition.setScaleType(ImageView.ScaleType.CENTER);
        centerPosition.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                centerMapToUsersPosition(recentLocation);
            }
        });
        final MapEventsReceiver mReceive = new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                switch (lastMarker) {
                    case 0:
                        destination.setLatitude(p.getLatitude());
                        destination.setLongitude(p.getLongitude());
                        updateMarkers(false, true, p.getLatitude(), p.getLongitude());
                        lastMarker = 1;
                        break;
                    case 1:
                        wayPoint.setLatitude(p.getLatitude());
                        wayPoint.setLongitude(p.getLongitude());
                        updateMarkers(false, false, p.getLatitude(), p.getLongitude());
                        lastMarker = 0;
                        break;
                }
                return false;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        };
        map.getOverlays().add(new MapEventsOverlay(mReceive));
    }

    public void statusCheck() {
        final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            buildAlertMessageNoGps();

        }
    }

    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Location Disabled");
        builder.setIcon(new IconicsDrawable(context)
                .icon(FontAwesome.Icon.faw_map_marked)
                .color(IconicsColor.colorInt(Color.RED))
                .size(IconicsSize.dp(24))
        );
        builder.setMessage("Your GPS seems to be disabled. This App needs to GPS to work. Do you want to enable it?")
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

    public void updateMarkers(boolean GPSLocationUpdate, boolean dest, double latitude, double longitude) {
        if (GPSLocationUpdate) {
            locationMarker.setPosition(new GeoPoint(latitude, longitude));
        } else {
            if (dest) {
                destinationMarker.setPosition(new GeoPoint(latitude, longitude));
            } else {
                wayPointMarker.setPosition(new GeoPoint(latitude, longitude));
            }
        }
        map.invalidate();
    }

    public static Context getAppContext() {
        return OSMActivity.context;
    }

    public void startTracking() {
        if (ContextCompat.checkSelfPermission(getAppContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(getAppContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // You need to ask the user to enable the permissions TODO
        } else {
            TrackerSettings settings =
                    new TrackerSettings()
                            .setUseGPS(true)
                            .setUseNetwork(true)
                            .setUsePassive(true)
                            .setTimeBetweenUpdates(1000)
                            .setMetersBetweenUpdates(1);
            tracker = new LocationTracker(getApplicationContext(), settings) {
                @Override
                public void onLocationFound(Location location) {
                    //newloc.append("\n " + location.getLongitude() + " " + location.getLatitude());


                    if (firstLoad) {
                        centerMapToUsersPosition(location);
                        firstLoad = false;
                    }


                    updateMarkers(true, false, location.getLatitude(), location.getLongitude());
                    recentLocation = location;
                }

                @Override
                public void onTimeout() {

                }
            };
            tracker.startListening();
        }
    }

    public void centerMapToUsersPosition(Location location) {
        GeoPoint startPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
        mapController.animateTo(startPoint);
        mapController.setCenter(startPoint);
    }

    public void onResume() {
        super.onResume();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        map.onResume(); //needed for compass, my location overlays, v6.0.0 and up

        if (tracker != null) {
            if (ContextCompat.checkSelfPermission(getAppContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(getAppContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // You need to ask the user to enable the permissions TODO
            } else {
                tracker.startListening();
            }
        }
    }

    public void onPause() {
        super.onPause();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().save(this, prefs);
        map.onPause();  //needed for compass, my location overlays, v6.0.0 and up

        if (tracker != null) {
            tracker.stopListening();
        }
    }
}
