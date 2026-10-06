/* SPDX-License-Identifier: AGPL-3.0-or-later */
package org.freeyourgadget.weightrecorder;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public final class WeightDatabase extends SQLiteOpenHelper {
    public static final class Record {
        public long id, timeMillis; public double weight; public String endpoint, state, message, device;
    }
    private static WeightDatabase instance;
    public static synchronized WeightDatabase get(Context c) {
        if (instance == null) instance = new WeightDatabase(c.getApplicationContext());
        return instance;
    }
    WeightDatabase(Context c) { super(c, "weight.db", null, 2); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE weight (id INTEGER PRIMARY KEY AUTOINCREMENT, time_ms INTEGER NOT NULL, weight REAL NOT NULL, device TEXT NOT NULL, endpoint TEXT NOT NULL, state TEXT NOT NULL, message TEXT NOT NULL DEFAULT '', source TEXT NOT NULL DEFAULT 'live', UNIQUE(device,time_ms,weight))");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 1 && newVersion == 2) db.execSQL("ALTER TABLE weight ADD COLUMN source TEXT NOT NULL DEFAULT 'live'");
        else throw new IllegalStateException("Unsupported database upgrade");
    }
    public synchronized long save(ScaleProtocol.Measurement sample, String device, String endpoint) {
        return save(sample, device, endpoint, false);
    }
    public synchronized long save(ScaleProtocol.Measurement sample, String device, String endpoint, boolean historical) {
        SQLiteDatabase db = getWritableDatabase();
        long tolerance = sample.deviceTime ? 0 : 10000;
        try (Cursor c = db.rawQuery("SELECT id,state,source FROM weight WHERE device=? AND weight=? AND time_ms BETWEEN ? AND ? LIMIT 1",
                new String[]{device, Double.toString(sample.weightKg), Long.toString(sample.timeMillis - tolerance), Long.toString(sample.timeMillis + tolerance)})) {
            if (c.moveToFirst()) {
                // A history packet can arrive before the live packet for the same weighing.
                // Promote it exactly once so that packet order cannot suppress the webhook.
                if (!historical && c.getString(1).equals("local") && c.getString(2).equals("history")) {
                    long id = c.getLong(0); ContentValues promoted = new ContentValues();
                    promoted.put("source", "live"); promoted.put("endpoint", endpoint);
                    promoted.put("state", endpoint.isEmpty() ? "local" : "pending");
                    db.update("weight", promoted, "id=?", new String[]{Long.toString(id)});
                    return endpoint.isEmpty() ? -1 : id;
                }
                return -1;
            }
        }
        ContentValues values = new ContentValues();
        values.put("time_ms", sample.timeMillis); values.put("weight", sample.weightKg); values.put("device", device);
        values.put("endpoint", endpoint); values.put("state", endpoint.isEmpty() ? "local" : "pending");
        values.put("source", historical ? "history" : "live");
        return db.insertWithOnConflict("weight", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }
    private Record record(Cursor c) {
        Record r = new Record(); r.id = c.getLong(0); r.timeMillis = c.getLong(1); r.weight = c.getDouble(2);
        r.device = c.getString(3); r.endpoint = c.getString(4); r.state = c.getString(5); r.message = c.getString(6); return r;
    }
    private static final String COLUMNS = "id,time_ms,weight,device,endpoint,state,message";
    public Record find(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + COLUMNS + " FROM weight WHERE id=?", new String[]{Long.toString(id)})) { return c.moveToFirst() ? record(c) : null; }
    }
    public List<Record> all() {
        List<Record> records = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT " + COLUMNS + " FROM weight ORDER BY time_ms,id", null)) { while (c.moveToNext()) records.add(record(c)); }
        return records;
    }
    public synchronized void update(long id, String state, String message) {
        ContentValues values = new ContentValues(); values.put("state", state); values.put("message", message);
        getWritableDatabase().update("weight", values, "id=?", new String[]{Long.toString(id)});
    }
    public synchronized boolean prepareRetry(long id, String endpoint) {
        if (!WebhookContract.validUrl(endpoint)) return false;
        ContentValues values = new ContentValues(); values.put("endpoint", endpoint); values.put("state", "pending"); values.put("message", "");
        return getWritableDatabase().update("weight", values, "id=? AND state IN ('failed','local')", new String[]{Long.toString(id)}) == 1;
    }
}
