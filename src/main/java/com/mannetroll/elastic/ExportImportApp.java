package com.mannetroll.elastic;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import com.github.vanroy.springboot.autoconfigure.data.jest.ElasticsearchJestAutoConfiguration;
import com.mannetroll.elastic.exp.ExportSettings;
import com.mannetroll.elastic.exp.JestClientExporter;
import com.mannetroll.elastic.imp.AbstractThreadApp;
import com.mannetroll.elastic.imp.ImportConfig;
import com.mannetroll.elastic.imp.elastic.IndexDocument;
import com.mannetroll.elastic.imp.elastic.JestServiceImpl;
import com.mannetroll.elastic.imp.enrich.JsonUtil;

import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.client.config.HttpClientConfig.Builder;
import io.searchbox.client.http.JestHttpClient;

/**
 * @author mannetroll
 */
@SpringBootApplication(exclude = ElasticsearchJestAutoConfiguration.class)
public class ExportImportApp extends AbstractThreadApp implements CommandLineRunner {
	private static final Logger LOG = LogManager.getLogger(ExportImportApp.class);
	private static ConfigurableApplicationContext context;
	private static volatile int count = 0;
	private static long start = System.currentTimeMillis();
	private static DateTimeFormatter logstash = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

	@Autowired
	private ImportConfig importConfig;

	@Autowired
	private ExportSettings exportSettings;

	static {
		logstash = logstash.withZone(DateTimeZone.forID("GMT"));
	}

	JestClient jestClientImport() {
		String eshost = importConfig.getEshost();
		String cluster = importConfig.getCluster();
		LOG.info("eshost: " + eshost);
		LOG.info("cluster: " + cluster);
		List<Header> headers = new ArrayList<Header>();
		headers.add(new BasicHeader("X-Found-Cluster", cluster));
		String basic = new String(Base64.encodeBase64(importConfig.getShield().getBytes()));
		headers.add(new BasicHeader("Authorization", "Basic " + basic));
		JestClientFactory factory = new JestClientFactory() {
			@Override
			protected HttpClientBuilder configureHttpClient(HttpClientBuilder builder) {
				return builder.setDefaultHeaders(headers);
			}
		};
		Builder builder = new HttpClientConfig.Builder(eshost).multiThreaded(true).discoveryEnabled(false)
				.connTimeout(1000).readTimeout(30000);
		factory.setHttpClientConfig(builder.build());
		return (JestHttpClient) factory.getObject();
	}

	JestClient jestClientExport() {
		String eshost = exportSettings.getEshost();
		String cluster = exportSettings.getCluster();
		LOG.info("eshost: " + eshost);
		LOG.info("cluster: " + cluster);
		List<Header> headers = new ArrayList<Header>();
		headers.add(new BasicHeader("X-Found-Cluster", cluster));

		String apikey = exportSettings.getApikey();
		if (apikey != null) {
			headers.add(new BasicHeader("Authorization", "ApiKey " + apikey));
		} else {
			String basic = new String(Base64.encodeBase64(exportSettings.getShield().getBytes()));
			headers.add(new BasicHeader("Authorization", "Basic " + basic));
		}

		JestClientFactory factory = new JestClientFactory() {
			@Override
			protected HttpClientBuilder configureHttpClient(HttpClientBuilder builder) {
				return builder.setDefaultHeaders(headers);
			}
		};
		Builder builder = new HttpClientConfig.Builder(eshost).multiThreaded(true).discoveryEnabled(false)
				.connTimeout(1000).readTimeout(30000);
		factory.setHttpClientConfig(builder.build());
		return (JestHttpClient) factory.getObject();
	}

