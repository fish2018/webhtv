package com.fongmi.android.tv.subtitle.source;

import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class SubtitleSourceConfig {
    private String key="", name="", api="", ext="", sha256="";
    private int type, enabled=1, priority, timeout=15, protocolVersion=1;
    private boolean queryIndependent;
    private List<String> languages=Collections.emptyList(), formats=Collections.emptyList();
    private Map<String,String> headers=Collections.emptyMap();
    private JsonObject params=new JsonObject();
    public String getKey(){return key;} public String getName(){return name;} public String getApi(){return api;} public String getExt(){return ext;} public String getSha256(){return sha256;}
    public int getType(){return type;} public boolean isEnabled(){return enabled!=0;} public int getPriority(){return priority;} public int getTimeout(){return Math.max(1,Math.min(timeout,60));} public int getProtocolVersion(){return protocolVersion;} public boolean isQueryIndependent(){return queryIndependent;}
    public List<String> getLanguages(){return languages==null?Collections.emptyList():languages;} public List<String> getFormats(){return formats==null?Collections.emptyList():formats;} public Map<String,String> getHeaders(){return headers==null?Collections.emptyMap():headers;} public JsonObject getParams(){return params==null?new JsonObject():params;} public void setParams(JsonObject params){this.params=params==null?new JsonObject():params;}
    public boolean valid(){return !key.trim().isEmpty()&&!name.trim().isEmpty()&&(type==3||type==4)&&!api.trim().isEmpty()&&(type!=3||!ext.trim().isEmpty())&&protocolVersion==1;}
}
