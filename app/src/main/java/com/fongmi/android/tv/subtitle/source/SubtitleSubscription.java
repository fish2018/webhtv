package com.fongmi.android.tv.subtitle.source;

import com.google.gson.Gson;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SubtitleSubscription {
    private String protocol="webhtv-subtitle", name=""; private int version=1; private List<SubtitleSourceConfig> subtitles=Collections.emptyList();
    public static SubtitleSubscription parse(String json,String base){ SubtitleSubscription v=new Gson().fromJson(json,SubtitleSubscription.class); if(v==null||v.version!=1) throw new IllegalArgumentException("PROTOCOL_UNSUPPORTED"); List<SubtitleSourceConfig> out=new ArrayList<>(); Set<String> keys=new HashSet<>(); if(v.subtitles!=null) for(SubtitleSourceConfig s:v.subtitles) if(s!=null&&s.valid()&&keys.add(s.getKey())) out.add(s); out.sort((a,b)->Integer.compare(b.getPriority(),a.getPriority())); v.subtitles=out; return v; }
    public List<SubtitleSourceConfig> getSubtitles(){return Collections.unmodifiableList(subtitles);} public int getVersion(){return version;} public String getName(){return name;} public static String resolve(String base,String ext){ if(ext==null||ext.isEmpty()) return ""; if(ext.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) return ext; return URI.create(base).resolve(ext).toString(); }
}
