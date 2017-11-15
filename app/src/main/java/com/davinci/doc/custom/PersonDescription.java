package com.davinci.doc.custom;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by aakash on 10/30/17.
 */

public class PersonDescription extends TimerTask {
	private String id = null, type = null;
	private JSONObject meta = null;
	private Timer timeout = new Timer(true);
	private ArrayList<PersonDescription> personDescriptions = null;

	public PersonDescription(String id, String type, JSONObject meta) throws JSONException {
		this.id = id;
		this.type = type;
		this.meta = meta;
	}

	public String getId() {
		return id;
	}

	public String getType() {
		return type;
	}

	public JSONObject getMeta() {
		return meta;
	}

	public PersonDescription setTimeout(long timeout, ArrayList<PersonDescription> personDescriptions) {
		this.personDescriptions = personDescriptions;
		this.timeout.schedule(this, timeout);
		return this;
	}

	@Override
	public void run() {

		if (personDescriptions != null)
			personDescriptions.remove(this);
	}
}
