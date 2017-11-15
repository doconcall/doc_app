package com.davinci.doc.activities;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.design.widget.TextInputEditText;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.davinci.doc.ApplicationWrapper;
import com.davinci.doc.R;
import com.davinci.doc.adapters.ViewPagerAdapter;
import com.davinci.doc.custom.FragmentDataItem;
import com.davinci.doc.custom.FusedLocationProvider;
import com.davinci.doc.custom.FusedLocationProvider.LocationChangedListener;
import com.davinci.doc.fragments.InfoFragment;
import com.davinci.doc.fragments.RequestFragment;
import com.davinci.doc.services.LocationService;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.model.LatLng;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static android.view.View.GONE;

public class MainActivity extends AppCompatActivity
	implements OnClickListener, Callback, LocationChangedListener {
	
	ApplicationWrapper wrapper = null;
	ViewPagerAdapter adapter = null;
	ArrayList<FragmentDataItem> fragmentData = new ArrayList<>();
	ViewPager viewPager = null;
	
	boolean orientation = false;
	LocationRequest request = null;
	FusedLocationProvider locationProvider = null;
	LatLng lastLocation = null;
	AlertDialog dialog = null;
	
	BroadcastReceiver pushNotificationListener = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			refresh();
			String msg = intent.getStringExtra("msg");
			if (msg != null) Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		wrapper = (ApplicationWrapper) getApplication();
		findViewById(R.id.refresh).setOnClickListener(this);
		
		viewPager = findViewById(R.id.viewPager);
		adapter = new ViewPagerAdapter(getSupportFragmentManager(), fragmentData);
		ViewPager viewPager = findViewById(R.id.viewPager);
		viewPager.setAdapter(adapter);
		((TabLayout) findViewById(R.id.tabIndicator))
			.setupWithViewPager(viewPager, true);
		
		View view = findViewById(R.id.sos);
		view.setOnClickListener(this);
		setCoordinates(wrapper.getLocation());
		init();
		if (savedInstanceState != null)
			orientation = savedInstanceState.getBoolean("orientation");
		else wrapper.clear();
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putBoolean("orientation", orientation);
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		if (wrapper.getEmail().equals(""))
			startActivityForResult(new Intent(this, LoginActivity.class), 0);
		else {
			if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
				!= PackageManager.PERMISSION_GRANTED)
				ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1111);
			else startLocationService();
			if (!ApplicationWrapper.isNetworkConnected(this) || !ApplicationWrapper.isInternetAccessible()) {
				Toast.makeText(this, "Internet not accessible", Toast.LENGTH_SHORT).show();
				return;
			}
			fetchData();
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode != 0) return;
		switch (resultCode) {
			case Activity.RESULT_OK:
				wrapper.getClient()
					.newCall(wrapper.
						getPreparedRequest("mutation{updateDeviceID(" +
							"email:\"" + wrapper.getEmail() + "\" " +
							"password:\"" + wrapper.getPassword() + "\" " +
							"type:\"" + wrapper.getType() + "\" " +
							"deviceID:\"" + wrapper.getDeviceID() + "\"" +
							")}", "deviceID"))
					.enqueue(this);
				setCoordinates(null);
				break;
			default:
				finish();
		}
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		if (locationProvider != null && request != null) locationProvider.getUpdates(request);
		registerReceiver(pushNotificationListener, new IntentFilter(ApplicationWrapper.ACTION_PUSH_NOTIFICATION));
	}
	
	@Override
	protected void onPause() {
		unregisterReceiver(pushNotificationListener);
		if (locationProvider != null && request != null) locationProvider.stopUpdates();
		super.onPause();
	}
	
	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		if (requestCode != 1111) return;
		if (grantResults.length < 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
			Toast.makeText(this, "Enable location permission to avail this service", Toast.LENGTH_LONG).show();
			//finish();
			return;
		}
		Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show();
		startLocationService();
	}
	
	@Override
	public void onClick(View view) {
		switch (view.getId()) {
			case R.id.refresh:
				refresh();
				Toast.makeText(this, "Refreshing data", Toast.LENGTH_LONG).show();
				break;
			case R.id.sos:
				if (wrapper.getType().equals("client")) showNoteDialog();
				else {
					LatLng location = wrapper.getLocation();
					if (location == null) return;
					startActivity(new Intent(Intent.ACTION_VIEW, new Uri.Builder()
						.scheme("https")
						.authority("www.google.com")
						.appendPath("maps")
						.appendPath("dir")
						.appendPath("")
						.appendQueryParameter("api", "1")
						.appendQueryParameter("destination", location.latitude + "," + location.longitude).build()));
				}
				break;
		}
	}
	
	@Override
	public void onLastLocation(List<Location> locations) {
		Location location = locations.get(0);
		lastLocation = new LatLng(location.getLatitude(), location.getLongitude());
	}
	
	@Override
	public void onLocationChanged(List<Location> locations) {
		Location location = locations.get(0);
		lastLocation = new LatLng(location.getLatitude(), location.getLongitude());
		Log.i(ApplicationWrapper.TAG, "onLocationChanged: " + lastLocation);
	}
	
	@Override
	public void onFailure(@NonNull Call call, @NonNull IOException e) {
		e.printStackTrace();
	}
	
	@Override
	public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
		ResponseBody body = response.body();
		runOnUiThread(() -> {
			if (dialog != null) {
				dialog.dismiss();
				dialog = null;
			}
			String res = null;
			try {
				if (body == null || (res = body.string()).contains("errors")) {
					Log.i(ApplicationWrapper.TAG, "onResponse: Error(" + res + ")");
					MainActivity.this.runOnUiThread(() ->
						Toast.makeText(MainActivity.this,
							"Something went wrong\nUnable to update server", Toast.LENGTH_LONG).show());
					return;
				}
				Log.i(ApplicationWrapper.TAG, "onResponse: " + res);
				switch ((String) call.request().tag()) {
					case "deviceID":
						Toast.makeText(MainActivity.this,
							"Server updated successfully", Toast.LENGTH_LONG).show();
						break;
					case "sos":
						wrapper.setLatestSOS(res)
							.extendRange(60000);
						refresh();
						Toast.makeText(this, "SOS broadcast successful", Toast.LENGTH_LONG).show();
						break;
					case "info":
						try {
							FragmentDataItem dataItem = findDataItem("Info");
							if (dataItem == null) {
								fragmentData.add(0, new FragmentDataItem("Info", wrapper.setInfo(res)));
								adapter.notifyDataSetChanged();
							} else {
								dataItem.setData(wrapper.setInfo(res));
								InfoFragment fragment = (InfoFragment) getPage(0);
								if (fragment != null)
									fragment.setData((JSONObject) dataItem.getData());
							}
						} catch (JSONException e) {
							e.printStackTrace();
						}
						break;
					case "sosHistory":
						try {
							FragmentDataItem dataItem = findDataItem("SOS History");
							if (dataItem == null) {
								fragmentData.add(1, new FragmentDataItem("SOS History", wrapper.setSOSHistory(res)));
								adapter.notifyDataSetChanged();
							} else {
								dataItem.setData(wrapper.setSOSHistory(res));
								RequestFragment fragment = (RequestFragment) getPage(1);
								if (fragment != null)
									fragment.setData((JSONObject) dataItem.getData());
							}
						} catch (JSONException e) {
							e.printStackTrace();
						}
						break;
					case "transitHistory":
						try {
							FragmentDataItem dataItem = findDataItem("Transit History");
							if (dataItem == null)
								fragmentData.add(new FragmentDataItem("Transit History", wrapper.setTransitHistory(res)));
							else {
								dataItem.setData(wrapper.setTransitHistory(res));
								RequestFragment fragment = (RequestFragment) getPage(wrapper.getType().equals("doctor") ? 2 : 1);
								if (fragment != null)
									fragment.setData((JSONObject) dataItem.getData());
							}
							adapter.notifyDataSetChanged();
						} catch (JSONException e) {
							e.printStackTrace();
						}
						break;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}
	
	@Override
	public void onBackPressed() {
		if (viewPager.getCurrentItem() != 0) {
			viewPager.setCurrentItem(0);
			return;
		}
		super.onBackPressed();
	}
	
	private Fragment getPage(int position) {
		return getSupportFragmentManager().findFragmentByTag("android:switcher:" + R.id.viewPager + ":" + position);
	}
	
	private void init() {
		String type = wrapper.getType();
		fragmentData.clear();
		fragmentData.add(0, new FragmentDataItem("Info", wrapper.getInfo()));
		if (!type.equals("transit"))
			fragmentData.add(new FragmentDataItem("SOS History", wrapper.getSOSHistory()));
		if (!type.equals("client"))
			fragmentData.add(new FragmentDataItem("Transit History", wrapper.getTransitHistory()));
		adapter.notifyDataSetChanged();
	}
	
	@Nullable
	private FragmentDataItem findDataItem(String id) {
		for (FragmentDataItem item : fragmentData)
			if (item.getId().equals(id)) return item;
		return null;
	}
	
	private void fetchData() {
		JSONObject data;
		if ((data = wrapper.getInfo()) == null)
			requestInfo();
		else {
			FragmentDataItem dataItem = findDataItem("Info");
			if (dataItem != null) {
				dataItem.setData(data);
				InfoFragment fragment = (InfoFragment) getPage(0);
				if (fragment != null)
					fragment.setData((JSONObject) dataItem.getData());
			}
		}
		String type = wrapper.getType();
		switch (type) {
			case "client":
				if (wrapper.getSOSHistory() == null) requestSOSHistory();
				request = new LocationRequest()
					.setInterval(30000)
					.setFastestInterval(30000)
					.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
				locationProvider = new FusedLocationProvider(this, this);
				break;
			case "doctor":
				if (wrapper.getSOSHistory() == null) requestSOSHistory();
				if (wrapper.getTransitHistory() == null) requestTransitHistory();
				break;
			case "transit":
				if (wrapper.getTransitHistory() == null) requestTransitHistory();
				break;
		}
	}
	
	private void requestInfo() {
		String type = wrapper.getType();
		wrapper.getClient()
			.newCall(wrapper
				.getPreparedRequest("query{" + type + "(email:\"" + wrapper.getEmail() +
					"\" password:\"" + wrapper.getPassword() + "\"){" +
					(type.equals("doctor") || type.equals("transit") ? "accepted,total," : "") +
					"info{name,phone" +
					(type.equals("client") ? ",history" : type.equals("doctor") ? ",designation" : "") + "}}}", "info"))
			.enqueue(this);
	}
	
	private void requestSOSHistory() {
		wrapper.getClient()
			.newCall(wrapper
				.getPreparedRequest("query{sosHistory(" +
					"email:\"" + wrapper.getEmail() + "\" " +
					"password:\"" + wrapper.getPassword() + "\" " +
					"type:\"" + wrapper.getType() + "\")" +
					"{id,lat,lon,note,resolved,cid,createdAt,fulfilled}}", "sosHistory"))
			.enqueue(this);
	}
	
	private void requestTransitHistory() {
		wrapper.getClient()
			.newCall(wrapper
				.getPreparedRequest("query{transitHistory(" +
					"email:\"" + wrapper.getEmail() + "\" " +
					"password:\"" + wrapper.getPassword() + "\" " +
					"type:\"" + wrapper.getType() + "\")" +
					"{id,lat,lon,note,resolved,did,createdAt,fulfilled}}", "transitHistory"))
			.enqueue(this);
	}
	
	public void refresh() {
		requestInfo();
		String type = wrapper.getType();
		if (!type.equals("transit"))
			requestSOSHistory();
		if (!type.equals("client"))
			requestTransitHistory();
		setCoordinates(wrapper.getLocation());
	}
	
	private void startLocationService() {
		if (!wrapper.getType().equals("client") && wrapper.getPersistentId() == -1 &&
			ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
			&& ApplicationWrapper.isNetworkConnected(this) && ApplicationWrapper.isInternetAccessible())
			startService(new Intent(this, LocationService.class));
		//else Log.i(ApplicationWrapper.TAG, "startLocationService: " + wrapper.getPersistentId());
	}
	
	private void showSOSDialog() {
		ConstraintLayout root = (ConstraintLayout) LayoutInflater.from(this).inflate(R.layout.dialog_login, findViewById(R.id.root), false);
		((TextView) root.findViewById(R.id.content))
			.setText(R.string.sosContent);
		dialog = new AlertDialog.Builder(this)
			.setTitle(R.string.noteTitle)
			.setIcon(R.drawable.heart)
			.setView(root)
			.setCancelable(false)
			.create();
		dialog.show();
	}
	
	private void SOS(String note) throws JSONException {
		JSONObject sos = new JSONObject();
		sos.putOpt("cid", wrapper.getEmail());
		sos.putOpt("lat", lastLocation.latitude);
		sos.putOpt("lon", lastLocation.longitude);
		sos.putOpt("note", note.replace("\n", "\\n"));
		wrapper.getClient()
			.newCall(wrapper.getPreparedRequest("mutation{newSOS(" +
				"email:\"" + wrapper.getEmail() + "\" " +
				"password:\"" + wrapper.getPassword() + "\" " +
				"radius:" + 1000 + " " +
				"sos:\"" + sos.toString().replace("\"", "\\\"") +
				"\"){id,cid,note,lat,lon,createdAt,resolved}}", "sos"))
			.enqueue(this);
	}
	
	private void showNoteDialog() {
		ConstraintLayout root = (ConstraintLayout) LayoutInflater.from(this).inflate(R.layout.dialog_note, findViewById(R.id.root), false);
		TextInputEditText editText = root.findViewById(R.id.note);
		dialog = new AlertDialog.Builder(this)
			.setTitle(R.string.noteTitle)
			.setIcon(R.drawable.heart)
			.setView(root)
			.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
				try {
					dialogInterface.dismiss();
					dialog = null;
					Editable text = editText.getText();
					showSOSDialog();
					SOS(text != null ? text.toString() : "");
				} catch (JSONException e) {
					e.printStackTrace();
				}
			})
			.create();
		dialog.show();
		Window window = dialog.getWindow();
		if (window != null)
			window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
	}
	
	public void setCoordinates(LatLng location) {
		FloatingActionButton sos = findViewById(R.id.sos);
		if (wrapper.getType().equals("client")) {
			sos.setImageResource(R.drawable.heart);
			sos.setVisibility(wrapper.getLatestSOS() == null ? View.VISIBLE : View.GONE);
		} else if (location != null) {
			sos.setImageResource(R.drawable.navigation);
			sos.setVisibility(View.VISIBLE);
		} else sos.setVisibility(GONE);
	}
}