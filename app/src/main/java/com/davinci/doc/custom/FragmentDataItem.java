package com.davinci.doc.custom;

/**
 * Created by aakash on 11/8/17.
 * Holds the page information
 */
public class FragmentDataItem {
	//title of the page
	private String id = null;
	//JSON of the information and respective histories
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
