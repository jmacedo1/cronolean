package org.acedo.jmtiempos;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Estado de la app en el dispositivo: ajustes, cronómetro y registros pendientes de enviar. */
final class Store {
    static final String KIND_NOT_BILLABLE = "GENERAL_NOT_BILLABLE";
    static final String KIND_BILLABLE = "GENERAL_BILLABLE";
    private static final String KEY_ALIAS = "jm_tiempos_api";

    private final SharedPreferences p;

    Store(Context c) {
        p = c.getApplicationContext().getSharedPreferences("jm_tiempos", Context.MODE_PRIVATE);
    }

    String url() { return p.getString("url", ""); }
    String userId() { return p.getString("userId", ""); }
    String kind() { return p.getString("kind", KIND_NOT_BILLABLE); }
    boolean configured() { return !p.getString("apiKey", "").isEmpty(); }

    void saveSettings(String url, String apiKey, String userId, String kind) {
        SharedPreferences.Editor e = p.edit()
                .putString("url", url.trim().replaceAll("/+$", ""))
                .putString("userId", userId.trim())
                .putString("kind", kind);
        if (!apiKey.trim().isEmpty()) e.putString("apiKey", encrypt(apiKey.trim()));
        e.apply();
    }

    String apiKey() {
        String v = p.getString("apiKey", "");
        return v.isEmpty() ? "" : decrypt(v);
    }

    int ticketId() { return p.getInt("ticketId", 0); }
    String ticketName() { return p.getString("ticketName", ""); }
    String note() { return p.getString("note", ""); }
    long start() { return p.getLong("start", 0); }
    boolean running() { return start() > 0; }
    long reviewStart() { return p.getLong("reviewStart", 0); }
    boolean reviewPending() { return reviewStart() > 0; }
    double reviewHours() { return Double.longBitsToDouble(p.getLong("reviewHours", 0)); }
    String status() { return p.getString("status", ""); }
    String lastError() { return p.getString("lastError", ""); }

    int ticketProject() { return p.getInt("ticketProject", 0); }
    /** Estado de la tarea actual; vacío si aún no se conoce. */
    String ticketStatusName() { return p.getString("ticketStatusName", ""); }
    int ticketStatus() { return p.getInt("ticketStatus", Integer.MIN_VALUE); }

    void setTicket(int id, String name, int project, int status) {
        p.edit().putInt("ticketId", id).putString("ticketName", name).apply();
        clearStopStatus();
        clearStartPrevStatus();
        setTicketState(project, status);
        rememberRecent(id);
    }

    void setTicketState(int project, int status) {
        p.edit().putInt("ticketProject", project).putInt("ticketStatus", status)
                .putString("ticketStatusName", statusName(statusLabels(project), status)).apply();
    }

    void clearTicket() {
        p.edit().remove("ticketId").remove("ticketName").remove("ticketProject")
                .remove("ticketStatus").remove("ticketStatusName").apply();
    }

    /** Estado elegido para cuando se pare el cronómetro. Las claves conservan el nombre de cuando solo se podía cerrar. */
    boolean hasStopStatus() { return p.getBoolean("markDone", false); }
    int stopStatus() { return p.getInt("closeStatus", 0); }
    String stopStatusName() { return p.getString("closeStatusName", ""); }
    boolean stopStatusCloses() { return p.getBoolean("closeCloses", true); }
    void setStopStatus(int status, String name, boolean closes) {
        p.edit().putBoolean("markDone", true).putInt("closeStatus", status)
                .putString("closeStatusName", name).putBoolean("closeCloses", closes).apply();
    }
    void clearStopStatus() {
        p.edit().remove("markDone").remove("closeStatus").remove("closeStatusName").remove("closeCloses").apply();
    }

    /** Estado que tenía la tarea antes de iniciar, para devolverla si el inicio fue sin querer. */
    int startPrevStatus() { return p.getInt("startPrevStatus", Integer.MIN_VALUE); }
    void setStartPrevStatus(int status) { p.edit().putInt("startPrevStatus", status).apply(); }
    void clearStartPrevStatus() { p.edit().remove("startPrevStatus").apply(); }

