package com.davinci.doc.adapters;

import android.annotation.SuppressLint;
import android.os.Build;
import android.support.constraint.ConstraintLayout;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.davinci.doc.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

import static android.view.View.GONE;
import static android.view.View.OnClickListener;
import static android.view.View.VISIBLE;
import static com.davinci.doc.ApplicationWrapper.OnItemSelectedListener;

/**
 * Created by aakash on 10/27/17.
 * Adapter for sos history and transit history
 */
public class RequestAdapter extends RecyclerView.Adapter<RequestAdapter.RequestViewHolder> {

	private ArrayList<RequestItem> requestItems = null;
	private OnItemSelectedListener itemSelectedListener = null;

	public RequestAdapter(ArrayList<RequestItem> requestItems, OnItemSelectedListener itemSelectedListener) {
		this.requestItems = requestItems;
		this.itemSelectedListener = itemSelectedListener;
	}

	@Override
	public RequestViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		return new RequestViewHolder(LayoutInflater
			.from(parent.getContext()).inflate(R.layout.item_request, parent, false),
			this.itemSelectedListener);
	}

	@Override
	public void onBindViewHolder(RequestViewHolder holder, int position) {
		holder.bind(requestItems.get(position));
	}

	@Override
	public int getItemCount() {
		return requestItems.size();
	}
	
	//sort the source with descending date
	public void sort() {
		Collections.sort(this.requestItems, Collections.reverseOrder((r1, r2) -> r1.created.compareTo(r2.created)));
	}

	public static class RequestItem {

		public static final int ACCEPTABLE = 1;
		public static final int RESOLVABLE = 2;
		public static final int UNDEFINED = 0;

		String id = null,
			rid = null,
			note = null;
		Date created = null;
		double lat, lon;
		int status = 0;

		public RequestItem(String id, String rid, String note, double lat, double lon, int status, long created) {
			this.id = id;
			this.rid = rid;
			this.note = note;
			this.created = new Date(created);
			this.lat = lat;
			this.lon = lon;
			this.status = status;
		}

		public String getId() {
			return id;
		}

		public double getLat() {
			return lat;
		}

		public double getLon() {
			return lon;
		}

		public int getStatus() {
			return status;
		}
	}

	public static class RequestViewHolder extends RecyclerView.ViewHolder
		implements OnClickListener {
		RequestItem requestItem = null;
		ConstraintLayout root = null;
		OnItemSelectedListener itemSelectedListener = null;

		RequestViewHolder(View itemView, OnItemSelectedListener itemSelectedListener) {
			super(itemView);
			//initialize this view and set this instance as a click listener
			root = (ConstraintLayout) ((CardView) itemView).getChildAt(0);
			root.findViewById(R.id.accept).setOnClickListener(this);
			root.findViewById(R.id.decline).setOnClickListener(this);
			root.findViewById(R.id.requestID).setOnClickListener(this);
			root.findViewById(R.id.requester).setOnClickListener(this);
			root.findViewById(R.id.note).setOnClickListener(this);
			root.findViewById(R.id.createdAt).setOnClickListener(this);
			root.findViewById(R.id.location).setOnClickListener(this);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
				((TextView) root.findViewById(R.id.note)).setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD);
			this.itemSelectedListener = itemSelectedListener;
		}

		@SuppressLint("DefaultLocale")
		void bind(RequestItem requestItem) {
			//bind this view with given information
			if (requestItem == null) return;
			this.requestItem = requestItem;
			((TextView) root.findViewById(R.id.requestID)).setText(this.requestItem.id);
			((TextView) root.findViewById(R.id.requester)).setText(this.requestItem.rid);
			((TextView) root.findViewById(R.id.note)).setText(this.requestItem.note);
			((TextView) root.findViewById(R.id.createdAt)).setText(this.requestItem.created.toString());
			((TextView) root.findViewById(R.id.location))
				.setText(String.format("%.3f/%.3f", this.requestItem.lat, this.requestItem.lon));
			Button accept = root.findViewById(R.id.accept), reject = root.findViewById(R.id.decline);
			switch (requestItem.status) {
				case RequestItem.ACCEPTABLE:
					accept.setVisibility(VISIBLE);
					accept.setText(R.string.accept);
					reject.setVisibility(VISIBLE);
					break;
				case RequestItem.RESOLVABLE:
					accept.setVisibility(VISIBLE);
					accept.setText(R.string.resolve);
					reject.setVisibility(GONE);
					break;
				case RequestItem.UNDEFINED:
					accept.setVisibility(GONE);
					reject.setVisibility(GONE);
					break;
			}
		}

		@Override
		public void onClick(View view) {
			if (this.itemSelectedListener != null)
				this.itemSelectedListener.OnItemSelected(requestItem, view);
		}
	}
}
