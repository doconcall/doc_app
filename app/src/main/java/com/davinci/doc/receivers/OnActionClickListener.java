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
	
	//Singleton ApplicationWrapper instance
	ApplicationWrapper wrapper;
	//object that will end this thread after our work is finished
	PendingResult result = null;
	//the intent this receiver was invoked with
	Intent intent;
	//method to update the server with of request with the given request ID
	String method = null, requestID = null;

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.i(TAG, "onReceive: " + intent);
		//we return if no internet
		if (!ApplicationWrapper.isNetworkConnected(context) || !ApplicationWrapper.isInternetAccessible())
			return;
		NotificationManager manager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
		if (manager != null)
			manager.cancel(intent.getIntExtra("id", 1));
		//hold the intent this was invoked with
		this.intent = intent;
		//we go async because we're making a network request
		result = goAsync();
		try {
			//hold the singleton ApplicationWrapper instance
			wrapper = (ApplicationWrapper) context.getApplicationContext();
			String action = intent.getAction();
			requestID = intent.getStringExtra("requestID");
			//we return if no action was provided
			if (action == null) {
				result.finish();
				return;
			}
			//get the update method of the server
			method = getMethod(action);
			//update the server
			updateServer(action, requestID, method);
			//arrange transit was clicked, we need to show the note dialog to doctor to make the request
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
	
	//maps the action to server method we need to use to update the server
	String getMethod(String action) {
		String type = wrapper.getType();
		return action.equals("decline") ?
			(type.equals("doctor") ? "declineSOS" : "declineTransitRequest") :
			(type.equals("doctor") ? "acceptSOS" : "acceptTransitRequest");
	}
	
	/**
	 * convenience method to update the server
	 * @param action: will only be accept, decline or transit
	 * @param requestID: the request ID of the sos or transit request to update
	 * @param method: method to update the server with
	 * @throws IOException: OkHttp exception
	 */
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
	
	/**
	 * convenience method to make a new detail with appropriate timeout
	 * @param id: email id of the other user
	 * @param personType: type of the other user
	 * @param person: meta of the other user
	 * @return: PersonDescription with timeout
	 * @throws JSONException: JSON parse exception
	 */
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
		//this method is called in background, so we go to foreground using looper
		Looper.prepare();
		try {
			//get response body, return if any errors
			ResponseBody body = response.body();
			String res = null;
			if (body == null || (res = body.string()).contains("errors")) {
				Log.i(TAG, "onError: " + res);
				Toast.makeText(wrapper, "Something went wrong\nUnable to update server", Toast.LENGTH_LONG).show();
				result.finish();
				return;
			}
			Log.i(TAG, "onResponse: " + res);
			//if the user being doctor or transit service has accepted, we need to launch the maps
			Intent mapsIntent = null;
			//parse the coordinates from the saved intent this receiver was invoked with
			String location = intent.getStringExtra("location");
			switch (method) {
				//if sos was accepted by the doctor, we launch the google maps with parsed location
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
					
				//if transit request was accepted by the transit serve, we launch the google maps with parsed location
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
			//show a toast that server was updated
			Toast.makeText(wrapper, "Server updated!", Toast.LENGTH_LONG).show();
			//notify the activity to update
			wrapper.sendBroadcast(new Intent(ApplicationWrapper.ACTION_PUSH_NOTIFICATION));
			if (mapsIntent != null) wrapper.startActivity(mapsIntent);
			//finish this receiver since we've done our work
			result.finish();
		} catch (Exception e) {
			e.printStackTrace();
		}
		//this is like label goto in c++
		Looper.loop();
	}
	
	//parses location from intent
	private LatLng parseLocation(String locationString) {
		String[] comps = locationString.split(",");
		return new LatLng(Double.parseDouble(comps[0]), Double.parseDouble(comps[1]));
	}
}