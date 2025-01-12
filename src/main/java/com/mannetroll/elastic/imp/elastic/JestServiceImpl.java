package com.mannetroll.elastic.imp.elastic;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.gson.JsonObject;

import io.searchbox.client.JestClient;
import io.searchbox.client.JestResult;
import io.searchbox.core.Bulk;
import io.searchbox.core.Bulk.Builder;
import io.searchbox.core.BulkResult;
import io.searchbox.core.BulkResult.BulkResultItem;
import io.searchbox.core.Index;
import io.searchbox.indices.CreateIndex;
import io.searchbox.indices.IndicesExists;
import io.searchbox.indices.settings.GetSettings;

/**
 * @author mannetroll
 */
public class JestServiceImpl {
	private static Logger LOG = LogManager.getLogger(JestServiceImpl.class);
	private JestClient jestClient;
	private static ObjectMapper mapper;

	static {
		mapper = new ObjectMapper();
		mapper.setSerializationInclusion(Include.NON_NULL);
		mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
	}

	public JestServiceImpl(JestClient jestClient) {
		this.jestClient = jestClient;
	}

	public String toJson(Object object) {
		try {
			StringWriter sw = new StringWriter();
			mapper.writeValue(sw, object);
			return sw.toString();
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
			return "{\"message\": \"toJson failed\"}";
		}
	}

	public void checkOrCreateIndex(String indexName) {
		try {
			// 1. Check if the index exists
			IndicesExists indicesExists = new IndicesExists.Builder(indexName).build();
			JestResult existsResult = jestClient.execute(indicesExists);
			if (!existsResult.isSucceeded()) {
				// 2. If the index does not exist, create it
				CreateIndex createIndex = new CreateIndex.Builder(indexName).build();
				JestResult createResult = jestClient.execute(createIndex);
				if (createResult.isSucceeded()) {
					LOG.info("Index \"" + indexName + "\" created successfully.");
				} else {
					LOG.info("Failed to create index \"" + indexName + "\": " + createResult.getErrorMessage());
				}
			} else {
				LOG.info("Index \"" + indexName + "\" already exists.");
			}
		} catch (IOException e) {
			LOG.error("IOException occurred while checking/creating index: " + e.getMessage());
		}
	}

	public void toElasticSearchBulk(List<IndexDocument> bulk, int retry) {
		String index = "";
		try {
			Builder bulkRequest = new Bulk.Builder();
			for (IndexDocument doc : bulk) {
				index = doc.getIndex();
				bulkRequest.addAction(new Index.Builder(toJson(doc.getSource())).index(index).id(doc.getId())
						.setParameter("op_type", "create") // only write ops with an op_type of create are allowed in
															// data streams"
						.build());
			}
			BulkResult execute = jestClient.execute(bulkRequest.build());
			String errorMessage = execute.getErrorMessage();
			if (errorMessage != null) {
				LOG.info("errorMessage: " + errorMessage);
				LOG.info("execute: " + execute);
				List<BulkResultItem> items = execute.getItems();
				LOG.info("Items: " + execute.getItems().size());
				for (BulkResultItem bri : items) {
					LOG.info("bulkResultItem: " + bri.errorReason + ": " + bri.index + ": " + bri.id);
				}
				List<BulkResultItem> failedItems = execute.getFailedItems();
				LOG.info("FailedItems: " + execute.getFailedItems().size());
				for (BulkResultItem bri : failedItems) {
					LOG.info("bulkResultItem: " + bri.errorReason + ": " + bri.index + ": " + bri.id);
				}
			}
		} catch (IOException e) {
			if (retry > 0) {
				LOG.info("retry: " + retry + ", index: " + index);
				try {
					Thread.sleep(10000);
				} catch (Exception e1) {
				}
				this.toElasticSearchBulk(bulk, retry - 1);
			} else {
				if (bulk.size() > 0) {
					LOG.info("bulk: " + bulk.get(0).getSource());
				}
				LOG.error(e.getMessage(), e);
			}
		}
	}

	public int getNumberOfShards(String index) {
		try {
			JestResult result = jestClient.execute(new GetSettings.Builder().addIndex(index).build());
			if (result.isSucceeded()) {
				// System.out.println(result.getJsonString());
				JsonObject json = result.getJsonObject().getAsJsonObject(index).getAsJsonObject("settings");
				XContentBuilder xcb = XContentFactory.contentBuilder(XContentType.JSON);
				Settings settings = Settings.builder().loadFromSource(json.toString(), xcb.contentType()).build();
				// System.out.println(settings);
				return Integer.parseInt(settings.get("index.number_of_shards"));
			}
		} catch (IOException e) {
			LOG.error(e.getMessage(), e);
		}
		return 0;
	}

}
