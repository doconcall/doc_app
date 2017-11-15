package com.davinci.doc.custom;

/**
 * Created by aakash on 11/8/17.
 */

public class FragmentDataItem {
	private String id = null;
	private Object data = null;

	public FragmentDataItem(String id,Object data){
		this.id = id;
		this.data = data;
	}

	public String getId() {
		return id;
	}

	public Object getData() {
		return data;
	}

	public void setData(Object data) {
		this.data = data;
	}
}
