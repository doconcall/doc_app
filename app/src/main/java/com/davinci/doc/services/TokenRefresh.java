package com.davinci.doc.services;

import android.util.Log;

import com.davinci.doc.ApplicationWrapper;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.davinci.doc.ApplicationWrapper.TAG;

public class TokenRefresh extends FirebaseInstanceIdService {
	@Override
	public void onTokenRefresh() {
		super.onTokenRefresh();
		String token = FirebaseInstanceId.getInstance().getToken();
		Log.i(TAG, "onTokenRefresh: " + token);
		ApplicationWrapper applicationWrapper = (ApplicationWrapper) getApplication();
		applicationWrapper.setDeviceID(token);
		if (applicationWrapper.getEmail().equals("")) return;
		try {
			Response response = applicationWrapper.getClient()
				.newCall(new Request.Builder()
					.url(ApplicationWrapper.api)
					.header("Content-Type", "application/graphql")
					.post(RequestBody.create(MediaType.parse("application/text charset=utf-8"),
						"mutation{updateDeviceID(" +
							"email:\"" + applicationWrapper.getEmail() + "\" " +
							"password:\"" + applicationWrapper.getPassword() + "\" " +
							"type:\"" + applicationWrapper.getType() + "\" " +
							"deviceID:\"" + token + "\"" +
							")}"))
					.build()).execute();
			ResponseBody body = response.body();
			if (body != null) Log.i(TAG, "onTokenRefresh: " + body.string());
		} catch (IOException e) {
			e.printStackTrace();
		}
		/*getApplicationContext().getSharedPreferences(ApplicationWrapper.PREFERENCE, MODE_PRIVATE)
			.edit().putString(ApplicationWrapper.INSTANCE_TOKEN, token).apply();
		Toast.makeText(getApplicationContext(), token, Toast.LENGTH_LONG).show();*/
	}
}
