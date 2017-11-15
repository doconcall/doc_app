package com.davinci.doc;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.util.Pair;
import android.view.View;

import com.davinci.doc.activities.MainActivity;
import com.davinci.doc.custom.PersonDescription;
import com.davinci.doc.custom.QueuedRequest;
import com.davinci.doc.receivers.OnActionClickListener;
import com.davinci.doc.receivers.PowerButtonListener;
import com.google.android.gms.maps.model.LatLng;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Created by aakash on 10/22/17.
 */

public class ApplicationWrapper extends Application {
	public final static String TAG = BuildConfig.LOG;

	public final static String api = "https://6ogevcpotf.execute-api.ap-south-1.amazonaws.com/dev/api",
		ACTION_PUSH_NOTIFICATION = "ACTION_PUSH_NOTIFICATION";
	public final static int PREVIEW_TIMEOUT = 600000;

	public static int PERSISTENT_ID = -1;
	public static final Pair<String, String> locationChannel = new Pair<>("LocationService", "Location Polling Service"),
		requestChannel = new Pair<>("SOS_Channel", "SOS");

	PowerButtonListener powerButtonListener = null;

	public int setPersistentId(int persistentId) {
		return PERSISTENT_ID = persistentId;
	}

	public int getPersistentId() {
		return PERSISTENT_ID;
	}

	public interface OnItemSelectedListener {
		void OnItemSelected(Object item, View view);
	}

	public static boolean isNetworkConnected(Context context) {
		ConnectivityManager manager = (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);
		return manager != null && manager.getActiveNetworkInfo() != null;
	}

	public static boolean isInternetAccessible() {
		try {
			InetAddress inetAddress = Executors.newSingleThreadExecutor()
				.submit(() -> InetAddress.getByName("google.com"))
				.get(1000, TimeUnit.MILLISECONDS);
			//noinspection EqualsBetweenInconvertibleTypes
			return inetAddress != null && !inetAddress.equals("");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	final OkHttpClient client = new OkHttpClient();

	String email = "", password = "", deviceID = "", type = "",
		lastInfo = "";
	JSONObject info = null, sosHistory = null, transitHistory = null;

	LatLng location = null;
	ArrayList<PersonDescription> people = new ArrayList<>();
	ArrayList<QueuedRequest> queuedRequests = new ArrayList<>();

	String latestSOS = null;
	int radius = 0;
	Timer rangeExtender = null, navigator = null;

	@Override
	public void onCreate() {
		super.onCreate();
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		email = preferences.getString("email", "");
		password = preferences.getString("password", "");
		deviceID = preferences.getString("deviceID", "");
		type = preferences.getString("type", "client");

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			if (manager == null) return;
			manager.createNotificationChannel(new NotificationChannel(locationChannel.first, locationChannel.second, NotificationManager.IMPORTANCE_MIN));
			manager.createNotificationChannel(new NotificationChannel(requestChannel.first, requestChannel.second, NotificationManager.IMPORTANCE_HIGH));
		}

		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_SCREEN_ON);
		filter.addAction(Intent.ACTION_SCREEN_OFF);
		powerButtonListener = new PowerButtonListener(this);
		registerReceiver(powerButtonListener, filter);
	}

	@Override
	public void onTrimMemory(int level) {
		save();
		super.onTrimMemory(level);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		save();
		super.onConfigurationChanged(newConfig);
	}

	private void save() {
		PreferenceManager.getDefaultSharedPreferences(this)
			.edit()
			.putString("email", email)
			.putString("password", password)
			.putString("deviceID", deviceID)
			.putString("type", type)
			.apply();
	}

	public OkHttpClient getClient() {
		return client;
	}

	public Request getPreparedRequest(String body, String tag) {
		return new Request.Builder()
			.url(ApplicationWrapper.api)
			.header("Content-Type", "application/graphql")
			.post(RequestBody.create(MediaType.parse("application/text charset=utf-8"), body))
			.tag(tag)
			.build();
	}

	public String getEmail() {
		return email;
	}

	public String getPassword() {
		return password;
	}

	public String getType() {
		return type;
	}

	public String getDeviceID() {
		return deviceID;
	}

	public JSONObject getInfo() {
		return info;
	}

	public JSONObject getSOSHistory() {
		return sosHistory;
	}

