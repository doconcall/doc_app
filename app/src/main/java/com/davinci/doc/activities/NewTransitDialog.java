package com.davinci.doc.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import com.davinci.doc.ApplicationWrapper;
import com.davinci.doc.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static android.view.View.OnClickListener;
import static com.davinci.doc.ApplicationWrapper.TAG;

public class NewTransitDialog extends AppCompatActivity
	implements OnClickListener, Callback {

	ApplicationWrapper wrapper = null;
	String requestID = null;
	double lat = Double.MAX_VALUE, lon = Double.MAX_VALUE;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		wrapper = (ApplicationWrapper) getApplication();
		setContentView(R.layout.activity_newtransit);
		setIcon();
		//get the intent this activity was launched with
		Intent intent = getIntent();
		if (intent == null)
			finish();
		else {
			//get the request id, latitude and longitude from that intent
			requestID = intent.getStringExtra("requestID");
			lat = intent.getDoubleExtra("lat", Double.MAX_VALUE);
			lon = intent.getDoubleExtra("lon", Double.MAX_VALUE);
			if (lat == Double.MAX_VALUE || lon == Double.MAX_VALUE) {
				finish();
				return;
			}
		}
		//show keyboard
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
	}

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
			case R.id.ok:
				EditText editText = ((TextInputLayout) findViewById(R.id.note)).getEditText();
				if (editText == null) {
					finish();
					return;
				}
				try {
					//make the network transit request
					transitRequest(editText.getText().toString());
				} catch (JSONException e) {
					e.printStackTrace();
				}
				break;
			case R.id.cancel:
				//exit since user cancelled it
				finish();
				break;
		}
	}

	@Override
	public void onFailure(@NonNull Call call, @NonNull IOException e) {
		e.printStackTrace();
		runOnUiThread(this::finish);
	}

	@Override
	public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
		ResponseBody body = response.body();
		runOnUiThread(() -> {
			try {
				//get the response body and return if we've an error
				String res = null;
				if (body == null || (res = body.string()).contains("errors")) {
					Log.i(TAG, "onError: " + res);
					Toast.makeText(this, "Something went wrong\nUnable to update server", Toast.LENGTH_LONG).show();
					return;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			//show the user we successfully broadcasted the network request
			Toast.makeText(this, "New transit locationRequest broadcasted", Toast.LENGTH_LONG).show();
			//launch the google maps with client's coordinates
			startActivity(new Intent(Intent.ACTION_VIEW,
				new Uri.Builder()
					.scheme("https")
					.authority("www.google.com")
					.appendPath("maps")
					.appendPath("dir")
					.appendPath("")
					.appendQueryParameter("api", "1")
					.appendQueryParameter("destination", lat + "," + lon).build()));
			finish();
		});
	}

	private void setIcon() {
		ActionBar sActionBar = getSupportActionBar();
		if (sActionBar != null) {
			sActionBar.setIcon(R.drawable.ambulance);
			//return;
		}
		android.app.ActionBar actionBar = getActionBar();
		if (actionBar != null) {
			actionBar.setIcon(R.drawable.ambulance);
		}
	}
	
	//convenience method to make the network request using the note extracted
	private void transitRequest(String note) throws JSONException {
		JSONObject request = new JSONObject();
		request.putOpt("id", requestID);
		request.putOpt("did", wrapper.getEmail());
		request.putOpt("lat", lat);
		request.putOpt("lon", lon);
		request.putOpt("note", note.equals("") ? "NONE" : note.replace("\n", "\\n"));
		wrapper.getClient()
			.newCall(wrapper.getPreparedRequest("mutation{newTransitRequest(" +
				"email:\"" + wrapper.getEmail() + "\" " +
				"password:\"" + wrapper.getPassword() + "\" " +
				"request:\"" + request.toString().replace("\"", "\\\"") + "\" " +
				"radius:" + wrapper.getRadius() +
				"){id,did,tids,lat,lon,note,createdAt}}", "transitRequest"))
			.enqueue(this);
	}
}
