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

/*
 * VCAIS export: exports every document in one or more Notes databases to DXL.
 *
 * Fixes over the original one-shot agent:
 *   - Exports one document at a time and appends to the file, so memory use
 *     stays flat no matter how big the database is. (The original built the
 *     whole database as one Java String and could run out of memory.)
 *   - Always closes its files, so nothing is cut off at the end.
 *   - Keeps going when a document fails, and logs which one and why.
 *   - Takes a list of databases (open cases, closed cases, archives).
 *   - Writes a manifest (one CSV row per document, no case data) and checks the
 *     number exported against the database's own count.
 *
 * Output, per database, in OUTPUT_DIR:
 *   <name>-<time>.dxl           all documents, wrapped in one <vcaisexport> root
 *   <name>-<time>-manifest.csv  note id, UNID, form, status, result per document
 *   vcais-export-<time>.log     progress, errors and the final count check
 *
 * Written for old Notes clients: Java 6 syntax only.
 *
 * To use: run the census agent first to find the right database file names.
 * Edit DATABASES below. In Designer, create a Java agent, paste this in as
 * class JavaAgent (or set the base class name to VcaisExport), set the trigger
 * to "Action menu selection" and the target to "None", then run it from the
 * Actions menu.
 */
public class JavaAgent extends AgentBase {

	// ---- Settings --------------------------------------------------------
	// Database file paths. Copy them from the census report
	// (a full path, or a path relative to the Notes data folder).
	// Use forward slashes: "C:/Users/user/Desktop/Domino/data/VCAIS/x.nsf"
	private static final String[] DATABASES = { "ClosedVCAIS.nsf" };
	// Folder for the output. It is created if missing.
	private static final String OUTPUT_DIR = "C:\\vcais-export";
	// Fields tried, in order, for the case status column in the manifest.
	private static final String[] STATUS_ITEMS = { "Status", "CaseStatus", "Case_Status" };
	// Write a progress line to the log every this many documents.
	private static final int PROGRESS_EVERY = 500;
	// ----------------------------------------------------------------------

	private BufferedWriter log;

