package org.acedo.jmtiempos;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Cliente mínimo de la API JSON-RPC de Leantime (/api/jsonrpc + x-api-key). */
final class LeantimeApi {
    /** Leantime ha respondido con un error: reintentar sin cambiar nada no lo arregla necesariamente. */
    static final class ApiException extends Exception {
        ApiException(String message) { super(message); }
    }

    private final String baseUrl;
    private final String apiKey;

    LeantimeApi(Store s) {
        baseUrl = s.url();
        apiKey = s.apiKey();
    }

    private Object rpc(String method, JSONObject params) throws IOException, ApiException, JSONException {
        HttpURLConnection c = (HttpURLConnection) new URL(baseUrl + "/api/jsonrpc").openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("User-Agent", "JM-Tiempos/1.0");
        c.setRequestProperty("x-api-key", apiKey);
        byte[] body = new JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("method", "leantime.rpc." + method)
                .put("params", params)
                .toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream o = c.getOutputStream()) {
            o.write(body);
        }
        int code = c.getResponseCode();
        String text = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
        JSONObject data;
        try {
            data = new JSONObject(text);
        } catch (JSONException e) {
            throw new ApiException("HTTP " + code + ": respuesta inesperada del servidor");
        }
        if (data.has("error")) {
            throw new ApiException(data.getJSONObject("error").optString("message", "Error de Leantime"));
        }
        if (code >= 400) throw new ApiException("HTTP " + code);
        return data.opt("result");
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        try (InputStream i = in) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = i.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        }
    }

    /** Tareas abiertas: [{id, headline, projectName}], sin hitos. */
    JSONArray openTasks() throws IOException, ApiException, JSONException {
        JSONObject criteria = new JSONObject().put("currentProject", "").put("currentUser", "");
        Object r = rpc("Tickets.getAll", new JSONObject().put("searchCriteria", criteria).put("limit", 500));
        JSONArray out = new JSONArray();
        if (!(r instanceof JSONArray)) return out;
        JSONArray all = (JSONArray) r;
        for (int i = 0; i < all.length(); i++) {
            JSONObject t = all.getJSONObject(i);
            int status = t.optInt("status", -1);
            boolean open = status == 3 || status == 4 || status == 2 || status == 1;
            if (!open || "milestone".equals(t.optString("type"))) continue;
            out.put(new JSONObject()
                    .put("id", t.getInt("id"))
                    .put("headline", t.optString("headline"))
                    .put("projectName", t.optString("projectName")));
        }
        return out;
    }

    /** Pasa la tarea al estado "hecho" de su proyecto, sea cual sea su número en ese proyecto. */
    void markDone(int ticketId) throws IOException, ApiException, JSONException {
        Object r = rpc("Tickets.setTicketStatusByType", new JSONObject().put("ticketId", ticketId).put("statusType", "done"));
        if (Boolean.FALSE.equals(r)) throw new ApiException("Leantime no ha cambiado el estado de la tarea");
    }

    /**
     * Registra el tiempo con la hora real de inicio. Leantime suma las horas y mezcla las
     * descripciones de los registros con la misma fecha y hora: al llevar segundos, cada
     * sesión del cronómetro queda como un registro propio.
     */
    void logTime(JSONObject item) throws IOException, ApiException, JSONException {
        JSONObject params = new JSONObject()
                .put("timestamp", item.getLong("start") / 1000)
                .put("hours", item.getDouble("hours"))
                .put("description", item.optString("note"))
                .put("kind", item.optString("kind", Store.KIND_NOT_BILLABLE));
        String userId = item.optString("userId");
        if (!userId.isEmpty()) params.put("userId", Integer.parseInt(userId));
        Object r = rpc("Timesheets.logTime", new JSONObject().put("ticketId", item.getInt("ticketId")).put("params", params));
        if (Boolean.FALSE.equals(r)) throw new ApiException("Leantime no ha guardado el registro");
    }
}
