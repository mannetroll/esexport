package com.mannetroll.elastic.imp.enrich;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.mannetroll.elastic.imp.elastic.IndexDocument;

/**
 * @author mannetroll
 */
public class JsonUtil {
	private final static Logger LOGGER = LogManager.getLogger(JsonUtil.class);
	private static final ObjectMapper mapper;
	private static final ObjectMapper pretty;

	static {
		mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		mapper.setSerializationInclusion(Include.NON_NULL);
		mapper.registerModule(new JodaModule());
		mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
		//
		pretty = new ObjectMapper();
		pretty.setSerializationInclusion(Include.NON_NULL);
		pretty.enable(SerializationFeature.INDENT_OUTPUT);
	}

	public static String toJson(Object object) {
		try {
			StringWriter sw = new StringWriter();
			mapper.writeValue(sw, object);
			return sw.toString();
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
			return null;
		}
	}

	public static IndexDocument parseIndexDocument(String json) {
		try {
			return mapper.readValue(json, IndexDocument.class);
		} catch (IOException e) {
			LOGGER.error(e.getMessage(), e);
			return null;
		}
	}

	public static String toPretty(Object object) {
		try {
			StringWriter sw = new StringWriter();
			pretty.writeValue(sw, object);
			return sw.toString();
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
			return null;
		}
	}

	public static String indent(String queryString) {
		Map<String, Object> tmp = JsonUtil.parse(queryString);
		return JsonUtil.toPretty(tmp);
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> parse(String json) {
		Map<String, Object> dto = new TreeMap<String, Object>();
		try {
			dto = mapper.readValue(json, Map.class);
		} catch (IOException e) {
			LOGGER.info(e.getMessage(), e);
		}
		return dto;
	}
}
