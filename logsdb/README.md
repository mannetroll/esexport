
### LogsDB

```
index                                                              pri docs.count dataset.size
.ds-logsdb-varnish-2025.01.11-001599-2025.01.11-000001               1    5000000        2.2gb
ls-varnish-2025.01.11-001599                                         1    5000000        6.8gb
```

### Kibana


```
GET _stats/store
GET _cat/indices?s=i&v&h=index,pri,docs.count,dataset.size

GET _index_template/ls-varnish-v4
GET _index_template/logsdb-varnish-v4

POST _reindex?wait_for_completion=false
{
  "source": {
    "index": "ls-varnish-2025.01.11-001599"
  },
  "dest": {
    "index": "logsdb-varnish-2025.01.11-001599",
    "op_type": "create"
  }
}

GET _tasks/?pretty&detailed=true&actions=*reindex
GET _tasks?actions=*reindex&detailed


PUT _index_template/my-index-template
{
  "data_stream": { },
  "template": {
     "settings": {
        "index.mode": "logsdb" 
     }
  }
}
```

### Logstash

```
output {
  elasticsearch {
    id => "ls-varnish"
    hosts => ["https://21b91997abb54c98b27ea866211f1126.elastic.tele2.net:9243"]
    user => "es-tv-tech-prod-write"
    password => "*********************"
    ssl_verification_mode => "none"

    # This sets Elasticsearch's op_type to 'create'
    action => "create"

    index => "ls-varnish_alias6"
    template => "/opt/logstash-custom/templates/ls-varnish-v4.json"
    template_api => "composable"
    template_name => "ls-varnish-v4"
    template_overwrite => "true"
  }
}
```