    /** Estados guardados de un proyecto, para poder cambiarlos sin cobertura. */
    JSONArray statusLabels(int project) {
        try {
            return new JSONArray(p.getString("labels" + project, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }
    void setStatusLabels(int project, JSONArray labels) { p.edit().putString("labels" + project, labels.toString()).apply(); }

    static boolean isDone(JSONArray labels, int status) {
        for (int i = 0; i < labels.length(); i++) {
            JSONObject l = labels.optJSONObject(i);
            if (l.optInt("id") == status) return l.optBoolean("done");
        }
        // Sin etiqueta conocida: los números de Leantime para cerrada y archivada.
        return status <= 0;
    }

    static String statusName(JSONArray labels, int status) {
        for (int i = 0; i < labels.length(); i++) {
            JSONObject l = labels.optJSONObject(i);
            if (l.optInt("id") == status) return l.optString("name");
        }
        return "";
    }

    void setNote(String v) { p.edit().putString("note", v.trim()).apply(); }
    void setStart(long ms) { p.edit().putLong("start", ms).apply(); }
    void setStatus(String v) { p.edit().putString("status", v).apply(); }
    void setLastError(String v) { p.edit().putString("lastError", v).apply(); }

    void setReview(long startMs, double hours) {
        p.edit().putLong("reviewStart", startMs).putLong("reviewHours", Double.doubleToLongBits(hours)).apply();
    }
    void clearReview() { p.edit().remove("reviewStart").remove("reviewHours").apply(); }

    List<Integer> recents() {
        List<Integer> out = new ArrayList<>();
        for (String s : p.getString("recents", "").split(",")) {
            if (!s.isEmpty()) out.add(Integer.parseInt(s));
        }
        return out;
    }

    private void rememberRecent(int id) {
        List<Integer> r = recents();
        r.remove(Integer.valueOf(id));
        r.add(0, id);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(r.size(), 20); i++) {
            if (i > 0) b.append(',');
            b.append(r.get(i));
        }
        p.edit().putString("recents", b.toString()).apply();
    }

    /** Empresa con la que se abre el selector de tareas; vacío = todas. */
    String defaultProject() { return p.getString("defaultProject", ""); }
    void setDefaultProject(String v) { p.edit().putString("defaultProject", v).apply(); }

    /** Empresas de la última lista de tareas, por orden alfabético. En Leantime son los proyectos. */
    List<String> projects() {
        java.util.TreeSet<String> names = new java.util.TreeSet<>();
        try {
            JSONArray a = new JSONArray(tasksJson());
            for (int i = 0; i < a.length(); i++) {
                String name = a.getJSONObject(i).optString("projectName");
                if (!name.isEmpty()) names.add(name);
            }
        } catch (JSONException ignored) {
            // Una lista guardada ilegible equivale a no tener lista: se vuelve a pedir.
        }
        return new ArrayList<>(names);
    }

    String tasksJson() { return p.getString("tasks", "[]"); }
    void setTasksJson(String v) { p.edit().putString("tasks", v).apply(); }

    synchronized JSONArray queue() {
        try {
            return new JSONArray(p.getString("queue", "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    synchronized void enqueue(int ticketId, String ticketName, long startMs, double hours, String note) {
        try {
            JSONObject o = new JSONObject()
                    .put("id", UUID.randomUUID().toString())
                    .put("ticketId", ticketId)
                    .put("ticketName", ticketName)
                    .put("start", startMs)
                    .put("hours", hours)
                    .put("note", note)
                    .put("kind", kind())
                    .put("userId", userId());
            p.edit().putString("queue", queue().put(o).toString()).commit();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Va en un registro aparte del tiempo: un reintento no debe volver a sumar las horas. */
    synchronized void enqueueDone(int ticketId, String ticketName) {
        try {
            JSONObject o = new JSONObject()
                    .put("id", UUID.randomUUID().toString())
                    .put("type", "done")
                    .put("ticketId", ticketId)
                    .put("ticketName", ticketName);
            p.edit().putString("queue", queue().put(o).toString()).commit();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    synchronized void enqueueStatus(int ticketId, String ticketName, int status, String statusName) {
        try {
            JSONObject o = new JSONObject()
                    .put("id", UUID.randomUUID().toString())
                    .put("type", "status")
                    .put("ticketId", ticketId)
                    .put("ticketName", ticketName)
                    .put("status", status)
                    .put("statusName", statusName);
            p.edit().putString("queue", queue().put(o).toString()).commit();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    void removeCachedTask(int ticketId) {
        try {
            JSONArray a = new JSONArray(tasksJson());
            JSONArray out = new JSONArray();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (o.optInt("id") != ticketId) out.put(o);
            }
            setTasksJson(out.toString());
        } catch (JSONException ignored) {
            // La lista guardada se vuelve a pedir al abrir el selector.
        }
    }

    synchronized void removeFromQueue(String id) {
        JSONArray q = queue();
        JSONArray out = new JSONArray();
        for (int i = 0; i < q.length(); i++) {
            JSONObject o = q.optJSONObject(i);
            if (o != null && !id.equals(o.optString("id"))) out.put(o);
        }
        p.edit().putString("queue", out.toString()).commit();
    }

    private static SecretKey secretKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) return (SecretKey) ks.getKey(KEY_ALIAS, null);
        KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        g.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return g.generateKey();
    }

    private static String encrypt(String plain) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, secretKey());
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(c.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String decrypt(String stored) {
        try {
            String[] parts = stored.split(":");
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            return new String(c.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
