package com.mannetroll.elastic.exp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import io.searchbox.cloning.CloneUtils;
import io.searchbox.core.SearchResult;

/**
 * @author mannetroll
 */
public class ISearchResult extends SearchResult {

	public ISearchResult(SearchResult searchResult) {
		super(searchResult);
	}

	// Compatible with multiple versions （for es5,es6,es7,es8）
	@Override
	public Long getTotal() {
		Long total = null;
		JsonElement element = getPath(PATH_TO_TOTAL);
		if (element != null) {
			if (element instanceof JsonPrimitive) {
				return ((JsonPrimitive) element).getAsLong();
			} else if (element instanceof JsonObject) {
				total = ((JsonObject) element).getAsJsonPrimitive("value").getAsLong();
			}
		}
		return total;
	}

	// Compatible with es8, set _type = _doc
	protected <T, K> Hit<T, K> extractHit(Class<T> sourceType, Class<K> explanationType, JsonElement hitElement,
			String sourceKey, boolean addEsMetadataFields) {
		Hit<T, K> hit = null;

		if (hitElement.isJsonObject()) {
			JsonObject hitObject = hitElement.getAsJsonObject();
			JsonObject source = hitObject.getAsJsonObject(sourceKey);

			String index = hitObject.get("_index").getAsString();
			String type = "_doc"; // hitObject.get("_type").getAsString(); // _type is gone in version 8

			String id = hitObject.get("_id").getAsString();

			Double score = null;
			if (hitObject.has("_score") && !hitObject.get("_score").isJsonNull()) {
				score = hitObject.get("_score").getAsDouble();
			}

			String parent = null;
			String routing = null;

			if (hitObject.has("_parent") && !hitObject.get("_parent").isJsonNull()) {
				parent = hitObject.get("_parent").getAsString();
			}

			if (hitObject.has("_routing") && !hitObject.get("_routing").isJsonNull()) {
				routing = hitObject.get("_routing").getAsString();
			}

			JsonElement explanation = hitObject.get(EXPLANATION_KEY);
			Map<String, List<String>> highlight = extractHighlight(hitObject.getAsJsonObject(HIGHLIGHT_KEY));
			List<String> sort = extractSort(hitObject.getAsJsonArray(SORT_KEY));

			List<String> matchedQueries = new ArrayList<>();
			if (hitObject.has("matched_queries") && !hitObject.get("matched_queries").isJsonNull()) {
				JsonArray rawMatchedQueries = hitObject.get("matched_queries").getAsJsonArray();
				rawMatchedQueries.forEach(matchedQuery -> {
					matchedQueries.add(matchedQuery.getAsString());
				});
			}

			if (addEsMetadataFields) {
				JsonObject clonedSource = null;
				for (MetaField metaField : META_FIELDS) {
					JsonElement metaElement = hitObject.get(metaField.esFieldName);
					if (metaElement != null) {
						if (clonedSource == null) {
							if (source == null) {
								clonedSource = new JsonObject();
							} else {
								clonedSource = (JsonObject) CloneUtils.deepClone(source);
							}
						}
						clonedSource.add(metaField.internalFieldName, metaElement);
					}
				}
				if (clonedSource != null) {
					source = clonedSource;
				}
			}

			hit = new Hit<T, K>(sourceType, source, explanationType, explanation, highlight, sort, index, type, id,
					score, parent, routing, matchedQueries);

		}

		return hit;
	}
}