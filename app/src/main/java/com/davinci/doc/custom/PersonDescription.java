package com.davinci.doc.custom;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by aakash on 10/30/17.
 * class that holds the details of any user after a positive response
 */
public class PersonDescription extends TimerTask {
	//email and type of the other user
	private String id = null, type = null;
	//details of the user
	private JSONObject meta = null;
	//timeout to remove this object after timeout
	private Timer timeout = new Timer(true);
	//singleton instance created and held in ApplicationWrapper
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
