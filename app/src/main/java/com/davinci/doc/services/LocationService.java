package com.davinci.doc.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.davinci.doc.ApplicationWrapper;
import com.davinci.doc.R;
import com.davinci.doc.activities.MainActivity;
import com.davinci.doc.custom.FusedLocationProvider;
import com.google.android.gms.location.LocationRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.davinci.doc.ApplicationWrapper.TAG;
import static com.davinci.doc.custom.FusedLocationProvider.LocationChangedListener;

//background service that'll run for doctor and transit service to log their location to server
public class LocationService extends Service
	implements LocationChangedListener, Callback {

	LocationRequest request = new LocationRequest()
		.setInterval(60000)
		.setFastestInterval(60000)
		.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
	FusedLocationProvider locationProvider = null;
	ApplicationWrapper wrapper = null;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		new Handler(Looper.getMainLooper())
			.post(() -> Toast.makeText(this, "Starting Location Service", Toast.LENGTH_SHORT).show());
		wrapper = (ApplicationWrapper) getApplication();
		//start in foreground to prevent being killed
		startForeground(wrapper.setPersistentId((int) (Math.random() * 1000)), getPersistentNotification());
		//initialize the location provider for location updates
		locationProvider = new FusedLocationProvider(this, this);
		//start receiving updates
		locationProvider.getUpdates(request);
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		//stop receiving location update
		if (locationProvider != null) {
			locationProvider.stopUpdates();
			locationProvider = null;
		}
		//reset the persistent notification id
		wrapper.setPersistentId(-1);
		Log.i(TAG, "onDestroy: stopping location service");
		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onLastLocation(List<Location> locations) {
		//if we don't have internet connection, we return
		if (!ApplicationWrapper.isNetworkConnected(getApplicationContext()) || !ApplicationWrapper.isInternetAccessible())
			return;
		try {
			//get the location and make the network request to log it to the server
			Location location = locations.get(0);
			wrapper.getClient()
				.newCall(wrapper.getPreparedRequest(getBody(location.getLatitude(), location.getLongitude()), "updateLocation"))
				.enqueue(this);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onLocationChanged(List<Location> locations) {
		if (!ApplicationWrapper.isNetworkConnected(getApplicationContext()) || !ApplicationWrapper.isInternetAccessible())
			return;
		try {
			//get the location and make the network request to log it to the server
			Location location = locations.get(0);
			wrapper.getClient()
				.newCall(wrapper.getPreparedRequest(getBody(location.getLatitude(), location.getLongitude()), "updateLocation"))
				.enqueue(this);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onFailure(@NonNull Call call, @NonNull IOException e) {
		e.printStackTrace();
	}

	@Override
	public void onResponse(@NonNull Call call, @NonNull Response response) {
		try {
			//log the response body, nothing else to do here
			ResponseBody body = response.body();
			if (body != null)
				Log.i(TAG, "onResponse: " + body.string());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	//convenience method to build the notification
	private Notification getPersistentNotification() {
		return new NotificationCompat.Builder(this, ApplicationWrapper.locationChannel.first)
			.setContentTitle("DOC Location Service")
			.setContentText("This service updates your location to the server")
			.setSmallIcon(R.mipmap.ic_launcher)
			.setAutoCancel(false)
			.setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0,
				new Intent(getApplicationContext(), MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT))
			.build();
	}
	
	//convenience method to build the request body with latitude and longitude
	@NonNull
	private String getBody(double lat, double lon) throws JSONException {
		JSONObject parsed = new JSONObject();
		parsed.putOpt("lat", lat);
		parsed.putOpt("lon", lon);
		return "mutation{updateLocation(" +
			"email:\"" + wrapper.getEmail() + "\" " +
			"password:\"" + wrapper.getPassword() + "\" " +
			"type:\"" + wrapper.getType() + "\" " +
			"location:\"" + parsed.toString().replace("\"", "\\\"") + "\")}";
	}
}
