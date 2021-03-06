package com.example.camilobaquero.maptwitter;

import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.location.Location;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatButton;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.example.camilobaquero.maptwitter.util.Utilities;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MapsActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener,
        GoogleMap.OnMapLongClickListener{

    private final static String TAG = "MapsActivity";
    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;
    private LatLng mDestination;
    private LocationRequest mLocationRequest;
    private PlaceAutocompleteFragment autocompleteFragment;
    private boolean mDestinationSelected = false;
    private Snackbar snackbar;

    // COLORS
    int[] colors = new int[4];
    int[] radius = {200, 100, 50, 10};

    // UI
    @BindView(R.id.coordinator_layout) CoordinatorLayout coordinatorLayout;
    @BindView(R.id.map_button_ok) AppCompatButton confirmationButton;

    @OnClick(R.id.map_button_ok) void confirmDestination() {
        snackbar.setText(calculateDistance(mLastLocation, mDestination));
        snackbar.show();
        animateMapPadding(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        ButterKnife.bind(this);

        setUpMapIfNeeded();
        setUpGoogleClient();
        createLocationRequest();

        autocompleteFragment = (PlaceAutocompleteFragment) getFragmentManager().findFragmentById(R.id.place_autocomplete_fragment);
        setAutoCompleteFragment();

        colors[0] = ContextCompat.getColor(this, R.color.far);
        colors[1] = ContextCompat.getColor(this, R.color.near);
        colors[2] = ContextCompat.getColor(this, R.color.next_to);
        colors[3] = ContextCompat.getColor(this, R.color.in);

        confirmationButton.setEnabled(false);
        snackbar = Snackbar.make(coordinatorLayout, "Destino fijado", Snackbar.LENGTH_INDEFINITE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_restart:
                Log.e(TAG, "Restart app");
                restart();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        mGoogleApiClient.connect();
        super.onStart();
    }

    @Override
    protected void onStop() {
        //mGoogleApiClient.disconnect();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        mGoogleApiClient.disconnect();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p/>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p/>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we
     * just add a marker near Africa.
     * <p/>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
        try {
            mMap.setMyLocationEnabled(true);
            mMap.setOnMapLongClickListener(this);
            mMap.setPadding(0, Utilities.convertDpToPixel(70), Utilities.convertDpToPixel(4), 0);
        } catch (SecurityException e) {
            Log.e(TAG, "The user needs to accept the location permissions.");
        }
    }

    private void setUpGoogleClient() {
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
    }

    @Override
    public void onConnected(Bundle connectionHint) {

        try {
            mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                    mGoogleApiClient);
            if (mLastLocation != null) {
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()), 15));
                Log.e("Latitude", String.valueOf(mLastLocation.getLatitude()));
                Log.e("Longitude", String.valueOf(mLastLocation.getLongitude()));
            }

            startLocationUpdates();

        } catch (SecurityException e) {
            Log.e(TAG, "The user needs to accept the location permissions.");
        }
    }

    protected void startLocationUpdates() {
        try {
            LocationServices.FusedLocationApi.requestLocationUpdates(
                    mGoogleApiClient, mLocationRequest, this);
        } catch (SecurityException e) {
            Log.e(TAG, "The user needs to accept the location permissions.");
        }
    }

    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    @Override
    public void onLocationChanged(Location location) {

        Log.e("Update", "Updating Location");
        Location oldLocation = mLastLocation;

        mLastLocation = location;
        if(mDestinationSelected) {
            updateUI();

            if(compareLocations(oldLocation, location)) {
                notificateUser();
            }
        }
    }

    private boolean compareLocations(Location oldLocation, Location newLocation) {
        Log.e("Update", "Comparing locations");

        Location destination = new Location("");
        destination.setLatitude(mDestination.latitude);
        destination.setLongitude(mDestination.longitude);

        int oldDistance = (int)oldLocation.distanceTo(destination);
        int newDistance = (int)newLocation.distanceTo(destination);

        Log.e("Comparing", "Old: " + oldDistance + " vs " + "New: " + newDistance);

        if(oldDistance > 200 && newDistance <= 200) return true;
        if(oldDistance > 100 && newDistance <= 100) return true;
        if(oldDistance > 50 && newDistance <= 50) return true;
        if(oldDistance > 10 && newDistance <= 10) {

            tweetMessage(new LatLng(newLocation.getLatitude(),newLocation.getLongitude()));

            return true;
        }

        return false;
    }

    private void updateUI() {
        Log.e("Update", "Updating UI");
        snackbar.setText(calculateDistance(mLastLocation, mDestination));
        if(!snackbar.isShown()) {
            snackbar.show();
        }
    }

    private void setAutoCompleteFragment() {

        autocompleteFragment.setBoundsBias(new LatLngBounds( // MEXICO
                new LatLng(13.239945, -119.179688),
                new LatLng(35.029996, -90.175781)));

        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                Log.e(TAG, "Place: " + place.getName());
                selectDestination(place.getLatLng());
            }

            @Override
            public void onError(Status status) {
                // TODO: Handle the error.
                Log.e(TAG, "An error occurred: " + status);
            }
        });
    }

    @Override
    public void onMapLongClick(LatLng latLng) {
        selectDestination(latLng);
    }

    private void selectDestination(LatLng latLng) {

        mDestination = latLng;
        mDestinationSelected = true;
        confirmationButton.setEnabled(true);

        mMap.clear();
        mMap.addMarker(new MarkerOptions().position(latLng).title("Destino"));

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()));
        builder.include(latLng);

        LatLngBounds bounds = builder.build();

        int padding = Utilities.convertDpToPixel(70); // offset from edges of the map in pixels
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding);

        Log.e(TAG, "Moving camera...");
        mMap.animateCamera(cameraUpdate);

        drawRangeCircles(latLng);
        
    }

    private void drawRangeCircles(LatLng latLng) {
        for(int i=0 ; i<colors.length ; i++) {
            mMap.addCircle(new CircleOptions()
                    .center(latLng)
                    .strokeWidth(1)
                    .radius(radius[i])
                    .fillColor(colors[i]));
        }
    }

    private void restart() {
        mDestinationSelected = false;
        mMap.clear();
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()), 15));
        confirmationButton.setEnabled(false);
        autocompleteFragment.setText("");
        snackbar.dismiss();
        animateMapPadding(false);
        mDestination = new LatLng(0,0);
    }

    private void animateMapPadding (boolean up) {

        ValueAnimator animation;

        int padding = Utilities.convertDpToPixel(48);

        if(up)
            animation = ValueAnimator.ofInt(0, padding);
        else
            animation = ValueAnimator.ofInt(padding, 0);

        animation.setDuration(150);
        animation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                mMap.setPadding(0, 0, 0, Integer.parseInt(valueAnimator.getAnimatedValue().toString()));
            }
        });
        animation.start();
    }

    private String calculateDistance(Location mLastLocation, LatLng mDestination) {

        Location destination = new Location("");
        destination.setLatitude(mDestination.latitude);
        destination.setLongitude(mDestination.longitude);

        int distance = (int)mLastLocation.distanceTo(destination);
        String inMeters = " ( " + distance + "m )";
        StringBuilder stringBuilder = new StringBuilder("");

        if(distance < 10)
            return stringBuilder.append(getString(R.string.in)).append(inMeters).toString();

        if(distance <= 50)
            return stringBuilder.append(getString(R.string.next_to)).append(inMeters).toString();

        if(distance <= 100)
            return stringBuilder.append(getString(R.string.near)).append(inMeters).toString();

        if(distance <= 200)
            return stringBuilder.append(getString(R.string.far)).append(inMeters).toString();

        return stringBuilder.append(getString(R.string.too_far)).append(inMeters).toString();
    }

    private void notificateUser() {

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        Notification.Builder mBuilder =
                new Notification.Builder(this)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(getString(R.string.notification_title))
                        .setContentText(calculateDistance(mLastLocation, mDestination))
                        .setSound(defaultSoundUri).setVibrate(new long[]{1000, 1000, 1000, 1000, 1000})
                        .setPriority(Notification.PRIORITY_HIGH);

        Intent resultIntent = new Intent(this, MapsActivity.class);
        resultIntent.setAction(Intent.ACTION_MAIN);
        resultIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                resultIntent, 0);

        mBuilder.setContentIntent(pendingIntent);
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotificationManager.notify(1, mBuilder.build());

    }

    private void tweetMessage(LatLng latLng) {

        String message = "He llegado a mi objetivo: (" + latLng.latitude + ", " + latLng.longitude + ").";
        String mapUrl = "https://maps.googleapis.com/maps/api/staticmap?center=" + latLng.latitude + "," + latLng.longitude + "&zoom=17&sensor=false&size=300x200&scale=2";//?markers=color:red%7C%7C" + latLng.latitude + "," + latLng.longitude;

        String tweetUrl = "https://twitter.com/intent/tweet?text="+message+"&url="+mapUrl;

        Log.e(TAG, "Tweet: " + tweetUrl);

        Uri uri = Uri.parse(tweetUrl);
        startActivity(new Intent(Intent.ACTION_VIEW, uri));

    }

    @Override
    public void onConnectionSuspended(int i) {  }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) { }
}