package com.davinci.doc.adapters;

import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;

import com.davinci.doc.ApplicationWrapper.OnItemSelectedListener;
import com.davinci.doc.R;

import java.util.ArrayList;

/**
 * Created by aakash on 10/24/17.
 * Adapter for user's information
 */
public class InfoAdapter extends RecyclerView.Adapter<InfoAdapter.InfoViewHolder> {

	private ArrayList<InfoItem> infoItems = null;
	private OnItemSelectedListener itemSelectedListener = null;

	public InfoAdapter(ArrayList<InfoItem> infoItems, OnItemSelectedListener itemSelectedListener) {
		this.infoItems = infoItems;
		this.itemSelectedListener = itemSelectedListener;
	}

	@Override
	public InfoViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		return new InfoViewHolder(LayoutInflater
			.from(parent.getContext()).inflate(R.layout.item_info, parent, false),
			itemSelectedListener);
	}

	@Override
	public void onBindViewHolder(InfoViewHolder holder, int position) {
		holder.bind(infoItems.get(position));
	}

	@Override
	public int getItemCount() {
		return infoItems.size();
	}

	public static class InfoItem {
		String title = "",
			subtitle = "";
		boolean editable = false;
		int ID;

		public InfoItem(String title, String subtitle, int ID, boolean editable) {
			this.title = title;
			this.subtitle = subtitle;
			this.ID = ID;
			this.editable = editable;
		}

		public String getTitle() {
			return title;
		}

		public String getSubtitle() {
			return subtitle;
		}

		public int getID() {
			return ID;
		}

		public void setSubtitle(String subtitle) {
			this.subtitle = subtitle;
		}
	}

	public static class InfoViewHolder extends ViewHolder
		implements OnClickListener {
		TextView title = null, subtitle = null;
		InfoItem item = null;

		OnItemSelectedListener itemSelectedListener;

		InfoViewHolder(View itemView, OnItemSelectedListener itemSelectedListener) {
			super(itemView);
			//initialize and set this instance as click listeners
			(title = itemView.findViewById(R.id.title)).setOnClickListener(this);
			(subtitle = itemView.findViewById(R.id.subtitle)).setOnClickListener(this);
			this.itemSelectedListener = itemSelectedListener;
		}

		@Override
		public void onClick(View view) {
			//invoke itemSelectedListener if not null
			if (item.editable && itemSelectedListener != null)
				itemSelectedListener.OnItemSelected(this.item, view);
		}

		void bind(InfoItem item) {
			//bind the information with this view
			this.item = item;
			title.setText(this.item.getTitle());
			subtitle.setText(this.item.getSubtitle());
			subtitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, item.editable ? R.drawable.edit : 0, 0);
		}
	}
}
