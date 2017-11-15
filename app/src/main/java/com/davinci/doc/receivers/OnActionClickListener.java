package com.davinci.doc.receivers;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.davinci.doc.ApplicationWrapper;
import com.davinci.doc.activities.NewTransitDialog;
import com.davinci.doc.custom.PersonDescription;
import com.google.android.gms.maps.model.LatLng;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static android.content.Context.NOTIFICATION_SERVICE;
import static com.davinci.doc.ApplicationWrapper.TAG;

public class OnActionClickListener extends BroadcastReceiver
	implements Callback {

	ApplicationWrapper wrapper;
	PendingResult result = null;
	Intent intent;
	String method = null, requestID = null;

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.i(TAG, "onReceive: " + intent);
		if (!ApplicationWrapper.isNetworkConnected(context) || !ApplicationWrapper.isInternetAccessible())
			return;
		NotificationManager manager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
		if (manager != null)
			manager.cancel(intent.getIntExtra("id", 1));
		this.intent = intent;
		result = goAsync();
		try {
			wrapper = (ApplicationWrapper) context.getApplicationContext();
			String action = intent.getAction();
			requestID = intent.getStringExtra("requestID");
			if (action == null) {
				result.finish();
				return;
			}
			method = getMethod(action);
			updateServer(action, requestID, method);
			if (action.equals("transit")) {
				String[] location = intent.getStringExtra("location").split(",");
				Intent transitIntent = new Intent(wrapper, NewTransitDialog.class);
				transitIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				transitIntent.putExtra("requestID", requestID);
				transitIntent.putExtra("lat", Double.parseDouble(location[0]));
				transitIntent.putExtra("lon", Double.parseDouble(location[1]));
				wrapper.startActivity(transitIntent);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	String getMethod(String action) {
		String type = wrapper.getType();
		return action.equals("decline") ?
			(type.equals("doctor") ? "declineSOS" : "declineTransitRequest") :
			(type.equals("doctor") ? "acceptSOS" : "acceptTransitRequest");
	}

	void updateServer(String action, String requestID, String method) throws IOException {
		String type = wrapper.getType();
		switch (action) {
			case "decline":
				wrapper.getClient()
					.newCall(wrapper.getPreparedRequest("mutation{" + method + "(" +
						"id:\"" + requestID + "\" " +
						(type.equals("doctor") ? "did" : "tid") + ":\"" + wrapper.getEmail() + "\" " +
						"password:\"" + wrapper.getPassword() + "\"" +
						")}", type.equals("doctor") ? "declineSOS" : "declineTransitRequest")).enqueue(this);
				break;
			default:
				wrapper.clearQueuedRequests();
				wrapper.acceptSOSorTransit(requestID, method, this);
				break;
		}
	}

	PersonDescription addNewPerson(String id, String personType, JSONObject person) throws JSONException {
		return new PersonDescription(id, personType, person)
			.setTimeout(ApplicationWrapper.PREVIEW_TIMEOUT, wrapper.getPeople());
	}

	@Override
	public void onFailure(@NonNull Call call, @NonNull IOException e) {
		e.printStackTrace();
	}

	@Override
	public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
		Looper.prepare();
		try {
			ResponseBody body = response.body();
			String res = null;
			if (body == null || (res = body.string()).contains("errors")) {
				Log.i(TAG, "onError: " + res);
				Toast.makeText(wrapper, "Something went wrong\nUnable to update server", Toast.LENGTH_LONG).show();
				result.finish();
				return;
			}
			Log.i(TAG, "onResponse: " + res);
			Intent mapsIntent = null;
			String location = intent.getStringExtra("location");
			switch (method) {
				case "acceptSOS":
					wrapper.getPeople()
						.add(addNewPerson(requestID, "client",
							new JSONObject(res).optJSONObject("data").optJSONObject("acceptSOS")));
					wrapper.setLocation(parseLocation(location));
					if (Objects.equals(intent.getAction(), "accept"))
						mapsIntent = new Intent(Intent.ACTION_VIEW,
							new Uri.Builder()
								.scheme("https")
								.authority("www.google.com")
								.appendPath("maps")
								.appendPath("dir")
								.appendPath("")
								.appendQueryParameter("api", "1")
								.appendQueryParameter("destination", location).build());
					break;
				case "acceptTransitionRequest":
					wrapper.getPeople()
						.add(addNewPerson(requestID, "doctor",
							new JSONObject(res).optJSONObject("data").optJSONObject("acceptTransitRequest")));
					wrapper.setLocation(parseLocation(location));
					mapsIntent = new Intent(Intent.ACTION_VIEW,
						new Uri.Builder()
							.scheme("https")
							.authority("www.google.com")
							.appendPath("maps")
							.appendPath("dir")
							.appendPath("")
							.appendQueryParameter("api", "1")
							.appendQueryParameter("destination", location).build());
					break;
			}
			Toast.makeText(wrapper, "Server updated!", Toast.LENGTH_LONG).show();
			wrapper.sendBroadcast(new Intent(ApplicationWrapper.ACTION_PUSH_NOTIFICATION));
			if (mapsIntent != null) wrapper.startActivity(mapsIntent);
			result.finish();
		} catch (Exception e) {
			e.printStackTrace();
		}
		Looper.loop();
	}

	private LatLng parseLocation(String locationString) {
		String[] comps = locationString.split(",");
		return new LatLng(Double.parseDouble(comps[0]), Double.parseDouble(comps[1]));
	}
}