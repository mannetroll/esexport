package com.mannetroll.elastic.imp.elastic;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author mannetroll
 */
public class IndexDocument {

	@JsonProperty("_id")
	private String id;

	@JsonProperty("_index")
	private String index;

	@JsonProperty("_source")
	private Map<String, Object> source;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getIndex() {
		return index;
	}

	public void setIndex(String index) {
		this.index = index;
	}

	public Map<String, Object> getSource() {
		return source;
	}

	public void setSource(Map<String, Object> source) {
		this.source = source;
	}
}
