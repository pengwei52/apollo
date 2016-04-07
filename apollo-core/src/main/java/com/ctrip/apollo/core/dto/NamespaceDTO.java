package com.ctrip.apollo.core.dto;

public class NamespaceDTO {

  private long id;
  
  private String appId;
  
  private String clusterName;

  private String namespaceName;

  public String getAppId() {
    return appId;
  }

  public String getClusterId() {
    return clusterName;
  }

  public long getId() {
    return id;
  }

  public String getNamespaceName() {
    return namespaceName;
  }

  public void setAppId(String appId) {
    this.appId = appId;
  }

  public void setClusterName(String clusterName) {
    this.clusterName = clusterName;
  }

  public void setId(long id) {
    this.id = id;
  }

  public void setNamespaceName(String namespaceName) {
    this.namespaceName = namespaceName;
  }
}