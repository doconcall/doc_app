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
 */

public class RequestFragment extends Fragment
	implements OnItemSelectedListener, Callback {
	
	public static RequestFragment newInstance(String type, Object data) {
		Bundle args = new Bundle();
		RequestFragment fragment = new RequestFragment();
		fragment.requestType = type;
		fragment.data = (JSONObject) data;
		fragment.setArguments(args);
		return fragment;
	}
	
	String requestType = null;
	
	ApplicationWrapper wrapper = null;
	RecyclerView recyclerView = null;
	RequestAdapter adapter = null;
	AlertDialog dialog = null;
	
	ArrayList<RequestItem> requestItems = new ArrayList<>();
	JSONObject data = null;
	
	RequestItem acceptedRequest = null;
	String detailID = null, detailType = null;
	
	
	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		wrapper = (ApplicationWrapper) getActivity().getApplication();
	}
	
	@Override @Nullable
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		setRetainInstance(true);
		recyclerView = (RecyclerView) inflater.inflate(R.layout.fragment_request, container, false);
		recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
		adapter = new RequestAdapter(requestItems, this);
		recyclerView.setAdapter(adapter);
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
		if (detailID != null && detailType != null)
			showDetails(detailID, detailType);
	}
	
	@Override
	public void OnItemSelected(Object item, View view) {
		RequestItem requestItem = (RequestItem) item;
		switch (view.getId()) {
			case R.id.accept:
				if (requestItem.getStatus() == RequestItem.RESOLVABLE) {
					String personType = wrapper.getType(),
						method = personType.equals("client") ? "resolveSOS" : "resolveTransitRequest";
					wrapper.getClient()
						.newCall(wrapper.getPreparedRequest("mutation{" + method + "(" +
							"id:\"" + requestItem.getId() + "\" " +
							(personType.equals("client") ? "cid" : "did") + ":\"" + wrapper.getEmail() + "\" " +
							"password:\"" + wrapper.getPassword() + "\"" +
							")}", method))
						.enqueue(this);
					String latestSOS = wrapper.getLatestSOS();
					if (latestSOS != null && latestSOS.contains(requestItem.getId())) {
						wrapper.setLatestSOS(null);
						wrapper.clearExtender();
					}
				} else {
					try {
						wrapper.clearQueuedRequests();
						wrapper.acceptSOSorTransit(requestItem.getId(),
							wrapper.getType().equals("doctor") ? "acceptSOS" : "acceptTransitRequest", this);
					} catch (IOException e) {
						e.printStackTrace();
					}
					acceptedRequest = requestItem;
				}
				break;
			case R.id.decline:
				String personType = wrapper.getType(),
					method = personType.equals("doctor") ? "declineSOS" : "declineTransitRequest";
				wrapper.getClient()
					.newCall(wrapper.getPreparedRequest("mutation{" + method + "(" +
						"id:\"" + requestItem.getId() + "\" " +
						(personType.equals("doctor") ? "did" : "tid") + ":\"" + wrapper.getEmail() + "\" " +
						"password:\"" + wrapper.getPassword() + "\"" +
						")}", method))
					.enqueue(this);
				break;
			default:
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
				case "resolveSOS":
				case "resolveTransitRequest":
				case "declineSOS":
				case "declineTransitRequest":
					break;
				case "acceptSOS":
					try {
						wrapper.getPeople()
							.add(addNewPerson(acceptedRequest.getId(), "client",
								new JSONObject(finalRes).optJSONObject("data").optJSONObject("acceptSOS")));
					} catch (JSONException e) {
						e.printStackTrace();
					}
					wrapper.setLocation(new LatLng(acceptedRequest.getLat(), acceptedRequest.getLon()));
					dialog = new AlertDialog.Builder(getContext())
						.setTitle(R.string.transitRequestTitle)
						.setIcon(R.drawable.heart)
						.setMessage(R.string.transitRequestContent)
						.setNegativeButton(android.R.string.no, (dialogInterface, i) -> {
							if (dialog != null) {
								dialog.dismiss();
								dialog = null;
							}
							acceptedRequest = null;
						})
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
						wrapper.getPeople()
							.add(addNewPerson(acceptedRequest.getId(), "doctor",
								new JSONObject(finalRes).optJSONObject("data").optJSONObject("acceptTransitRequest")));
					} catch (JSONException e) {
						e.printStackTrace();
					}
					wrapper.setLocation(new LatLng(acceptedRequest.getLat(), acceptedRequest.getLon()));
					if (dialog == null)
						acceptedRequest = null;
					break;
				case "transitRequest":
					Toast.makeText(getContext(), "Transit request broadcasted", Toast.LENGTH_LONG).show();
					break;
			}
			activity.refresh();
		});
	}
	
	@NonNull
	private RequestItem getRequestItem(JSONObject object, String personType) {
		return new RequestItem(object.optString("id"),
			object.optString(this.requestType.equals("sosHistory") ? "cid" : "did"),
			object.optString("note"),
			object.optDouble("lat"), object.optDouble("lon"),
			getVisibility(personType, object.optBoolean("resolved"), !Objects.equals(object.optString("fulfilled"), "null")),
			Long.parseLong(object.optString("createdAt")));
	}
	
	public void setData(JSONObject data) {
		this.data = data;
		if (isResumed())
			init();
	}
	
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
	
	private int getVisibility(String personType, boolean resolved, boolean fulfilled) {
		if (requestType.equals("sosHistory")) {
			if (personType.equals("client"))
				if (resolved) return RequestItem.UNDEFINED;
				else return RequestItem.RESOLVABLE;
			else if (fulfilled) return RequestItem.UNDEFINED;
			else return RequestItem.ACCEPTABLE;
		} else {
			if (personType.equals("doctor"))
				if (resolved) return RequestItem.UNDEFINED;
				else return RequestItem.RESOLVABLE;
			else if (fulfilled) return RequestItem.UNDEFINED;
			else return RequestItem.ACCEPTABLE;
		}
	}
	
	private void showNoteDialog(String id, double lat, double lon) {
		ConstraintLayout root = (ConstraintLayout) LayoutInflater.from(getContext())
			.inflate(R.layout.dialog_note, recyclerView, false);
		TextInputEditText editText = root.findViewById(R.id.note);
		dialog = new AlertDialog.Builder(getContext())
			.setTitle(R.string.noteTitle)
			.setIcon(R.drawable.heart)
			.setView(root)
			.setNegativeButton(android.R.string.cancel, (dialogInterface, i) -> {
				dialog.dismiss();
				dialog = null;
			})
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
		Window window = dialog.getWindow();
		if (window != null)
			window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
	}
	
	private void transitRequest(String id, String note, double lat, double lon) throws JSONException {
		JSONObject request = new JSONObject();
		request.putOpt("id", id);
		request.putOpt("did", wrapper.getEmail());
		request.putOpt("lat", lat);
		request.putOpt("lon", lon);
		request.putOpt("note", note.replace("\n", "\\n"));
		wrapper.getClient()
			.newCall(wrapper.getPreparedRequest("mutation{newTransitRequest(" +
				"email:\"" + wrapper.getEmail() + "\" " +
				"password:\"" + wrapper.getPassword() + "\" " +
				"request:\"" + request.toString().replace("\"", "\\\"") + "\" " +
				"radius:1000){id,did,tids,lat,lon,note,createdAt}}", "transitRequest"))
			.enqueue(this);
	}
	
	private PersonDescription addNewPerson(String id, String personType, JSONObject person) throws JSONException {
		return new PersonDescription(id, personType, person)
			.setTimeout(ApplicationWrapper.PREVIEW_TIMEOUT, wrapper.getPeople());
	}
	
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
