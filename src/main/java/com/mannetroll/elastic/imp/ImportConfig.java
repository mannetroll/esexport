package com.mannetroll.elastic.imp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author mannetroll
 */
@Component
@ConfigurationProperties(prefix = "import")
public class ImportConfig {
    private String eshost;
    private String cluster;
    private String shield;
    private Integer bulkSize;
    private Integer rows;

    public String getEshost() {
        return eshost;
    }

    public void setEshost(String eshost) {
        this.eshost = eshost;
    }

    public String getCluster() {
        return cluster;
    }

    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    public String getShield() {
        return shield;
    }

    public void setShield(String shield) {
        this.shield = shield;
    }

    public Integer getBulkSize() {
        return bulkSize;
    }

    public void setBulkSize(Integer bulkSize) {
        this.bulkSize = bulkSize;
    }

    public Integer getRows() {
        return rows;
    }

    public void setRows(Integer rows) {
        this.rows = rows;
    }
}
