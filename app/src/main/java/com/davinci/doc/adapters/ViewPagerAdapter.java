package com.davinci.doc.adapters;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

import com.davinci.doc.custom.FragmentDataItem;
import com.davinci.doc.fragments.InfoFragment;
import com.davinci.doc.fragments.RequestFragment;

import java.util.ArrayList;

/**
 * Created by aakash on 10/25/17.
 */

public class ViewPagerAdapter extends FragmentPagerAdapter {

	private ArrayList<FragmentDataItem> fragmentData = new ArrayList<>();

	public ViewPagerAdapter(FragmentManager fm, ArrayList<FragmentDataItem> fragmentData) {
		super(fm);
		this.fragmentData = fragmentData;
	}

	@Override
	public Fragment getItem(int position) {
		FragmentDataItem dataItem = fragmentData.get(position);
		switch (dataItem.getId()) {
			case "Info":
				return InfoFragment.newInstance(dataItem.getData());
			case "SOS History":
				return RequestFragment.newInstance("sosHistory", dataItem.getData());
			case "Transit History":
				return RequestFragment.newInstance("transitHistory", dataItem.getData());
		}
		return null;
	}

	@Override
	public int getCount() {
		return fragmentData.size();
	}

	@Override
	public CharSequence getPageTitle(int position) {
		return fragmentData.get(position).getId();
	}

	@Override
	public void notifyDataSetChanged() {
		super.notifyDataSetChanged();
	}
}
