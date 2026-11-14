package com.bxostock.app.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Map;

import retrofit2.Response;

public class ApiError {
    public static String message(Response<?> r) {
        try {
            if (r.errorBody() != null) {
                JsonObject o = JsonParser.parseString(r.errorBody().string()).getAsJsonObject();
                if (o.has("errors") && o.get("errors").isJsonObject()) {
                    StringBuilder sb = new StringBuilder();
                    for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("errors").entrySet()) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(e.getKey()).append(": ").append(e.getValue().getAsString());
                    }
                    return sb.toString();
                }
                if (o.has("message")) return o.get("message").getAsString();
            }
        } catch (Exception ignored) {
        }
        return "Something went wrong (code " + r.code() + ").";
    }

    public static final String NETWORK =
            "Cannot reach the server. It may be waking up, so try again in a minute.";
}