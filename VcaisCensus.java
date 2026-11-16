import lotus.domino.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

/*
 * VCAIS census: a read-only diagnostic Java agent for the Notes client.
 *
 * It lists every database in the local Notes data directory, with its document
 * count and your access level. For databases whose file path or title contains
 * MATCH, it also reports:
 *   - document counts by Form
 *   - document counts by status value (from the first STATUS_ITEMS field found)
 *   - response, encrypted, Readers-field and Authors-field document counts
 *   - the field names used on each Form (useful for mapping to a new system)
 *
 * It writes NO case data: only counts, field names and short status values.
 * The report is a small text file that is safe to read out or share.
 *
 * Written for old Notes clients: Java 6 syntax only (no try-with-resources,
 * no diamond operator, no lambdas).
 *
 * To use: in Designer, create a Java agent, paste this in as class JavaAgent
 * (or set the base class name to VcaisCensus), set the trigger to
 * "Action menu selection" and the target to "None", then run it from the
 * Actions menu. Edit the settings below first.
 */
public class JavaAgent extends AgentBase {

	// ---- Settings --------------------------------------------------------
	// Folder for the report. It is created if missing.
	private static final String OUTPUT_DIR = "C:\\vcais-export";
	// Databases whose path or title contains this (case-insensitive) get a
	// full scan. Use "" to scan every database (slow on a full data folder).
	private static final String MATCH = "vcais";
	// Fields tried, in order, for the case status.
	private static final String[] STATUS_ITEMS = { "Status", "CaseStatus", "Case_Status" };
	// Status values longer than this are counted as "<long value>" so free
	// text never ends up in the report.
	private static final int MAX_STATUS_LENGTH = 30;
	// At most this many distinct status values are listed per database.
	private static final int MAX_STATUS_VALUES = 40;
	// ----------------------------------------------------------------------

	private static final String[] ACCESS_LEVELS = {
		"No Access", "Depositor", "Reader", "Author", "Editor", "Designer", "Manager" };

	private BufferedWriter out;

	public void NotesMain() {
		File dir = new File(OUTPUT_DIR);
		dir.mkdirs();
		String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
		File reportFile = new File(dir, "vcais-census-" + stamp + ".txt");
		try {
			out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(reportFile), "UTF-8"));
			Session session = getSession();
			line("VCAIS census " + stamp);
			line("Notes version: " + session.getNotesVersion() + " on " + session.getPlatform());
			line("Running as: " + session.getUserName());
			line("Full scan of databases matching: \"" + MATCH + "\"");
			line("");

