package com.davinci.doc.custom;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.v4.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by aakash on 9/20/17.
 * Custom location provider extended from the native api
 * handles all the clean up and irrelevant stubs
 */
public class FusedLocationProvider extends LocationCallback
	implements OnSuccessListener<Location> {
	private FusedLocationProviderClient locationProvider;
	private LocationChangedListener locationChangedListener;

	public FusedLocationProvider(Context context, LocationChangedListener locationChangedListener) {
		locationProvider = new FusedLocationProviderClient(context);
		this.locationChangedListener = locationChangedListener;
	}
	
	public void getUpdates(LocationRequest request) {
		if (this.locationProvider == null || ActivityCompat.checkSelfPermission(locationProvider.getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
			return;
		locationProvider.getLastLocation()
			.addOnSuccessListener(this);
		this.locationProvider.requestLocationUpdates(request, this, null);
	}

	public void stopUpdates() {
		this.locationProvider.removeLocationUpdates(this);
	}

	@Override
	public void onLocationResult(LocationResult locationResult) {
		if (this.locationChangedListener == null) return;
		this.locationChangedListener.onLocationChanged(locationResult.getLocations());
	}

	@Override
	public void onSuccess(Location location) {
		if (this.locationChangedListener == null) return;
		ArrayList<Location> locations = new ArrayList<>();
		locations.add(location);
		this.locationChangedListener.onLastLocation(locations);
	}
	
	//custom callback if location is available
	public interface LocationChangedListener {
		void onLastLocation(List<Location> locations);

		void onLocationChanged(List<Location> locations);
	}
}
