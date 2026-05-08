package org.yuan_dev.eternalFrontier.clan;

import java.util.HashMap;
import java.util.Map;

public class ClanResult {
    private final boolean success;
    private final String messageKey;
    private final Map<String, String> placeholders;
    private final ClanData clan;

    private ClanResult(boolean success, String messageKey, ClanData clan, Map<String, String> placeholders) {
        this.success = success;
        this.messageKey = messageKey;
        this.clan = clan;
        this.placeholders = placeholders;
    }

    public static ClanResult success(ClanData clan, String... kv) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) map.put(kv[i], kv[i+1]);
        return new ClanResult(true, null, clan, map);
    }

    public static ClanResult fail(String key, String... kv) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) map.put(kv[i], kv[i+1]);
        return new ClanResult(false, key, null, map);
    }

    public boolean isSuccess()             { return success; }
    public String getMessageKey()          { return messageKey; }
    public ClanData getClan()              { return clan; }
    public Map<String, String> getPlaceholders() { return placeholders; }
}
