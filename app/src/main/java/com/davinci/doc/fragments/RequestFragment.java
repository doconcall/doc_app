package com.davinci.doc.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.TextInputEditText;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.davinci.doc.ApplicationWrapper;
import com.davinci.doc.R;
import com.davinci.doc.activities.MainActivity;
import com.davinci.doc.adapters.RequestAdapter;
import com.davinci.doc.adapters.RequestAdapter.RequestItem;
import com.davinci.doc.custom.PersonDescription;
import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.davinci.doc.ApplicationWrapper.OnItemSelectedListener;
import static com.davinci.doc.ApplicationWrapper.TAG;

/**
 * Created by aakash on 10/28/17.
 * Request history fragment page
 */
public class RequestFragment extends Fragment
	implements OnItemSelectedListener, Callback {
	
	/**
	 * static method to create instance of fragment
	 *
	 * @param type: can only be sosHistory or transitHistory
	 * @param data: the JSON parsed data to show
	 * @return new instance
	 */
	public static RequestFragment newInstance(String type, Object data) {
		Bundle args = new Bundle();
		RequestFragment fragment = new RequestFragment();
		fragment.requestType = type;
		fragment.data = (JSONObject) data;
		fragment.setArguments(args);
		return fragment;
	}
	
	//this type of history this fragment will show
	String requestType = null;
	
	ApplicationWrapper wrapper = null;
	RecyclerView recyclerView = null;
	RequestAdapter adapter = null;
	AlertDialog dialog = null;
	
	//list of requests
	ArrayList<RequestItem> requestItems = new ArrayList<>();
	//JSON data of all requests
	JSONObject data = null;
	
	//holds the request that was accepted
	RequestItem acceptedRequest = null;
	//responsible for searching the details after positive outcome
	String detailID = null, detailType = null;
	
	
	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		wrapper = (ApplicationWrapper) getActivity().getApplication();
	}
	
	@Override @Nullable
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		//initialize recycler view and it's adapter
		setRetainInstance(true);
		recyclerView = (RecyclerView) inflater.inflate(R.layout.fragment_request, container, false);
		recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
		adapter = new RequestAdapter(requestItems, this);
		recyclerView.setAdapter(adapter);
		//get back detailID and detail on orientation change
		if (savedInstanceState != null) {
			detailID = savedInstanceState.getString("detailID");
			detailType = savedInstanceState.getString("detailType");
		}
		init();
		return recyclerView;
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		if (detailID != null) outState.putString("detailID", detailID);
		if (detailType != null) outState.putString("detailType", detailType);
	}
	
	@Override
	public void onResume() {
		super.onResume();
		//show details if detailID and detailType ar not null
		//this is essential as the dialog is reshown if shown already after an orientation change
		if (detailID != null && detailType != null)
			showDetails(detailID, detailType);
	}
	
	@Override
	public void OnItemSelected(Object item, View view) {
		RequestItem requestItem = (RequestItem) item;
		switch (view.getId()) {
			case R.id.accept:
				//Resolve was clicked
				if (requestItem.getStatus() == RequestItem.RESOLVABLE) {
					//determine the if we're resolve an sos or transit request
					String personType = wrapper.getType(),
						method = personType.equals("client") ? "resolveSOS" : "resolveTransitRequest";
					//update the server
					wrapper.getClient()
						.newCall(wrapper.getPreparedRequest("mutation{" + method + "(" +
							"id:\"" + requestItem.getId() + "\" " +
							(personType.equals("client") ? "cid" : "did") + ":\"" + wrapper.getEmail() + "\" " +
							"password:\"" + wrapper.getPassword() + "\"" +
							")}", method))
						.enqueue(this);
					//clear existing sent sos and stop extender
					String latestSOS = wrapper.getLatestSOS();
					if (latestSOS != null && latestSOS.contains(requestItem.getId())) {
						wrapper.setLatestSOS(null);
						wrapper.clearExtender();
					}
				} else {
					//we're accepting a request
					try {
						wrapper.clearQueuedRequests();
						//determine whether we're accepting sos or transit request and update the server
						wrapper.acceptSOSorTransit(requestItem.getId(),
							wrapper.getType().equals("doctor") ? "acceptSOS" : "acceptTransitRequest", this);
					} catch (IOException e) {
						e.printStackTrace();
					}
					acceptedRequest = requestItem;
				}
				break;
			case R.id.decline:
				//determine whether we're declining an sos or transit request
				String personType = wrapper.getType(),
					method = personType.equals("doctor") ? "declineSOS" : "declineTransitRequest";
				//update the server
				wrapper.getClient()
					.newCall(wrapper.getPreparedRequest("mutation{" + method + "(" +
						"id:\"" + requestItem.getId() + "\" " +
						(personType.equals("doctor") ? "did" : "tid") + ":\"" + wrapper.getEmail() + "\" " +
						"password:\"" + wrapper.getPassword() + "\"" +
						")}", method))
					.enqueue(this);
				break;
			default:
				//else we search for detailID in ApplicationWrapper's personDescription list
				//and show it if found
				String type = wrapper.getType();
				showDetails(detailID = requestItem.getId(),
					detailType = type.equals("doctor") ? "client" : type.equals("transit") ? "doctor" : "client");
		}
	}
	
	@Override
	public void onFailure(@NonNull Call call, @NonNull IOException e) {
		e.printStackTrace();
	}
	
	@Override
	public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
		MainActivity activity = (MainActivity) getActivity();
		activity.runOnUiThread(() -> {
			//get the response body and return if we error out
			ResponseBody body = response.body();
			String res = null;
			try {
				if (body == null || (res = body.string()).contains("errors")) {
					Log.i(TAG, "onError: " + res);
					Toast.makeText(this.getContext(),
						"Something went wrong\nUnable to update server", Toast.LENGTH_LONG).show();
					return;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			Log.i(TAG, "onResponse: " + res);
			String finalRes = res;
			switch ((String) call.request().tag()) {
				//server updated with an acceptSOS request
				case "acceptSOS":
					try {
						//we save clients information in ApplicationWrapper's personDescription list
						wrapper.getPeople()
							.add(addNewPerson(acceptedRequest.getId(), "client",
								new JSONObject(finalRes).optJSONObject("data").optJSONObject("acceptSOS")));
					} catch (JSONException e) {
						e.printStackTrace();
					}
					//set navigation location in ApplicationWrapper
					wrapper.setLocation(new LatLng(acceptedRequest.getLat(), acceptedRequest.getLon()));
					//show a dialog if the doctor wants to make a transit request
					dialog = new AlertDialog.Builder(getContext())
						.setTitle(R.string.transitRequestTitle)
						.setIcon(R.drawable.heart)
						.setMessage(R.string.transitRequestContent)
						//dismiss the dialog
						.setNegativeButton(android.R.string.no, (dialogInterface, i) -> {
							if (dialog != null) {
								dialog.dismiss();
								dialog = null;
							}
							acceptedRequest = null;
						})
						//make a transit request
						.setPositiveButton(android.R.string.yes, (dialogInterface, i) -> {
							if (dialog != null) {
								dialog.dismiss();
								dialog = null;
							}
							showNoteDialog(acceptedRequest.getId(), acceptedRequest.getLat(), acceptedRequest.getLon());
							acceptedRequest = null;
						})
						.create();
					dialog.show();
					break;
				case "acceptTransitRequest":
					try {
						//we save the doctor's information in ApplicationWrapper's personDescription list
						wrapper.getPeople()
							.add(addNewPerson(acceptedRequest.getId(), "doctor",
								new JSONObject(finalRes).optJSONObject("data").optJSONObject("acceptTransitRequest")));
					} catch (JSONException e) {
						e.printStackTrace();
					}
					//set navigation location in Application Wrapper
					wrapper.setLocation(new LatLng(acceptedRequest.getLat(), acceptedRequest.getLon()));
					if (dialog == null)
						acceptedRequest = null;
					break;
				case "transitRequest":
					//notify user an transit request was made
					Toast.makeText(getContext(), "Transit request broadcasted", Toast.LENGTH_LONG).show();
					break;
			}
			activity.refresh();
		});
	}
	
	//RequestItem builder from JSON data
	@NonNull
	private RequestItem getRequestItem(JSONObject object, String personType) {
		return new RequestItem(object.optString("id"),
			object.optString(this.requestType.equals("sosHistory") ? "cid" : "did"),
			object.optString("note"),
			object.optDouble("lat"), object.optDouble("lon"),
			getVisibility(personType, object.optBoolean("resolved"), !Objects.equals(object.optString("fulfilled"), "null")),
			Long.parseLong(object.optString("createdAt")));
	}
	
	//sets the JSON data and initializes
	public void setData(JSONObject data) {
		this.data = data;
		if (isAdded()) init();
	}
	
	//clear the adapter and repopulate with request history
	private void init() {
		requestItems.clear();
		adapter.notifyDataSetChanged();
		if (data == null) return;
		try {
			JSONArray root = data
				.optJSONObject("data")
				.optJSONArray(requestType);
			if (root == null) return;
			String type = wrapper.getType();
			for (int i = 0, iL = root.length(); i < iL; i++)
				requestItems.add(getRequestItem(root.getJSONObject(i), type));
			adapter.sort();
			adapter.notifyDataSetChanged();
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * determines if the positive button should be resolve or accept and whether it should be visible	 *
	 *
	 * @param personType: the type of the user
	 * @param resolved:   whether the request was resolved
	 * @param fulfilled:  has the request been fulfilled
	 * @return the mode the button should be in
	 */
	private int getVisibility(String personType, boolean resolved, boolean fulfilled) {
		//if this fragment is sosHistory requests
		if (requestType.equals("sosHistory")) {
			//if the user is client
			if (personType.equals("client"))
				//if the request is resolved hide the button
				if (resolved) return RequestItem.UNDEFINED;
					//else the request can be resolved
				else return RequestItem.RESOLVABLE;
				//if user is doctor, and the request is resolved, hide the button
			else if (fulfilled) return RequestItem.UNDEFINED;
				//else doctor can accept this sos request
			else return RequestItem.ACCEPTABLE;
			//if this fragment is transitHistory
		} else {
			//if the user is doctor
			if (personType.equals("doctor"))
				//if the request is resolved we hide the button
				if (resolved) return RequestItem.UNDEFINED;
					//else the user can resolve it
				else return RequestItem.RESOLVABLE;
				//else if the user is transit service and the request is already fulfilled, hide the button
			else if (fulfilled) return RequestItem.UNDEFINED;
				//else user can accept this transit request
			else return RequestItem.ACCEPTABLE;
		}
	}
	
	//convenience method to show a note dialog for transit request
	private void showNoteDialog(String id, double lat, double lon) {
		ConstraintLayout root = (ConstraintLayout) LayoutInflater.from(getContext())
			.inflate(R.layout.dialog_note, recyclerView, false);
		TextInputEditText editText = root.findViewById(R.id.note);
		dialog = new AlertDialog.Builder(getContext())
			.setTitle(R.string.noteTitle)
			.setIcon(R.drawable.heart)
			.setView(root)
			//dismiss the dialog
			.setNegativeButton(android.R.string.cancel, (dialogInterface, i) -> {
				dialog.dismiss();
				dialog = null;
			})
			//make a transit request
			.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
				try {
					dialog.dismiss();
					dialog = null;
					Editable text = editText.getText();
					transitRequest(id, text != null ? text.toString() : "", lat, lon);
				} catch (JSONException e) {
					e.printStackTrace();
				}
			})
			.create();
		dialog.show();
		//show the keyboard
		Window window = dialog.getWindow();
		if (window != null)
			window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
	}
	
	//convenience method to make a transit request
	private void transitRequest(String id, String note, double lat, double lon) throws JSONException {
		//build the body with required parameters
		JSONObject request = new JSONObject();
		request.putOpt("id", id);
		request.putOpt("did", wrapper.getEmail());
		request.putOpt("lat", lat);
		request.putOpt("lon", lon);
		request.putOpt("note", note.replace("\n", "\\n"));
		//make the network request
		wrapper.getClient()
			.newCall(wrapper.getPreparedRequest("mutation{newTransitRequest(" +
				"email:\"" + wrapper.getEmail() + "\" " +
				"password:\"" + wrapper.getPassword() + "\" " +
				"request:\"" + request.toString().replace("\"", "\\\"") + "\" " +
				"radius:1000){id,did,tids,lat,lon,note,createdAt}}", "transitRequest"))
			.enqueue(this);
	}
	
	//add the client in case a doctor accepts or doctor in case a transit service accepts a request to show details
	private PersonDescription addNewPerson(String id, String personType, JSONObject person) throws JSONException {
		return new PersonDescription(id, personType, person)
			.setTimeout(ApplicationWrapper.PREVIEW_TIMEOUT, wrapper.getPeople());
	}
	
	/**
	 * convenience method to show a dialog with additional detail on request click on positive outcome
	 * @param id: email id of the client (if user is doctor) or doctor (if user is transit service)
	 * @param type: type of the user
	 */
	private void showDetails(String id, String type) {
		PersonDescription person = wrapper.findPerson(id);
		if (person == null) return;
		JSONObject detail = person.getMeta(),
			info = detail.optJSONObject("info");
		ConstraintLayout root = (ConstraintLayout) LayoutInflater.from(getContext())
			.inflate(R.layout.dialog_info, this.recyclerView, false);
		((TextView) root.findViewById(R.id.email)).setText(detail.optString("email"));
		((TextView) root.findViewById(R.id.name)).setText(info.optString("name"));
		((TextView) root.findViewById(R.id.phone)).setText(info.optString("phone"));
		TextView history = root.findViewById(R.id.history);
		if (type.equals("client"))
			history.setText(info.optString("history"));
		else history.setVisibility(View.GONE);
		new AlertDialog.Builder(getContext())
			.setIcon(R.mipmap.ic_launcher)
			.setTitle(R.string.detailTitle)
			.setView(root)
			.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> dialogInterface.dismiss())
			.setOnDismissListener(dialogInterface -> detailID = detailType = null)
			.create().show();
	}
}