	public void NotesMain() {
		File dir = new File(OUTPUT_DIR);
		dir.mkdirs();
		String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
		File logFile = new File(dir, "vcais-export-" + stamp + ".log");
		try {
			log = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logFile), "UTF-8"));
			Session session = getSession();
			log("VCAIS export " + stamp + ", running as " + session.getUserName());
			DbDirectory dbDir = session.getDbDirectory(null);
			boolean allComplete = true;
			for (int i = 0; i < DATABASES.length; i++) {
				try {
					if (!exportDatabase(session, dbDir, DATABASES[i], dir, stamp)) allComplete = false;
				} catch (Throwable t) {
					allComplete = false;
					log("ERROR exporting " + DATABASES[i] + ": " + describe(t));
				}
			}
			log(allComplete ? "ALL COMPLETE" : "SOME EXPORTS INCOMPLETE: see above");
			System.out.println("VCAIS export finished. Log: " + logFile.getAbsolutePath());
		} catch (Throwable t) {
			System.out.println("VCAIS export failed: " + describe(t));
			t.printStackTrace();
			try { log("FAILED: " + describe(t)); } catch (Throwable ignored) { }
		} finally {
			if (log != null) {
				try { log.close(); } catch (Throwable ignored) { }
			}
		}
	}

	/** Returns true when every document was exported. */
	private boolean exportDatabase(Session session, DbDirectory dbDir, String path, File dir, String stamp)
			throws Exception {
		log("== " + path);
		Database db = dbDir.openDatabase(path);
		if (db == null || !db.isOpen()) {
			log("  could not open " + path);
			return false;
		}
		String base = new File(path).getName().replaceAll("(?i)\\.nsf$", "") + "-" + stamp;
		File dxlFile = new File(dir, base + ".dxl");
		File csvFile = new File(dir, base + "-manifest.csv");

		DocumentCollection dc = db.getAllDocuments();
		int expected = dc.getCount();
		log("  documents visible to this ID: " + expected);

		DxlExporter exporter = session.createDxlExporter();
		exporter.setExitOnFirstFatalError(false);

		BufferedWriter dxl = null;
		BufferedWriter csv = null;
		int exported = 0, failed = 0, seen = 0;
		Map<String, Integer> byForm = new TreeMap<String, Integer>();
		try {
			dxl = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(dxlFile), "UTF-8"));
			csv = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(csvFile), "UTF-8"));
			dxl.write("<?xml version='1.0' encoding='utf-8'?>\r\n");
			dxl.write("<vcaisexport database='" + xmlAttr(path) + "' replicaid='" + xmlAttr(db.getReplicaID())
				+ "' exported='" + stamp + "'>\r\n");
			csv.write("noteid,unid,form,status,is_response,parent_unid,encrypted,dxl_chars,result,error\r\n");

			Document doc = dc.getFirstDocument();
			while (doc != null) {
				seen++;
				String noteId = "", unid = "", form = "", status = "", parent = "", error = "";
				boolean response = false, encrypted = false;
				int chars = 0;
				boolean ok = false;
				try {
					noteId = doc.getNoteID();
					unid = doc.getUniversalID();
					form = doc.getItemValueString("Form");
					status = statusOf(doc);
					response = doc.isResponse();
					if (response) parent = doc.getParentDocumentUNID();
					encrypted = doc.isEncrypted();

					String body = stripProlog(exporter.exportDxl(doc));
					chars = body.length();
					dxl.write(body);
					dxl.write("\r\n");
					ok = true;
					exported++;
					bump(byForm, form == null || form.length() == 0 ? "<no Form>" : form);
				} catch (Throwable t) {
					// Includes OutOfMemoryError on a single huge document.
					failed++;
					error = describe(t);
					log("  FAILED note " + noteId + " (UNID " + unid + "): " + error);
				}
				csv.write(csvRow(new String[] { noteId, unid, form, status, response ? "Y" : "N", parent,
					encrypted ? "Y" : "N", String.valueOf(chars), ok ? "ok" : "failed", error }));

				if (seen % PROGRESS_EVERY == 0) {
					log("  progress: " + seen + " of " + expected);
					dxl.flush();
					csv.flush();
				}
				Document next = dc.getNextDocument();
				doc.recycle();
				doc = next;
			}
			dxl.write("</vcaisexport>\r\n");
		} finally {
			if (dxl != null) {
				try { dxl.close(); } catch (Throwable t) { log("  ERROR closing DXL file: " + describe(t)); }
			}
			if (csv != null) {
				try { csv.close(); } catch (Throwable t) { log("  ERROR closing manifest: " + describe(t)); }
			}
		}

		try {
			String exporterLog = exporter.getLog();
			if (exporterLog != null && exporterLog.trim().length() > 0) {
				log("  exporter log: " + exporterLog.trim());
			}
		} catch (Throwable ignored) { }

		log("  exported by Form:");
		for (Iterator<Map.Entry<String, Integer>> it = byForm.entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, Integer> e = it.next();
			log("    " + e.getValue() + "\t" + e.getKey());
		}
		boolean complete = failed == 0 && exported == expected;
		log("  " + (complete ? "COMPLETE" : "INCOMPLETE") + ": exported " + exported + ", failed " + failed
			+ ", walked " + seen + ", expected " + expected);
		log("  wrote " + dxlFile.getAbsolutePath() + " (" + dxlFile.length() + " bytes)");
		log("  wrote " + csvFile.getAbsolutePath());
		dc.recycle();
		exporter.recycle();
		db.recycle();
		return complete;
	}

	private static String statusOf(Document doc) throws NotesException {
		for (int i = 0; i < STATUS_ITEMS.length; i++) {
			String v = doc.getItemValueString(STATUS_ITEMS[i]);
			if (v != null && v.length() > 0) return v;
		}
		return "";
	}

	/** Removes the XML declaration and DOCTYPE so documents can share one root. */
	private static String stripProlog(String dxl) {
		String s = dxl.trim();
		boolean changed = true;
		while (changed) {
			changed = false;
			if (s.startsWith("<?xml")) {
				s = s.substring(s.indexOf("?>") + 2).trim();
				changed = true;
			} else if (s.startsWith("<!DOCTYPE")) {
				s = s.substring(s.indexOf('>') + 1).trim();
				changed = true;
			}
		}
		return s;
	}

	private static String xmlAttr(String s) {
		if (s == null) return "";
		return s.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")
			.replaceAll("'", "&apos;").replaceAll("\"", "&quot;");
	}

	private static String csvRow(String[] fields) {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < fields.length; i++) {
			if (i > 0) sb.append(',');
			String f = fields[i] == null ? "" : fields[i];
			if (f.indexOf(',') >= 0 || f.indexOf('"') >= 0 || f.indexOf('\n') >= 0 || f.indexOf('\r') >= 0) {
				f = "\"" + f.replaceAll("\"", "\"\"") + "\"";
			}
			sb.append(f);
		}
		sb.append("\r\n");
		return sb.toString();
	}

	private static void bump(Map<String, Integer> m, String key) {
		Integer n = m.get(key);
		m.put(key, Integer.valueOf(n == null ? 1 : n.intValue() + 1));
	}

	private static String describe(Throwable t) {
		if (t instanceof NotesException) {
			NotesException n = (NotesException) t;
			return "NotesException " + n.id + ": " + n.text;
		}
		return t.toString();
	}

	private void log(String s) throws Exception {
		System.out.println(s);
		log.write(new SimpleDateFormat("HH:mm:ss").format(new Date()) + " " + s + "\r\n");
		log.flush();
	}
}
