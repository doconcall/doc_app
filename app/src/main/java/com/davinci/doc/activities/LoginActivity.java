package com.davinci.doc.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.davinci.doc.ApplicationWrapper;
import com.davinci.doc.R;
import com.google.firebase.iid.FirebaseInstanceId;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class LoginActivity extends AppCompatActivity
	implements OnClickListener, OnItemSelectedListener, Callback {
	Intent result = null;
	boolean signingUp = false;
	String type = "client";
	AlertDialog loadingDialog = null;

	ApplicationWrapper wrapper = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		wrapper = (ApplicationWrapper) getApplication();
		//initialize the spinner
		setContentView(R.layout.activity_login);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.typeAdapter, R.layout.item_type);
		adapter.setDropDownViewResource(R.layout.item_type);
		Spinner spinner = findViewById(R.id.typeSelector);
		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(this);
		
		findViewById(R.id.accept).setOnClickListener(this);
		//set text entered in fields if orientation was changed
		if (savedInstanceState != null) {
			signingUp = savedInstanceState.getBoolean("signingUp", false);
			type = savedInstanceState.getString("type", "client");
			getEditText(R.id.email).setText(savedInstanceState.getString("email", ""));
			getEditText(R.id.password).setText(savedInstanceState.getString("password", ""));
			getEditText(R.id.name).setText(savedInstanceState.getString("name", ""));
			getEditText(R.id.phone).setText(savedInstanceState.getString("phone", ""));
			getEditText(R.id.designation).setText(savedInstanceState.getString("designation", ""));
			getEditText(R.id.history).setText(savedInstanceState.getString("history", ""));
			showSignup();
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		//save text fields' values before orientation change
		outState.putBoolean("signingUp", signingUp);
		outState.putString("type", type);
		outState.putString("email", getEditText(R.id.email).getText().toString());
		outState.putString("password", getEditText(R.id.password).getText().toString());
		outState.putString("name", getEditText(R.id.name).getText().toString());
		outState.putString("phone", getEditText(R.id.phone).getText().toString());
		outState.putString("designation", getEditText(R.id.designation).getText().toString());
		outState.putString("history", getEditText(R.id.history).getText().toString());
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
			//we mutate layout to show signup
			case R.id.signup:
				signingUp = true;
				findViewById(R.id.signup).setVisibility(View.GONE);
				showSignup();
				break;
			//method to sign in or sign up
			case R.id.accept:
				String email = getEditText(R.id.email).getText().toString().trim(),
					password = getEditText(R.id.password).getText().toString();
				//return if invalid values were provided
				if (email.equals("") || password.equals("")) {
					Toast.makeText(this, "Please provide valid credentials!", Toast.LENGTH_LONG).show();
					return;
				}
				//return if internet was not accessible
				if (!ApplicationWrapper.isNetworkConnected(this) || !ApplicationWrapper.isInternetAccessible()) {
					Toast.makeText(this, "Internet not accessible", Toast.LENGTH_SHORT).show();
					return;
				}
				//show a dialog that we're trying to log in
				loadingDialog = getLoginDialog();
				loadingDialog.show();
				try {
					//make the sign up or sign in network request
					wrapper.getClient()
						.newCall(wrapper.getPreparedRequest(signingUp ?
							"mutation{" + (type.equals("client") ? "newClient" : type.equals("doctor") ? "newDoctor" : "newTransit") +
								"(" + type + ":\"" + getNewPerson() + "\"" + "){email," +
								(!type.equals("client") ? "accepted,total," : "") +
								"info{name,phone" +
								(type.equals("client") ? ",history" : type.equals("doctor") ? ",designation" : "") +
								"}}}" :
							"query{" + type +
								"(email:\"" + email + "\"" +
								" password:\"" + password + "\"){email," +
								(!type.equals("client") ? "accepted,total," : "") +
								"info{name,phone" +
								(type.equals("client") ? ",history" : type.equals("doctor") ? ",designation" : "") +
								"}}}", "login"))
						.enqueue(this);
				} catch (JSONException e) {
					e.printStackTrace();
				}
				break;
		}
	}

	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
		String type = ((String) adapterView.getItemAtPosition(i)).toLowerCase();
		if (type.equals(this.type)) return;
		//get the selected user type
		this.type = type.equals("transit service") ? "transit" : type;
		//return if we're not signing up
		if (!signingUp) return;
		showSignup();
	}

	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {
		//set default user type to client
		((Spinner) findViewById(R.id.typeSelector)).setSelection(0);
	}

	@Override
	public void onBackPressed() {
		//if user has opted to signup, we hide those additional fields
		if (signingUp) {
			signingUp = false;
			hideSignup();
			return;
		}
		//else we simply exit
		super.onBackPressed();
	}

	@Override
	public void onFailure(@NonNull Call call, @NonNull IOException e) {
		e.printStackTrace();
		runOnUiThread(() -> {
			loadingDialog.hide();
			loadingDialog = null;
			Toast.makeText(LoginActivity.this, "Something went wrong\nCheck logs", Toast.LENGTH_LONG).show();
		});
	}

	@Override
	public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
		//dismiss the dialog that shows we're logging in
		loadingDialog.dismiss();
		loadingDialog = null;
		ResponseBody body = response.body();
		String res = body != null ? body.string() : null;
		Log.i(ApplicationWrapper.TAG, "onResponse: " + res);
		runOnUiThread(() -> {
			//if we've an error, we show appropriate msg
			String error = res == null ? "Something went wrong" :
				res.contains("error") ? res.contains("404") ? "Email not found\nPlease signup" : "Provide valid credentials!" : null;
			if (error != null) {
				runOnUiThread(() -> Toast.makeText(LoginActivity.this, error, Toast.LENGTH_LONG).show());
				return;
			}
			
			//set those credentials in application
			try {
				wrapper.setEmail(getEditText(R.id.email).getText().toString().trim())
					.setPassword(getEditText(R.id.password).getText().toString())
					.setType(type)
					.setInfo(res);
			} catch (JSONException e) {
				e.printStackTrace();
			}
			//successfully logged in
			setResult(Activity.RESULT_OK);
			//we return to the parent activity
			finish();
		});
	}
	
	//convenience method to toggle the visibility of view with given id
	private void toggleVisibility(int id, boolean visible) {
		findViewById(id).setVisibility(visible ? View.VISIBLE : View.GONE);
	}
	
	//convenience method for to show additional fields
	private void showSignup() {
		if (!signingUp) return;
		toggleVisibility(R.id.signup, false);
		switch (type) {
			case "client":
				toggleVisibility(R.id.name, true);
				toggleVisibility(R.id.phone, true);
				toggleVisibility(R.id.designation, false);
				toggleVisibility(R.id.history, true);
				break;
			case "doctor":
				toggleVisibility(R.id.name, true);
				toggleVisibility(R.id.phone, true);
				toggleVisibility(R.id.designation, true);
				toggleVisibility(R.id.history, false);
				break;
			case "transit":
				toggleVisibility(R.id.name, true);
				toggleVisibility(R.id.phone, true);
				toggleVisibility(R.id.designation, false);
				toggleVisibility(R.id.history, false);
				break;
		}
	}
	
	//convenience method to hide the additional fields
	private void hideSignup() {
		if (signingUp) return;
		getEditText(R.id.password).setImeOptions(EditorInfo.IME_ACTION_GO);
		toggleVisibility(R.id.signup, true);
		toggleVisibility(R.id.name, false);
		toggleVisibility(R.id.phone, false);
		toggleVisibility(R.id.designation, false);
		toggleVisibility(R.id.history, false);
	}
	
	//convenience method to build the logging in dialog
	private AlertDialog getLoginDialog() {
		ConstraintLayout root = (ConstraintLayout) LayoutInflater.from(this).inflate(R.layout.dialog_login, findViewById(R.id.root), false);
		((TextView) root.findViewById(R.id.content))
			.setText(signingUp ? R.string.signup_content : R.string.login_content);
		return new AlertDialog.Builder(this)
			.setTitle(R.string.login_title)
			.setIcon(R.mipmap.ic_launcher)
			.setView(root)
			.setPositiveButton("", (dialogInterface, i) -> {
			})
			.setCancelable(false)
			.create();
	}
	
	//convenience method to get the editext with given id
	private EditText getEditText(int Id) {
		return ((TextInputLayout) findViewById(Id)).getEditText();
	}
	
	//convenience method to build the user fields we want to retrieve from the server
	private String getNewPerson() throws JSONException {
		JSONObject person = new JSONObject();
		person.putOpt("email", getEditText(R.id.email).getText().toString().trim());
		person.putOpt("password", getEditText(R.id.password).getText().toString());
		person.putOpt("deviceID", FirebaseInstanceId.getInstance().getToken());
		JSONObject info = new JSONObject();
		info.putOpt("name", getEditText(R.id.name).getText().toString().trim());
		info.putOpt("phone", getEditText(R.id.phone).getText().toString().trim());
		switch (type) {
			case "client":
				info.putOpt("history", getEditText(R.id.history).getText().toString().trim().replace("\n", "\\n"));
				break;
			case "doctor":
				info.putOpt("designation", getEditText(R.id.designation).getText().toString().trim());
				break;
		}
		person.putOpt("info", info);
		return person.toString().replace("\"", "\\\"");
	}
}