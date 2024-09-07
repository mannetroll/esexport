package com.mannetroll.elastic.exp;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.GZIPOutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import io.searchbox.client.JestClient;
import io.searchbox.client.JestResult;
import io.searchbox.core.Cat;
import io.searchbox.core.CatResult;
import io.searchbox.core.ClearScroll;
import io.searchbox.core.Count;
import io.searchbox.core.CountResult;
import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;
import io.searchbox.core.SearchScroll;
import io.searchbox.params.Parameters;

/**
 * @author mannetroll
 */
public class JestClientExporter {
	private static Logger LOG = LogManager.getLogger(JestClientExporter.class);
	private static final String HITS = "hits";
	private static final String NL = "\r\n";
	private static final String EXT = ".txt.gz";
	private static final String _ID = "_id";
	private final JestClient jestClient;
	private static DateTimeFormatter format = DateTimeFormat.forPattern("-yyyyMMdd");

	public JestClientExporter(JestClient jestClient) {
		this.jestClient = jestClient;
	}

	public static String toDate() {
		return JestClientExporter.format.print(System.currentTimeMillis());
	}

	boolean itemExists(String indexName, String type, String identifier) {
		try {
			SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
			searchSourceBuilder.query(QueryBuilders.matchQuery(_ID, identifier));
			Search search = new Search.Builder(searchSourceBuilder.toString()).addIndex(indexName).addType(type)
					.build();
			JestResult result = jestClient.execute(search);
			LOG.info("result: " + result.getSourceAsStringList());
			if (result != null && result.getSourceAsStringList() != null && result.getSourceAsStringList().size() > 0) {
				return true;
			} else {
				return false;
			}
		} catch (IOException e) {
			LOG.error(e.getMessage(), e);
			return false;
		}
	}

	Long countAllQuery(String indexName) {
		try {
			final Count.Builder searchBuilder = new Count.Builder();
			searchBuilder.addIndex(indexName);
			Count build = searchBuilder.build();
			CountResult execute = jestClient.execute(build);

			// System.out.println("***************************************: CountResult");
			// System.out.println("execute: " + execute);
			// System.out.println(JsonUtil.toPretty(execute));
			// System.out.println("***************************************: CountResult");

			Long total = execute.getCount().longValue();
			LOG.info("total: " + total);
			return total;
		} catch (IOException e) {
			LOG.error(e.getMessage(), e);
			return 0L;
		}
	}

	void matchAllQuery(String indexName) {
		try {
			SearchSourceBuilder builder = new SearchSourceBuilder();
			builder.query(QueryBuilders.matchAllQuery()).size(100).from(0);
			Search search = new Search.Builder(builder.toString()).addIndex(indexName).build();
			SearchResult execute = jestClient.execute(search);
			if (execute != null) {
				SearchResult result = new ISearchResult(execute);
				if (result != null && result.getSourceAsStringList() != null
						&& result.getSourceAsStringList().size() > 0) {
					LOG.info("size: " + result.getSourceAsStringList().size());
					LOG.info("first: " + result.getSourceAsStringList().get(0));
				}
			}
		} catch (IOException e) {
			LOG.error(e.getMessage(), e);
		}
	}

	SearchResult startScrollSearch(String indexName, int size, boolean sort) throws IOException {
		SearchSourceBuilder builder = new SearchSourceBuilder();
		if (sort) {
			builder.query(QueryBuilders.matchAllQuery()).size(size).sort("@timestamp", SortOrder.ASC).fetchSource();
		} else {
			builder.query(QueryBuilders.matchAllQuery()).size(size).fetchSource();
		}
		Search search = new Search.Builder(builder.toString()).addIndex(indexName)
				.setParameter(Parameters.SCROLL, "600m").build();
		SearchResult searchResult = jestClient.execute(search);
		return searchResult;
	}

	JestResult readMoreFromScroll(JestResult inResult) throws IOException {
		String scrollId = inResult.getJsonObject().get("_scroll_id").getAsString();
		SearchScroll scroll = new SearchScroll.Builder(scrollId, "600m").build();
		JestResult searchResult = jestClient.execute(scroll);
		return searchResult;
	}

	JestResult clearScroll(JestResult inResult) throws IOException {
		String scrollId = inResult.getJsonObject().get("_scroll_id").getAsString();
		ClearScroll clearScroll = new ClearScroll.Builder().addScrollId(scrollId).build();
		JestResult searchResult = jestClient.execute(clearScroll);
		return searchResult;
	}

	public void processIndex(String indexName, int batch, int max, boolean sort) {
		try {
			long start = System.currentTimeMillis();
			Long countAllQuery = this.countAllQuery(indexName);
			LOG.info("countAllQuery: " + countAllQuery + ", sort: " + sort);
			SearchResult result = this.startScrollSearch(indexName, batch, sort);
			JsonArray hits = result.getJsonObject().getAsJsonObject(HITS).getAsJsonArray(HITS);
			int size = hits.size();
			int pos = size;
			//
			Writer writer = new OutputStreamWriter(
					new GZIPOutputStream(new FileOutputStream(indexName + toDate() + EXT)), StandardCharsets.UTF_8);
			if (pos > 0) {
				for (JsonElement jsonElement : hits) {
					if (jsonElement != null) {
						writer.write(jsonElement.toString());
						writer.write(NL);
						writer.flush();
					}
				}
			}

			while (pos < countAllQuery && pos < max) {
				JestResult readMoreFromScroll = this.readMoreFromScroll(result);
				if (readMoreFromScroll == null) {
					// try again
					LOG.info("readMoreFromScroll=null, " + indexName);
					readMoreFromScroll = this.readMoreFromScroll(result);
					if (readMoreFromScroll == null) {
						LOG.info("Sleeping 5 secs..." + indexName);
						Thread.sleep(5000);
						continue;
					}
				}
				hits = readMoreFromScroll.getJsonObject().getAsJsonObject(HITS).getAsJsonArray(HITS);
				size = hits.size();
				if (size > 0) {
					for (JsonElement jsonElement : hits) {
						if (jsonElement != null) {
							writer.write(jsonElement.toString());
							writer.write(NL);
							writer.flush();
						}
					}
				}
				pos += size;
				if ((pos % 1000) == 0) {
					long elapsed = System.currentTimeMillis() - start;
					LOG.info((pos) + ": fps: " + (Float.valueOf(1000f * pos / elapsed)).intValue() + "  "
							+ (Float.valueOf(100f * pos / countAllQuery)).intValue() + "%");
				}
			}
			this.clearScroll(result);
			writer.close();
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
	}

	@SuppressWarnings({ "deprecation", "unchecked", "rawtypes" })
	public Set<String> getIndicies() {
		try {
			Set<String> result = new TreeSet<String>();
			final Cat build = new Cat.IndicesBuilder().setParameter("h", "index").build();
			LOG.info(build.toString());
			CatResult cat = jestClient.execute(build);
			Map map = cat.getJsonMap();
			List<Map> object = (List<Map>) map.get("result");
			for (Map tmp : object) {
				final String index = (String) tmp.get("index");
				result.add(index);
			}
			return result;
		} catch (IOException e) {
			LOG.error(e.getMessage(), e);
			return Collections.emptySet();
		}
	}

	public Set<String> getIndicies(String prefix) {
		Set<String> result = new TreeSet<String>();
		Set<String> indicies = getIndicies();
		for (String name : indicies) {
			if (name.startsWith(prefix)) {
				LOG.info(name);
				result.add(name);
			}
		}
		return result;
	}

}
