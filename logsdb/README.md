
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
``