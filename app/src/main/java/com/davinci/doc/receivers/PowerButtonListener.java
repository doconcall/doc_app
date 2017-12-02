package com.davinci.doc.receivers;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
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

public class PowerButtonListener extends BroadcastReceiver
	implements Callback, FusedLocationProvider.LocationChangedListener {
	
	long lastEpoch = System.currentTimeMillis();
	int clickCount = 0;
	
	Context context = null;
	ApplicationWrapper wrapper = null;
	PendingResult result = null;
	FusedLocationProvider locationProvider = null;
	
	public PowerButtonListener(ApplicationWrapper wrapper) {
		//initialize with location service
		locationProvider = new FusedLocationProvider(this.wrapper = wrapper,
			this);
	}
	
	@Override
	public void onReceive(Context context, Intent intent) {
		this.context = context;
		String action = intent.getAction();
		//return if location permission wasn't provided
		if (action == null || (!action.equals(Intent.ACTION_SCREEN_OFF) && !action.equals(Intent.ACTION_SCREEN_ON)) ||
			ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
			return;
		//get the delta between emerging clicks
		long epoch = System.currentTimeMillis(), delta = epoch - lastEpoch;
		Log.i(TAG, "onReceive: " + delta);
		//click should happen before 1200ms
		if (delta < 1200)
			clickCount++;
		else clickCount = 1;
		lastEpoch = epoch;
		
		Log.i(TAG, "onReceive: " + clickCount);
		if (clickCount < 5) return;
		
		clickCount = 0;
		//user pressed the button 5 times, we go async and get location of the user
		result = goAsync();
		if (locationProvider == null)
			locationProvider = new FusedLocationProvider(this.wrapper, this);
		locationProvider.getUpdates(new LocationRequest()
			.setInterval(60000)
			.setFastestInterval(60000)
			.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY));
	}
	
	@Override
	public void onLastLocation(List<Location> locations) {
		//we don't have a location thus we return
		if (locations.size() < 1) {
			Toast.makeText(context, "Unable to lock location!", Toast.LENGTH_LONG).show();
			return;
		}
		//we have the last location, stop further location updates
		locationProvider.stopUpdates();
		Location lastLocation = locations.get(0);
		//build the network request with relevant information and make the network request
		try {
			JSONObject sos = new JSONObject();
			sos.putOpt("cid", wrapper.getEmail());
			sos.putOpt("lat", lastLocation.getLatitude());
			sos.putOpt("lon", lastLocation.getLongitude());
			sos.putOpt("note", "Emergency");
			wrapper.getClient()
			       .newCall(wrapper.getPreparedRequest("mutation{newSOS(" +
				       "email:\"" + wrapper.getEmail() + "\" " +
				       "password:\"" + wrapper.getPassword() + "\" " +
				       "radius:" + 1000 + " " +
				       "sos:\"" + sos.toString().replace("\"", "\\\"") +
				       "\"){id,cid,note,lat,lon,createdAt,resolved}}", "sos"))
			       .enqueue(this);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void onLocationChanged(List<Location> locations) {}
	
	@Override
	public void onFailure(@NonNull Call call, @NonNull IOException e) {
		e.printStackTrace();
		//finish this receiver anyway
		Looper.prepare();
		result.finish();
		Looper.loop();
	}
	
	@Override
	public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
		Looper.prepare();
		//get the response body and return if we error out
		ResponseBody body = response.body();
		String res = null;
		if (body == null || (res = body.string()).contains("errors")) {
			Log.i(TAG, "onError: " + res);
			Toast.makeText(wrapper, "Something went wrong\nUnable to update server", Toast.LENGTH_LONG).show();
			result.finish();
			return;
		}
		Log.i(TAG, "onResponse: " + res);
		//we set latest sos and start periodic range extension
		wrapper.setLatestSOS(res).extendRange();
		
		try {
			//build and show notification
			NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			if (manager != null)
				manager.notify(1001, new NotificationCompat.Builder(context, ApplicationWrapper.requestChannel.first)
					.setSmallIcon(R.drawable.heart)
					.setSubText(new JSONObject(res).optJSONObject("data").optJSONObject("newSOS").optString("id"))
					.setContentTitle("SOS broadcasted")
					.setContentText("Waiting for response")
					.setAutoCancel(true)
					.setColorized(true)
					.setVibrate(new long[]{0, 1000, 1000, 1000, 1000})
					.setContentIntent(PendingIntent.getActivity(wrapper, 0,
						new Intent(wrapper, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT))
					.build());
		} catch (JSONException e) {
			e.printStackTrace();
		}
		//send MainActivity a broadcast to update it's database
		wrapper.sendBroadcast(new Intent(ApplicationWrapper.ACTION_PUSH_NOTIFICATION));
		//finish since we've done our work
		result.finish();
		Looper.loop();
	}
}