			DbDirectory dbDir = session.getDbDirectory(null);
			Database db = dbDir.getFirstDatabase(DbDirectory.DATABASE);
			int total = 0;
			while (db != null) {
				total++;
				try {
					censusDatabase(db);
				} catch (Throwable t) {
					line("  ERROR: " + describe(t));
					line("");
				}
				out.flush();
				Database next = dbDir.getNextDatabase();
				db.recycle();
				db = next;
			}
			line("Databases found: " + total);
			line("Done.");
			System.out.println("VCAIS census written to " + reportFile.getAbsolutePath());
		} catch (Throwable t) {
			System.out.println("VCAIS census failed: " + describe(t));
			t.printStackTrace();
			try { line("FAILED: " + describe(t)); } catch (Throwable ignored) { }
		} finally {
			closeQuietly();
		}
	}

	private void censusDatabase(Database db) throws Exception {
		String path = db.getFilePath();
		line("== " + path);
		if (!db.isOpen() && !db.open()) {
			line("  could not open");
			line("");
			return;
		}
		String title = db.getTitle();
		line("  title: " + title);
		line("  size: " + Math.round(db.getSize() / 1024 / 1024) + " MB");
		line("  replica id: " + db.getReplicaID());
		line("  your access: " + accessName(db.getCurrentAccessLevel()));

		DocumentCollection dc = db.getAllDocuments();
		int count = dc.getCount();
		line("  documents visible to you: " + count);

		String match = MATCH.toLowerCase();
		boolean deep = match.length() == 0
			|| path.toLowerCase().indexOf(match) >= 0
			|| (title != null && title.toLowerCase().indexOf(match) >= 0);
		if (deep) {
			scanDocuments(dc);
		}
		dc.recycle();
		line("");
	}

	private void scanDocuments(DocumentCollection dc) throws Exception {
		Map<String, Integer> byForm = new TreeMap<String, Integer>();
		Map<String, Integer> byStatus = new TreeMap<String, Integer>();
		Map<String, TreeSet<String>> fieldsByForm = new TreeMap<String, TreeSet<String>>();
		int responses = 0, encrypted = 0, withReaders = 0, withAuthors = 0, errors = 0;
		String statusField = null;

		Document doc = dc.getFirstDocument();
		while (doc != null) {
			try {
				String form = doc.getItemValueString("Form");
				if (form == null || form.length() == 0) form = "<no Form>";
				bump(byForm, form);
				if (doc.isResponse()) responses++;
				if (doc.isEncrypted()) encrypted++;

				String status = null;
				for (int i = 0; i < STATUS_ITEMS.length && status == null; i++) {
					String v = doc.getItemValueString(STATUS_ITEMS[i]);
					if (v != null && v.length() > 0) {
						status = v;
						if (statusField == null) statusField = STATUS_ITEMS[i];
					}
				}
				if (status == null) status = "<none>";
				else if (status.length() > MAX_STATUS_LENGTH) status = "<long value>";
				if (byStatus.containsKey(status) || byStatus.size() < MAX_STATUS_VALUES) bump(byStatus, status);
				else bump(byStatus, "<other values>");

				TreeSet<String> fields = fieldsByForm.get(form);
				if (fields == null) {
					fields = new TreeSet<String>();
					fieldsByForm.put(form, fields);
				}
				boolean readers = false, authors = false;
				Vector items = doc.getItems();
				for (int i = 0; i < items.size(); i++) {
					Item item = (Item) items.get(i);
					fields.add(item.getName());
					if (item.isReaders()) readers = true;
					if (item.isAuthors()) authors = true;
					item.recycle();
				}
				if (readers) withReaders++;
				if (authors) withAuthors++;
			} catch (Throwable t) {
				errors++;
			}
			Document next = dc.getNextDocument();
			doc.recycle();
			doc = next;
		}

		line("  response documents: " + responses);
		line("  encrypted documents: " + encrypted);
		line("  documents with a Readers field: " + withReaders);
		line("  documents with an Authors field: " + withAuthors);
		if (errors > 0) line("  documents that failed to read: " + errors);
		if (withReaders > 0) {
			line("  NOTE: Readers fields are in use. Documents that leave out your ID are");
			line("  invisible here and cannot be counted. Compare with an admin ID.");
		}
		line("  by Form:");
		for (Iterator<Map.Entry<String, Integer>> it = byForm.entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, Integer> e = it.next();
			line("    " + e.getValue() + "\t" + e.getKey());
		}
		line("  by status (field: " + (statusField == null ? "none of " + join(STATUS_ITEMS) + " found" : statusField) + "):");
		for (Iterator<Map.Entry<String, Integer>> it = byStatus.entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, Integer> e = it.next();
			line("    " + e.getValue() + "\t" + e.getKey());
		}
		line("  fields by Form:");
		for (Iterator<Map.Entry<String, TreeSet<String>>> it = fieldsByForm.entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, TreeSet<String>> e = it.next();
			line("    " + e.getKey() + ": " + join(e.getValue().toArray(new String[0])));
		}
	}

	private static void bump(Map<String, Integer> m, String key) {
		Integer n = m.get(key);
		m.put(key, Integer.valueOf(n == null ? 1 : n.intValue() + 1));
	}

	private static String join(String[] parts) {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) sb.append(", ");
			sb.append(parts[i]);
		}
		return sb.toString();
	}

	private static String accessName(int level) {
		return level >= 0 && level < ACCESS_LEVELS.length ? ACCESS_LEVELS[level] : "level " + level;
	}

	private static String describe(Throwable t) {
		if (t instanceof NotesException) {
			NotesException n = (NotesException) t;
			return "NotesException " + n.id + ": " + n.text;
		}
		return t.toString();
	}

	private void line(String s) throws Exception {
		out.write(s);
		out.write("\r\n");
	}

	private void closeQuietly() {
		if (out != null) {
			try { out.close(); } catch (Throwable ignored) { }
		}
	}
}
