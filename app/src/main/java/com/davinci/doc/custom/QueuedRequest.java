package com.davinci.doc.custom;

import android.app.NotificationManager;
import android.content.Context;

import com.davinci.doc.ApplicationWrapper;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by aakash on 11/3/17.
 */

public class QueuedRequest extends TimerTask {

	private String id = null;
	private Timer timeout = new Timer(true);
	private ApplicationWrapper wrapper = null;
	private int notificationID = -1;

	public QueuedRequest(String id, int notificationID, ApplicationWrapper wrapper) {
		this.id = id;
		this.wrapper = wrapper;
		timeout.schedule(this, ApplicationWrapper.PREVIEW_TIMEOUT);
		this.notificationID = notificationID;
	}

	public String getId() {
		return id;
	}

	@Override
	public void run() {
		clearSelf(true);
	}

	public void clearSelf(boolean shouldRemove) {
		NotificationManager manager = (NotificationManager) wrapper.getSystemService(Context.NOTIFICATION_SERVICE);
		if (manager != null)
			manager.cancel(this.notificationID);
		timeout.cancel();
		wrapper.declineRequest(this.id);
		if (shouldRemove) {
			ArrayList<QueuedRequest> queuedRequests = wrapper.getQueuedRequests();
			queuedRequests.remove(this);
		}
	}
}
