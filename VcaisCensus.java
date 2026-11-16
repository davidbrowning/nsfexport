import lotus.domino.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

/*
 * VCAIS census: a read-only diagnostic Java agent for the Notes client.
 *
 * It lists every database in the local Notes data directory, and in any
 * EXTRA_FOLDERS, with its document count and your access level. For databases whose file path or title contains
 * MATCH, it also reports:
 *   - document counts by Form
 *   - document counts by status value (from the first STATUS_ITEMS field found)
 *   - response, encrypted, Readers-field and Authors-field document counts
 *   - the field names used on each Form (useful for mapping to a new system)
 *
 * It writes NO case data: only counts, field names and short status values.
 * The report is a small text file that is safe to read out or share.
 *
 * Written for old Notes clients (Notes 6 and later): Java 1.3 only, so no
 * generics, no for-each loops and no regular expressions.
 *
 * To use: in Designer, create a Java agent, paste this in as class JavaAgent
 * (or set the base class name to VcaisCensus), set the trigger to
 * "Action menu selection" and the target to "None", then run it from the
 * Actions menu. Edit the settings below first.
 */
public class JavaAgent extends AgentBase {

	// ---- Settings --------------------------------------------------------
	// Extra folders to search for .nsf files, besides the Notes data folder.
	// Use this when the databases are kept somewhere else. Use forward
	// slashes, for example: { "C:/Users/user/Desktop/Domino/data" }
	private static final String[] EXTRA_FOLDERS = { };
	// Folder for the output. Leave it "" to use a "vcais-export" folder on the
	// Desktop. It is created if missing. Use forward slashes, e.g. "D:/export".
	private static final String OUTPUT_DIR = "";
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
	private final Set seen = new HashSet();

	public void NotesMain() {
		File dir = outputFolder();
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
			for (int i = 0; i < EXTRA_FOLDERS.length; i++) {
				File folder = new File(EXTRA_FOLDERS[i]);
				line("-- Searching folder " + folder.getAbsolutePath());
				if (!folder.isDirectory()) {
					line("  not a folder, or not found");
					line("");
					continue;
				}
				total += scanFolder(dbDir, folder, 0);
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

	/** Opens every .nsf file under folder and reports on it. Returns how many were found. */
	private int scanFolder(DbDirectory dbDir, File folder, int depth) throws Exception {
		File[] files = folder.listFiles();
		if (files == null || depth > 8) return 0;
		int found = 0;
		for (int i = 0; i < files.length; i++) {
			File f = files[i];
			if (f.isDirectory()) {
				found += scanFolder(dbDir, f, depth + 1);
			} else if (f.getName().toLowerCase().endsWith(".nsf")) {
				found++;
				String path = f.getAbsolutePath().replace('\\', '/');
				try {
					Database db = dbDir.openDatabase(path);
					if (db == null) {
						line("== " + path);
						line("  could not open");
						line("");
					} else {
						censusDatabase(db);
						db.recycle();
					}
				} catch (Throwable t) {
					line("== " + path);
					line("  ERROR: " + describe(t));
					line("");
				}
				out.flush();
			}
		}
		return found;
	}

	private void censusDatabase(Database db) throws Exception {
		String path = db.getFilePath();
		String key = path.replace('\\', '/').toLowerCase();
		if (!seen.add(key)) return;
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
		Map byForm = new TreeMap();
		Map byStatus = new TreeMap();
		Map fieldsByForm = new TreeMap();
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

				TreeSet fields = (TreeSet) fieldsByForm.get(form);
				if (fields == null) {
					fields = new TreeSet();
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
		for (Iterator it = byForm.entrySet().iterator(); it.hasNext();) {
			Map.Entry e = (Map.Entry) it.next();
			line("    " + e.getValue() + "\t" + e.getKey());
		}
		line("  by status (field: " + (statusField == null ? "none of " + join(STATUS_ITEMS) + " found" : statusField) + "):");
		for (Iterator it = byStatus.entrySet().iterator(); it.hasNext();) {
			Map.Entry e = (Map.Entry) it.next();
			line("    " + e.getValue() + "\t" + e.getKey());
		}
		line("  fields by Form:");
		for (Iterator it = fieldsByForm.entrySet().iterator(); it.hasNext();) {
			Map.Entry e = (Map.Entry) it.next();
			line("    " + e.getKey() + ": " + join((String[]) ((TreeSet) e.getValue()).toArray(new String[0])));
		}
	}

	private static void bump(Map m, String key) {
		Integer n = (Integer) m.get(key);
		m.put(key, new Integer(n == null ? 1 : n.intValue() + 1));
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

	private static File outputFolder() {
		if (OUTPUT_DIR.length() > 0) return new File(OUTPUT_DIR);
		File desktop = new File(System.getProperty("user.home"), "Desktop");
		if (desktop.isDirectory()) return new File(desktop, "vcais-export");
		return new File("C:/vcais-export");
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
