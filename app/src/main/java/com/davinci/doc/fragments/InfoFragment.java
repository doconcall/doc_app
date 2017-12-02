package com.davinci.doc.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.davinci.doc.ApplicationWrapper;
import com.davinci.doc.ApplicationWrapper.OnItemSelectedListener;
import com.davinci.doc.R;
import com.davinci.doc.activities.MainActivity;
import com.davinci.doc.adapters.InfoAdapter;
import com.davinci.doc.adapters.InfoAdapter.InfoItem;
import com.davinci.doc.services.LocationService;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Created by aakash on 10/24/17.
 * Information fragment page of the user
 */
public class InfoFragment extends Fragment
	implements OnItemSelectedListener,
	Callback, OnClickListener {
	
	//static fragment builder method with given data
	public static InfoFragment newInstance(Object data) {
		Bundle args = new Bundle();
		InfoFragment fragment = new InfoFragment();
		fragment.data = (JSONObject) data;
		fragment.setArguments(args);
		return fragment;
	}
	
	//the actual data to show in the recycler view
	JSONObject data = null;
	//holds the id of field which is being edited
	int editing = -1;
	//the actual edit text in the alert dialog, reapply the same if orientation was changed
	String editedString = null;
	//we've successfully edited at least one field
	boolean edited = false;
	
	ApplicationWrapper wrapper = null;
	ConstraintLayout root = null;
	RecyclerView info = null;
	InfoAdapter adapter = null;
	AlertDialog dialog = null;
	
	//user information
	ArrayList<InfoItem> infoItems = new ArrayList<>();
	
	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//hold the application instance
		wrapper = (ApplicationWrapper) getActivity().getApplication();
	}
	
	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		setRetainInstance(true);
		root = (ConstraintLayout) inflater.inflate(R.layout.fragment_info, container, false);
		info = root.findViewById(R.id.info);
		info.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
		info.setAdapter(adapter = new InfoAdapter(infoItems, this));
		if (data != null) init();
		View view = root.findViewById(R.id.save);
		view.setOnClickListener(this);
		view.setEnabled(edited);
		root.findViewById(R.id.logout).setOnClickListener(this);
		return root;
	}
	
	@Override
	public void onDestroy() {
		//clear all fields if the fragment was destroyed
		infoItems.clear();
		super.onDestroy();
	}
	
	@Override
	public void onClick(View view) {
		switch (view.getId()) {
			//user wants to save the edited details,
			//we clear the dialog, extract the text and update the server
			case R.id.save:
				try {
					view.setEnabled(false);
					String type = wrapper.getType();
					JSONObject mutated = data.getJSONObject("data").getJSONObject(type);
					ConstraintLayout root = (ConstraintLayout) LayoutInflater.from(getContext())
						.inflate(R.layout.dialog_login, InfoFragment.this.root, false);
					((TextView) root.findViewById(R.id.content))
						.setText(R.string.updatingInfo);
					dialog = new AlertDialog.Builder(getContext())
						.setTitle(R.string.login_title)
						.setIcon(R.mipmap.ic_launcher)
						.setView(root)
						.setCancelable(false)
						.create();
					dialog.show();
					String suffix = wrapper.getType();
					wrapper.getClient()
						.newCall(wrapper.getPreparedRequest("mutation{" +
							"update" + suffix.substring(0, 1).toUpperCase() + suffix.substring(1)
							+ "(email:\"" + wrapper.getEmail() + "\" " +
							"password:\"" + wrapper.getPassword() + "\" " +
							"mutation:\"" + mutated.toString()
							.replace("\\n", "\\\\n")
							.replace("\"", "\\\"") + "\"){email," +
							(!type.equals("client") ? "accepted,total," : "") +
							"info{name,phone" +
							(type.equals("client") ? ",history" : type.equals("doctor") ? ",designation" : "") +
							"}}}", "update"))
						.enqueue(this);
				} catch (JSONException e) {
					e.printStackTrace();
				}
				break;
			//user wants to logout, we stop any location services, clear all fields and exit
			case R.id.logout:
				//show confirmation dialog
				dialog = new AlertDialog.Builder(getContext())
					.setTitle(R.string.logout)
					.setIcon(R.mipmap.ic_launcher)
					.setMessage(R.string.confirmLogout)
					.setNegativeButton(android.R.string.cancel, (dialogInterface, i) -> dialogInterface.dismiss())
					.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
						int ID = wrapper.getPersistentId();
						MainActivity activity = (MainActivity) getActivity();
						if (ID != -1)
							activity.stopService(new Intent(activity, LocationService.class));
						wrapper.getClient()
							.newCall(wrapper.getPreparedRequest("mutation{updateDeviceID(" +
								"email:\"" + wrapper.getEmail() + "\" " +
								"password:\"" + wrapper.getPassword() + "\" " +
								"type:\"" + wrapper.getType() + "\" " +
								"deviceID:\"null\"" +
								")}", "logout"))
							.enqueue(this);
					})
					.create();
				dialog.show();
				break;
		}
	}
	
	@Override
	public void OnItemSelected(Object item, View view) {
		//show the edit text dialog with appropriate title
		showEditable(((InfoItem) item).getID(), ((InfoItem) item).getSubtitle());
	}
	
	@Override
	public void onFailure(@NonNull Call call, @NonNull IOException e) {
		e.printStackTrace();
	}
	
	@Override
	public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
		ResponseBody body = response.body();
		if (dialog != null) {
			dialog.dismiss();
			dialog = null;
		}
		//extract the response body and return if we error out
		String res = null, tag = (String) call.request().tag();
		if (body == null || (res = body.string()).contains("error")) {
			Log.i(ApplicationWrapper.TAG, "onResponse: Error(" + res + ")");
			MainActivity activity = (MainActivity) getActivity();
			activity.runOnUiThread(() ->
				Toast.makeText(this.getContext(),
					"Something went wrong\nUnable to update server", Toast.LENGTH_LONG).show());
			return;
		}
		MainActivity activity = (MainActivity) getActivity();
		switch (tag) {
			case "update":
				try {
					//set the data and refresh
					data = wrapper.setInfo(res);
					activity.runOnUiThread(activity::refresh);
				} catch (JSONException e) {
					e.printStackTrace();
				}
				break;
			case "logout":
				//we logged out, we recreate the activity
				wrapper.logout();
				activity.runOnUiThread(activity::recreate);
				break;
		}
	}
	
	//set data and initialize if fragment was attached to the activity
	public void setData(JSONObject data) {
		this.data = data;
		if (isAdded()) init();
	}
	
	//parse the json and initialize the adapter
	private void init() {
		infoItems.clear();
		adapter.notifyDataSetChanged();
		if (data == null) return;
		String type = wrapper.getType();
		try {
			JSONObject root = data
				.optJSONObject("data")
				.optJSONObject(type);
			if (root == null) return;
			infoItems.add(new InfoItem("Email ID", wrapper.getEmail(), R.id.email, false));
			infoItems.add(new InfoItem("Password", "Change current password", R.id.password, true));
			JSONObject info = root.getJSONObject("info");
			infoItems.add(new InfoItem("Name", info.getString("name"), R.id.name, true));
			infoItems.add(new InfoItem("Phone", info.getString("phone"), R.id.phone, true));
			if (!type.equals("client")) {
				if (type.equals("doctor"))
					infoItems.add(new InfoItem("Designation", info.getString("designation"), R.id.designation, true));
				infoItems.add(new InfoItem("Accepted Requests", String.valueOf(root.getInt("accepted")), R.id.accepted, false));
				infoItems.add(new InfoItem("Total Requests", String.valueOf(root.getInt("total")), R.id.total, false));
			} else
				infoItems.add(new InfoItem("History", info.getString("history"), R.id.history, true));
			adapter.notifyDataSetChanged();
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}
	
	//hint to be shown in dialog according to the field we want to edit
	private String getHint(int id) {
		String hint = "Changed ";
		switch (editing = id) {
			case R.id.password:
				hint += "Password";
				break;
			case R.id.name:
				hint += "Name";
				break;
			case R.id.phone:
				hint += "Phone no";
				break;
			case R.id.history:
				hint += "Medical History";
				break;
			case R.id.designation:
				hint += "Designation";
				break;
		}
		return hint;
	}
	
	//get the relevant key to mutate in the json object according to the field we've edited
	private String getKey(int id) {
		switch (id) {
			case R.id.password:
				return "password";
			case R.id.name:
				return "name";
			case R.id.phone:
				return "phone";
			case R.id.history:
				return "history";
			case R.id.designation:
				return "designation";
		}
		return null;
	}
	
	//search relevant item that holds individual information of the user
	private InfoItem search(int id) {
		for (InfoItem infoItem : infoItems)
			if (infoItem.getID() == id)
				return infoItem;
		return null;
	}
	
	//show edit text dialog with appropriate title
	private void showEditable(int id, String text) {
		TextInputLayout root = (TextInputLayout) LayoutInflater.from(getContext())
			.inflate(R.layout.dialog_editable, this.root, false);
		root.setHint(getHint(id));
		root.setId(id);
		EditText editText = root.getEditText();
		if (editText == null) return;
		if (text != null) {
			editText.setText(text);
			editText.setSelection(text.length());
		}
		editText.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
			
			}
			
			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
			
			}
			
			@Override
			public void afterTextChanged(Editable editable) {
				editedString = editable.toString();
			}
		});
		dialog = new AlertDialog.Builder(getContext())
			.setTitle(R.string.editDialogTitle)
			.setIcon(R.drawable.edit)
			.setView(root)
			.setNegativeButton(android.R.string.cancel, (dialogInterface, i) -> {
				if (dialog != null) {
					dialog.dismiss();
					dialog = null;
					editing = -1;
				}
			})
			.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
				int ID = root.getId();
				String newText = root.getEditText().getText().toString(),
					key = getKey(ID);
				if (key == null) return;
				InfoItem infoItem = search(ID);
				if (infoItem != null && !infoItem.getTitle().equals("Password"))
					infoItem.setSubtitle(newText);
				adapter.notifyDataSetChanged();
				//mutate the information
				try {
					if (key.equals("password")) {
						data.putOpt(key, newText);
					} else {
						JSONObject info = data.optJSONObject("data")
							.optJSONObject(wrapper.getType())
							.optJSONObject("info");
						if (info != null)
							info.putOpt(key, newText);
					}
				} catch (JSONException e) {
					e.printStackTrace();
				}
				InfoFragment.this.root.findViewById(R.id.save).setEnabled(edited = true);
				dialog.dismiss();
				dialog = null;
				editing = -1;
				editedString = null;
			})
			.create();
		dialog.show();
		//show keyboard
		dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
	}
}