	public void go(int THREADS, int rows, String dirName, String pattern) {
		try {
			// Start workers.
			startWorkers(THREADS);
			LOG.info("Scanning: " + dirName);
			File fileName = new File(dirName);
			File[] fileList = fileName.listFiles();
			if (fileList == null) {
				LOG.info("No files...");
				System.exit(0);
			}
			Arrays.sort(fileList);
			//
			try {
				for (File file : fileList) {
					if (file.getName().contains(pattern)) {
						Job job = new KpiJob(file.toString(), rows);
						addJob(job);
					}
				}
			} catch (Exception e) {
				LOG.error(e.getMessage(), e);
			}
			// Wait until all jobs are done.
			finish();
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
	}

	public static String toDate() {
		return ExportImportApp.logstash.print(System.currentTimeMillis());
	}

	class KpiJob extends Job {
		private String filename;
		private int rows;
		private JestServiceImpl searchService;
		private int localwrites = 1;

		public KpiJob(String filename, int rows) {
			this.filename = filename;
			this.rows = rows;
			this.searchService = new JestServiceImpl(jestClientImport());
			LOG.info("searchService: " + searchService);
			LOG.info("Job: " + filename);
		}

		//
		// ===========================================================================================
		//
		public void execute() throws IOException {
			long localstart = System.currentTimeMillis();
			LOG.info("start: " + filename);
			int bulkSize = importConfig.getBulkSize();
			int row = 0;
			//
			// Bulk ImportConfig
			//
			LOG.info("Importing " + importConfig.getRows() + " rows from " + filename);
			BufferedReader in = new BufferedReader(
					new InputStreamReader(new GZIPInputStream(new FileInputStream(filename)), StandardCharsets.UTF_8));
			String inputLine;
			List<IndexDocument> bulk = new ArrayList<IndexDocument>(bulkSize);
			//
			while ((inputLine = in.readLine()) != null && row < rows) {
				row++;
				//
				IndexDocument doc = JsonUtil.parseIndexDocument(inputLine);
				///
				bulk.add(doc);
				//
				if ((row % bulkSize) == 0) {
					searchService.toElasticSearchBulk(bulk, 5);
					bulk.clear();
				}
				if ((localwrites++ % 6000) == 0) {
					long elapsed = System.currentTimeMillis() - localstart;
					LOG.info("localwrites: " + localwrites + ", fps: "
							+ Float.valueOf(Float.valueOf(1000f * localwrites / elapsed)).intValue() + ", " + filename);
				}
				if ((count++ % 2000) == 0) {
					// break;
					long elapsed = System.currentTimeMillis() - start;
					LOG.info(
							"FPS @ " + count + ": " + Float.valueOf(Float.valueOf(1000f * count / elapsed)).intValue());
				}
			}
			// rest request
			if (bulk.size() > 0) {
				LOG.info("rest: " + bulk.size());
				searchService.toElasticSearchBulk(bulk, 5);
			}
			in.close();
			LOG.info("processed: " + row + " in " + filename);
		}
	}

	private void process(int THREADS, int rows, String dirName, String pattern) {
		this.go(THREADS, rows, dirName, pattern);
		LOG.info("count: " + count);
		LOG.info("Done!");
	}

	public JestClientExporter exporter() {
		return new JestClientExporter(jestClientExport());
	}

	@Override
	public void run(String... args) {
		LOG.info("################################################## run...");
		if (args != null && args.length > 0) {
			String expimp = args[0];
			if ("import".equals(expimp)) {
				if (args.length == 4) {
					int THREADS = Integer.parseInt(args[1]);
					String path = args[2];
					String pattern = args[3];
					LOG.info("THREADS: " + THREADS);
					LOG.info("path: " + path);
					LOG.info("pattern: " + pattern);
					LOG.info("rows: " + importConfig.getRows());
					LOG.info("bulkSize: " + importConfig.getBulkSize());
					process(THREADS, importConfig.getRows(), path, pattern);
				} else {
					LOG.info("********** Usage: import <THREADS> <PATH> <PATTERN>");
				}
			} else if ("export".equals(expimp)) {
				if (args.length == 2 && exportSettings.getMax() != null) {
					String prefix = args[1];
					LOG.info("prefix: " + prefix);
					LOG.info("max: " + exportSettings.getMax());
					LOG.info("batch: " + exportSettings.getBatch());
					Set<String> indicies = exporter().getIndicies(prefix);
					LOG.info("indicies: " + indicies);
					int size = indicies.size();
					LOG.info("size: " + size);
					int ind = 0;
					for (String name : indicies) {
						long start = System.currentTimeMillis();
						LOG.info("name: " + name + "  " + (++ind) + " of " + size);
						exporter().processIndex(name, exportSettings.getBatch(), exportSettings.getMax(),
								exportSettings.getSort());
						LOG.info("Done: " + name + " in " + (System.currentTimeMillis() - start) / 1000 + " secs");
					}
				} else {
					LOG.info("********** Usage: export <INDEX>");
				}
			} else {
				LOG.info("********** Usage: import/export ...");
			}
		}
		System.exit(0);
	}

	/////////////////////////////////////////////////////////////////////////////////

	public static void main(String[] args) {
		context = SpringApplication.run(ExportImportApp.class, args);
		LOG.info("context: " + context.getBeanDefinitionCount());
	}
}