	public JSONObject getTransitHistory() {
		return transitHistory;
	}

	public ArrayList<PersonDescription> getPeople() {
		return people;
	}

	public LatLng getLocation() {
		return location;
	}

	public ArrayList<QueuedRequest> getQueuedRequests() {
		return queuedRequests;
	}

	public String getLatestSOS() {
		return latestSOS;
	}

	public ApplicationWrapper setEmail(String email) {
		this.email = email;
		return this;
	}

	public int getRadius() {
		return radius;
	}

	public ApplicationWrapper setPassword(String password) {
		this.password = password;
		return this;
	}

	public void setDeviceID(String deviceID) {
		this.deviceID = deviceID;
	}

	public JSONObject setInfo(String info) throws JSONException {
		return this.info = new JSONObject(
			this.lastInfo = info
				.replace("newClient", "client")
				.replace("newDoctor", "doctor")
				.replace("newTransit", "transitHistory")
				.replace("updateClient", "client")
				.replace("updateDoctor", "doctor")
				.replace("updateTransit", "transitHistory"));
	}

	public JSONObject setSOSHistory(String sos) throws JSONException {
		return this.sosHistory = new JSONObject(sos);
	}

	public JSONObject setTransitHistory(String transitHistory) throws JSONException {
		return this.transitHistory = new JSONObject(transitHistory);
	}

	public ApplicationWrapper setType(String type) {
		this.type = type;
		return this;
	}

	public void setLocation(LatLng location) {
		this.location = location;
		if (location == null) {
			if (navigator != null) {
				navigator.cancel();
				navigator = null;
			}
		} else {
			(navigator = new Timer(true))
				.schedule(new TimerTask() {
					@Override
					public void run() {
						setLocation(null);
						sendBroadcast(new Intent(ACTION_PUSH_NOTIFICATION));
					}
				}, PREVIEW_TIMEOUT);
		}
	}

	public ApplicationWrapper setLatestSOS(String latestSOS) {
		this.radius = 1000;
		this.latestSOS = latestSOS;
		return this;
	}

