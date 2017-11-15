package com.davinci.doc.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;

import com.davinci.doc.ApplicationWrapper;
import com.davinci.doc.custom.PersonDescription;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

public class NotificationService extends FirebaseMessagingService {
	ApplicationWrapper wrapper = null;

	@Override
	public void onMessageReceived(RemoteMessage remoteMessage) {
		wrapper = (ApplicationWrapper) getApplication();
		Map<String, String> notification = remoteMessage.getData();
		String title = notification.get("title");
		try {
			JSONObject data;
			String type = wrapper.getType();
			if (title == null) return;
			Notification notif = null;
			int id = (int) (Math.random() * 1000);
			switch (title) {
				case "sos":
					data = new JSONObject(notification.get("body"));
					notif = wrapper.getRequestNotification(data.optString("id"), data.optDouble("lat"), data.optDouble("lon"), data.optString("note"), id);
					break;
				case "accept":
					data = new JSONObject(notification.get("body"));
					String requestID = data.optString("requestID");
					wrapper.getPeople().add(addNewPerson(requestID, type, data));
					notif = wrapper.getRequestUpdateNotification(requestID, data.optString("email"));
					wrapper.clearExtender();
					wrapper.setLatestSOS(null);
					break;
				case "resolved":
					wrapper.resolve(notification.get("body"));
					break;
				case "rejection":
					wrapper.setLatestSOS(null);
					//extend range over here
					/*if(wrapper.getType().equals("client")){

					}*/
					break;
			}
			if (notif != null) {
				NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
				if (manager == null) return;
				manager.notify(id, notif);
			}
			sendBroadcast(new Intent(ApplicationWrapper.ACTION_PUSH_NOTIFICATION));
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	PersonDescription addNewPerson(String id, String personType, JSONObject person) throws JSONException {
		return new PersonDescription(id, personType, person)
			.setTimeout(ApplicationWrapper.PREVIEW_TIMEOUT, wrapper.getPeople());
	}
}
