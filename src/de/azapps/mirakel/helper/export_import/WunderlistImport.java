package de.azapps.mirakel.helper.export_import;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;

import android.content.Context;
import android.util.Pair;
import android.util.SparseIntArray;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import de.azapps.mirakel.Mirakel.NoSuchListException;
import de.azapps.mirakel.helper.Log;
import de.azapps.mirakel.model.list.ListMirakel;
import de.azapps.mirakel.model.recurring.Recurring;
import de.azapps.mirakel.model.task.Task;
import de.azapps.mirakelandroid.R;

public class WunderlistImport {
	private static final String TAG = "WunderlistImport";
	private static SparseIntArray taskMapping;

	public static Boolean exec(Context ctx, String file_path) {
		String json;
		try {
			json = ExportImport.getStringFromFile(file_path, ctx);
		} catch (IOException e) {
			Log.e(TAG, "cannot read File");
			return false;
		}
		Log.i(TAG, json);
		JsonObject i;
		try {
			i = new JsonParser().parse(json).getAsJsonObject();
		} catch (JsonSyntaxException e2) {
			Log.e(TAG, "malformed backup");
			return false;
		}
		Set<Entry<String, JsonElement>> f = i.entrySet();
		SparseIntArray listMapping = new SparseIntArray();
		List<Pair<Integer, String>> contents = new ArrayList<Pair<Integer, String>>();
		taskMapping = new SparseIntArray();
		for (Entry<String, JsonElement> e : f) {
			if (e.getKey().equals("categorys")) {
				Iterator<JsonElement> iter = e.getValue().getAsJsonArray()
						.iterator();
				while (iter.hasNext()) {
					listMapping = parseList(iter.next().getAsJsonObject(),
							listMapping);
				}
			} else if (e.getKey().equals("tasks")) {
				Iterator<JsonElement> iter = e.getValue().getAsJsonArray()
						.iterator();
				while (iter.hasNext()) {
					contents = parseTask(iter.next().getAsJsonObject(),
							listMapping, contents, ctx);
				}
			} else {
				Log.d(TAG, e.getKey());
			}
		}
		for (Pair<Integer, String> pair : contents) {
			Task t = Task.get(taskMapping.get(pair.first));
			if (t == null) {
				Log.d(TAG, "Task not found");
				continue;
			}
			String oldContent = t.getContent();
			t.setContent(oldContent == null || oldContent.equals("") ? pair.second
					: oldContent + "\n" + pair.second);
			try {
				t.save();
			} catch (NoSuchListException e1) {
				Log.wtf(TAG, "List did vanish");
			}
		}
		return true;
	}

	private static SparseIntArray parseList(JsonObject jsonList,
			SparseIntArray listMapping) {
		String name = jsonList.get("name").getAsString();
		int id = jsonList.get("id").getAsInt();
		ListMirakel l = ListMirakel.newList(name);
		listMapping.put(id, l.getId());
		return listMapping;

	}

	private static List<Pair<Integer, String>> parseTask(JsonObject jsonTask,
			SparseIntArray listMapping, List<Pair<Integer, String>> contents,
			Context ctx) {
		String name = jsonTask.get("title").getAsString();
		if (jsonTask.has("parentId")) {
			contents.add(new Pair<Integer, String>(jsonTask.get("parentId")
					.getAsInt(), name));
			return contents;
		}
		int list_id = jsonTask.get("categoryId").getAsInt();
		Task t = Task.newTask(name,
				ListMirakel.getList(listMapping.get(list_id)));
		taskMapping.put(jsonTask.get("id").getAsInt(), (int) t.getId());
		if (jsonTask.has("dueDate")) {
			Calendar due = new GregorianCalendar();
			long dueMs = jsonTask.get("dueDate").getAsLong();
			if (dueMs > 0) {
				due.setTimeInMillis(dueMs);
				t.setDue(due);
			}
		}
		if (jsonTask.has("priority")) {
			int prio = 0;
			if (jsonTask.get("priority").getAsString().equals("High")) {
				prio = 2;
			}
			t.setPriority(prio);
		}
		if (jsonTask.has("status")) {
			t.setDone(jsonTask.get("status").getAsString().equals("DONE"));
		}
		if (jsonTask.has("repeatMethod")) {
			String repeat = jsonTask.get("repeatMethod").getAsString();

			if (!repeat.equals("TASK_REPEAT_OFF")) {
				Recurring r = null;
				if (repeat.equals("TASK_REPEAT_DAY")) {
					r = Recurring.get(1, 0, 0);
					if (r == null) {
						r = Recurring.newRecurring(
								ctx.getString(R.string.daily), 0, 0, 1, 0, 0,
								true, null, null, false);
					}
				} else if (repeat.equals("TASK_REPEAT_WEEK")) {
					r = Recurring.get(7, 0, 0);
					if (r == null) {
						r = Recurring.newRecurring(
								ctx.getString(R.string.weekly), 0, 0, 7, 0, 0,
								true, null, null, false);
					}
				} else if (repeat.equals("TASK_REPEAT_MONTH")) {
					r = Recurring.get(0, 1, 0);
					if (r == null) {
						r = Recurring.newRecurring(
								ctx.getString(R.string.monthly), 0, 0, 0, 1, 0,
								true, null, null, false);
					}
				} else if (repeat.equals("TASK_REPEAT_YEAR")) {
					r = Recurring.get(0, 0, 1);
					if (r == null) {
						r = Recurring.newRecurring(
								ctx.getString(R.string.yearly), 0, 0, 0, 0, 1,
								true, null, null, false);
					}
				}
				if (r != null) {
					t.setRecurrence(r.getId());
				} else {
					Log.d(TAG, repeat);
				}

			}
		}
		try {
			t.save();
		} catch (NoSuchListException e) {
			Log.wtf(TAG, "list did vanish");
		}
		return contents;
	}

}