	@SuppressWarnings("SameParameterValue")
	public void extendRange(long interval) {
		if (latestSOS == null) return;
		rangeExtender = new Timer(true);
		rangeExtender.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				client.newCall(getPreparedRequest("mutation{extendRange(" +
					"email:\"" + email + "\" " +
					"password:\"" + password + "\" " +
					"id:\"" + latestSOS + "\" radius:" + (radius += 1000) + ")" +
					"{id,cid,lat,lon,dids,rejection,resolved}}", "extendRange"))
					.enqueue(new Callback() {
						@Override
						public void onFailure(@NonNull Call call, @NonNull IOException e) {
							e.printStackTrace();
							clearExtender();
							latestSOS = null;
							sendBroadcast(new Intent(ACTION_PUSH_NOTIFICATION));
						}

						@Override
						public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
							ResponseBody body = response.body();
							Log.i(TAG, "onResponse: " + (body != null ? body.string() : "null"));
							if (radius >= 5000) {
								clearExtender();
								latestSOS = null;
							}
							Intent intent = new Intent(ACTION_PUSH_NOTIFICATION);
							intent.putExtra("msg", "Range extended to " + radius + "m");
							sendBroadcast(intent);
						}
					});
			}
		}, interval, interval);
	}

	public void clearExtender() {
		if (rangeExtender != null)
			rangeExtender.cancel();
		rangeExtender = null;
		latestSOS = null;
	}

	public void acceptSOSorTransit(String id, String method, Callback callback) throws IOException {
		client.newCall(getPreparedRequest("mutation{" + method + "(" +
			"id:\"" + id + "\" " +
			(type.equals("doctor") ? "did" : "tid") + ":\"" + email + "\" " +
			"password:\"" + password + "\"" +
			"){email,info{name,phone" + (type.equals("doctor") ? ",history" : "") +
			"}}}", method)).enqueue(callback);
	}

	public PersonDescription findPerson(String id) {
		for (PersonDescription person : people)
			if (person.getId().equals(id))
				return person;
		return null;
	}

	public void logout() {
		clearExtender();
		clearQueuedRequests();
		people.clear();
		clear();
		email = "";
		password = "";
		type = "client";
		info = null;
		latestSOS = null;
		radius = 0;
		rangeExtender = null;
		navigator = null;
	}

	public void clear() {
		this.info = null;
		this.sosHistory = null;
		this.transitHistory = null;
		this.lastInfo = "";
	}

	public Notification getRequestNotification(String requestID, double lat, double lon, String note, int id) {
		queuedRequests.add(new QueuedRequest(requestID, id, this));
		@SuppressLint("DefaultLocale")
		NotificationCompat.Builder builder = new NotificationCompat.Builder(this, ApplicationWrapper.requestChannel.first)
			.setSmallIcon(R.drawable.heart)
			.setContentTitle(String.format("%.3f/%.3f", lat, lon))
			.setSubText(requestID)
			.setAutoCancel(true)
			.setColorized(true)
			.setDefaults(Notification.DEFAULT_ALL)
			.setStyle(new NotificationCompat.BigTextStyle().bigText(note))
			.setContentIntent(PendingIntent.getActivity(this, 0,
				new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT));
		Intent decline = new Intent(this, OnActionClickListener.class);
		decline.setAction("decline");
		decline.putExtra("requestID", requestID);
		decline.putExtra("id", id);
		Intent accept = new Intent(this, OnActionClickListener.class);
		accept.setAction("accept");
		accept.putExtra("requestID", requestID);
		accept.putExtra("location", lat + "," + lon);
		accept.putExtra("id", id);
		builder.addAction(R.drawable.cancel, "Decline", PendingIntent.getBroadcast(this, 0, decline, PendingIntent.FLAG_UPDATE_CURRENT));
		builder.addAction(R.drawable.accept, "Accept", PendingIntent.getBroadcast(this, 1, accept, PendingIntent.FLAG_UPDATE_CURRENT));
		if (type.equals("doctor")) {
			Intent transit = new Intent(this, OnActionClickListener.class);
			transit.setAction("transit");
			transit.putExtra("requestID", requestID);
			transit.putExtra("location", lat + "," + lon);
			transit.putExtra("id", id);
			builder.addAction(R.drawable.ambulance, "Arrange Transit", PendingIntent.getBroadcast(this, 2, transit, PendingIntent.FLAG_UPDATE_CURRENT));
		}
		return builder.build();
	}

	public Notification getRequestUpdateNotification(String requestID, String email) {
		if (requestID == null || email == null) return null;
		return new NotificationCompat.Builder(this, ApplicationWrapper.requestChannel.first)
			.setSmallIcon(R.drawable.heart)
			.setSubText(requestID)
			.setContentTitle("SOS Accepted")
			.setStyle(new NotificationCompat.BigTextStyle().bigText(email))
			.setAutoCancel(true)
			.setColorized(true)
			.setDefaults(Notification.DEFAULT_ALL)
			.setContentIntent(PendingIntent.getActivity(this, 0,
				new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT))
			.build();
	}

	public void declineRequest(String id) {
		String method = type.equals("doctor") ? "declineSOS" : "declineTransitRequest";
		client.newCall(getPreparedRequest("mutation{" + method +
			"(id:\"" + id + "\" " +
			(type.equals("doctor") ? "did" : "tid") + ":\"" + email + "\" " +
			"password:\"" + password + "\")}", method))
			.enqueue(new Callback() {
				@Override
				public void onFailure(@NonNull Call call, @NonNull IOException e) {
					e.printStackTrace();
				}

				@Override
				public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
					ResponseBody body = response.body();
					if (body == null) {
						Log.i(TAG, "onResponse: null");
						return;
					}
					Log.i(TAG, "onResponse: " + body.string());
					sendBroadcast(new Intent(ApplicationWrapper.ACTION_PUSH_NOTIFICATION));
				}
			});
	}

	public void clearQueuedRequests() {
		Iterator<QueuedRequest> iterator = queuedRequests.iterator();
		while (iterator.hasNext()) {
			QueuedRequest request = iterator.next();
			request.clearSelf(false);
			iterator.remove();
		}
	}

	public void resolve(String requestID) {
		QueuedRequest selected = null;
		for (QueuedRequest request : queuedRequests)
			if (request.getId().equals(requestID)) {
				selected = request;
				break;
			}
		if (selected != null) selected.run();
	}
}
