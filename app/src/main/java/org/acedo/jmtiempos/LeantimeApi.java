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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
        c.setRequestProperty("User-Agent", "CronoLean/1.8");
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

    /**
     * Estados de un proyecto en el orden del tablero: [{id, name, done}].
     * Cada proyecto puede tener los suyos, también con otros nombres y números.
     */
    JSONArray statusLabels(int projectId) throws IOException, ApiException, JSONException {
        Object r = rpc("Tickets.getStatusLabels", new JSONObject().put("projectId", projectId));
        List<JSONObject> labels = new ArrayList<>();
        if (r instanceof JSONObject) {
            JSONObject all = (JSONObject) r;
            for (Iterator<String> keys = all.keys(); keys.hasNext(); ) {
                String key = keys.next();
                JSONObject l = all.getJSONObject(key);
                labels.add(new JSONObject()
                        .put("id", Integer.parseInt(key))
                        .put("name", l.optString("name"))
                        .put("done", "DONE".equals(l.optString("statusType")))
                        .put("sortKey", l.optInt("sortKey")));
            }
        }
        labels.sort(Comparator.comparingInt(l -> l.optInt("sortKey")));
        return new JSONArray(labels);
    }

    /** Proyecto y estado actuales de una tarea. */
    JSONObject ticket(int ticketId) throws IOException, ApiException, JSONException {
        Object r = rpc("Tickets.getTicket", new JSONObject().put("id", ticketId));
        if (!(r instanceof JSONObject)) throw new ApiException("Leantime no encuentra la tarea");
        return (JSONObject) r;
    }

    /** Tareas abiertas: [{id, headline, projectId, projectName, status}], sin hitos. Guarda los estados de cada proyecto. */
    JSONArray openTasks(Store s) throws IOException, ApiException, JSONException {
        JSONObject criteria = new JSONObject().put("currentProject", "").put("currentUser", "");
        Object r = rpc("Tickets.getAll", new JSONObject().put("searchCriteria", criteria).put("limit", 500));
        JSONArray out = new JSONArray();
        if (!(r instanceof JSONArray)) return out;
        JSONArray all = (JSONArray) r;
        Map<Integer, JSONArray> labels = new HashMap<>();
        for (int i = 0; i < all.length(); i++) {
            JSONObject t = all.getJSONObject(i);
            if ("milestone".equals(t.optString("type"))) continue;
            int project = t.optInt("projectId");
            if (!labels.containsKey(project)) {
                labels.put(project, statusLabels(project));
                s.setStatusLabels(project, labels.get(project));
            }
            int status = t.optInt("status", -1);
            if (Store.isDone(labels.get(project), status)) continue;
            out.put(new JSONObject()
                    .put("id", t.getInt("id"))
                    .put("headline", t.optString("headline"))
                    .put("projectId", project)
                    .put("projectName", t.optString("projectName"))
                    .put("status", status));
        }
        return out;
    }

    void setStatus(int ticketId, int status) throws IOException, ApiException, JSONException {
        Object r = rpc("Tickets.patchTicket", new JSONObject().put("id", ticketId)
                .put("values", new JSONObject().put("status", status)));
        if (Boolean.FALSE.equals(r)) throw new ApiException("Leantime no ha cambiado el estado de la tarea");
    }

    /** Pasa la tarea al estado "hecho" de su proyecto. Solo para peticiones guardadas por versiones anteriores. */
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
