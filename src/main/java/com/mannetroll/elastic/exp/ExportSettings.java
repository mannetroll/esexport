package com.mannetroll.elastic.exp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author mannetroll
 */
@Component
@ConfigurationProperties(prefix = "export")
public class ExportSettings {
    private String eshost;
    private String cluster;
    private String shield;
    private Integer batch;
    private Integer max;
    private Boolean sort;

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

    public Integer getBatch() {
        return batch;
    }

    public void setBatch(Integer batch) {
        this.batch = batch;
    }

    public Integer getMax() {
        return max;
    }

    public void setMax(Integer max) {
        this.max = max;
    }

    public void setSort(Boolean sort) {
        this.sort = sort;
    }

    public Boolean getSort() {
        return sort;
    }

}
