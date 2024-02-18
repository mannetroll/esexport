package com.mannetroll.elastic.imp.elastic;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import io.searchbox.indices.settings.GetSettings;

/**
 * @author mannetroll
 */
public class JestServiceImpl {
    private static Logger LOG = LoggerFactory.getLogger(JestServiceImpl.class);
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

    public void toElasticSearchBulk(List<IndexDocument> bulk, int retry) {
        String index = "";
        try {
            Builder bulkRequest = new Bulk.Builder();
            for (IndexDocument doc : bulk) {
                index = doc.getIndex();
                bulkRequest.addAction(new Index.Builder(toJson(doc.getSource())).index(index).id(doc.getId()).build());
            }
            BulkResult execute = jestClient.execute(bulkRequest.build());
            String errorMessage = execute.getErrorMessage();
            if (errorMessage != null) {
                LOG.info("errorMessage: " + errorMessage);
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
                //System.out.println(result.getJsonString());
                JsonObject json = result.getJsonObject().getAsJsonObject(index).getAsJsonObject("settings");
                XContentBuilder xcb = XContentFactory.contentBuilder(XContentType.JSON);
                Settings settings = Settings.builder().loadFromSource(json.toString(), xcb.contentType()).build();
                //System.out.println(settings);
                return Integer.parseInt(settings.get("index.number_of_shards"));
            }
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
        }
        return 0;
    }

}
