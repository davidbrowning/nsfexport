import lotus.domino.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/*
 * VCAIS export: exports every document in one or more Notes databases, in
 * two formats (each can be turned off below):
 *   - DXL, Notes' full XML format, including rich text and attachments.
 *   - "Legacy XML": one file per document, written with Document.generateXML,
 *     the same format and file names (document.N.xml) as the earlier script,
 *     so importers written for that output keep working.
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
 * Output in OUTPUT_DIR:
 *   vcais-export-<time>.log          progress, errors and the final count check
 *   <name>-<time>/                   one folder per database, holding:
 *     dxl/<name>.dxl                 all documents under one <vcaisexport> root
 *     xml/document.0.xml, .1.xml ... legacy XML, one file per document
 *     manifest.csv                   per document: note id, UNID, form, status,
 *                                    legacy file name, and the result per format
 *
 * Written for old Notes clients (Notes 6 and later): Java 1.3 only, so no
 * generics, no for-each loops and no regular expressions.
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
	// Folder for the output. Leave it "" to use a "vcais-export" folder on the
	// Desktop. It is created if missing. Use forward slashes, e.g. "D:/export".
	private static final String OUTPUT_DIR = "";
	// Fields tried, in order, for the case status column in the manifest.
	private static final String[] STATUS_ITEMS = { "Status", "CaseStatus", "Case_Status" };
	// Formats to write. At least one must be true.
	private static final boolean WRITE_DXL = true;
	private static final boolean WRITE_LEGACY_XML = true;
	// Write a progress line to the log every this many documents.
	private static final int PROGRESS_EVERY = 500;
	// ----------------------------------------------------------------------

	private BufferedWriter log;

	public void NotesMain() {
		File dir = outputFolder();
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

	/** Returns true when every document was exported in every chosen format. */
	private boolean exportDatabase(Session session, DbDirectory dbDir, String path, File dir, String stamp)
			throws Exception {
		log("== " + path);
		if (!WRITE_DXL && !WRITE_LEGACY_XML) {
			log("  nothing to do: WRITE_DXL and WRITE_LEGACY_XML are both false");
			return false;
		}
		Database db = dbDir.openDatabase(path);
		if (db == null || !db.isOpen()) {
			log("  could not open " + path);
			return false;
		}
		String name = new File(path.replace('\\', '/')).getName();
		// Some VCAIS files are named like "ClosedVCAIS.nsf.NSF": drop every .nsf.
		while (name.toLowerCase().endsWith(".nsf")) name = name.substring(0, name.length() - 4);
		File dbOut = new File(dir, name + "-" + stamp);
		File dxlDir = new File(dbOut, "dxl");
		File xmlDir = new File(dbOut, "xml");
		if (WRITE_DXL) dxlDir.mkdirs();
		if (WRITE_LEGACY_XML) xmlDir.mkdirs();
		dbOut.mkdirs();
		File dxlFile = new File(dxlDir, name + ".dxl");
		File csvFile = new File(dbOut, "manifest.csv");

		DocumentCollection dc = db.getAllDocuments();
		int expected = dc.getCount();
		log("  documents visible to this ID: " + expected);
		log("  output folder: " + dbOut.getAbsolutePath());

		DxlExporter exporter = null;
		if (WRITE_DXL) {
			exporter = session.createDxlExporter();
			exporter.setExitOnFirstFatalError(false);
		}

		BufferedWriter dxl = null;
		BufferedWriter csv = null;
		int dxlOk = 0, dxlFailed = 0, xmlOk = 0, xmlFailed = 0, seen = 0;
		Map byForm = new TreeMap();
		try {
			if (WRITE_DXL) {
				dxl = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(dxlFile), "UTF-8"));
				dxl.write("<?xml version='1.0' encoding='utf-8'?>\r\n");
				dxl.write("<vcaisexport database='" + xmlAttr(path) + "' replicaid='" + xmlAttr(db.getReplicaID())
					+ "' exported='" + stamp + "'>\r\n");
			}
			csv = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(csvFile), "UTF-8"));
			csv.write("noteid,unid,form,status,is_response,parent_unid,encrypted,legacy_xml_file,dxl_result,xml_result,error\r\n");

			Document doc = dc.getFirstDocument();
			while (doc != null) {
				String noteId = "", unid = "", form = "", status = "", parent = "", error = "";
				String dxlResult = WRITE_DXL ? "failed" : "skipped";
				String xmlResult = WRITE_LEGACY_XML ? "failed" : "skipped";
				String xmlName = WRITE_LEGACY_XML ? "document." + seen + ".xml" : "";
				boolean response = false, encrypted = false;
				seen++;
				try {
					noteId = doc.getNoteID();
					unid = doc.getUniversalID();
					form = doc.getItemValueString("Form");
					status = statusOf(doc);
					response = doc.isResponse();
					if (response) parent = doc.getParentDocumentUNID();
					encrypted = doc.isEncrypted();
					bump(byForm, form == null || form.length() == 0 ? "<no Form>" : form);
				} catch (Throwable t) {
					error = append(error, "read: " + describe(t));
				}
				if (WRITE_DXL) {
					try {
						dxl.write(stripProlog(exporter.exportDxl(doc)));
						dxl.write("\r\n");
						dxlResult = "ok";
						dxlOk++;
					} catch (Throwable t) {
						// Includes OutOfMemoryError on a single huge document.
						dxlFailed++;
						error = append(error, "dxl: " + describe(t));
					}
				}
				if (WRITE_LEGACY_XML) {
					// Same call and same writer type (platform encoding) as the
					// earlier script, so the files match what importers expect.
					BufferedWriter x = null;
					try {
						x = new BufferedWriter(new FileWriter(new File(xmlDir, xmlName)));
						doc.generateXML(x);
						x.close();
						x = null;
						xmlResult = "ok";
						xmlOk++;
					} catch (Throwable t) {
						xmlFailed++;
						error = append(error, "xml: " + describe(t));
					} finally {
						if (x != null) {
							try { x.close(); } catch (Throwable ignored) { }
						}
					}
					// Don't leave an empty or partial file for the importer to trip on.
					if (!"ok".equals(xmlResult)) new File(xmlDir, xmlName).delete();
				}
				if (error.length() > 0) log("  PROBLEM with note " + noteId + " (UNID " + unid + "): " + error);
				csv.write(csvRow(new String[] { noteId, unid, form, status, response ? "Y" : "N", parent,
					encrypted ? "Y" : "N", xmlName, dxlResult, xmlResult, error }));

				if (seen % PROGRESS_EVERY == 0) {
					log("  progress: " + seen + " of " + expected);
					if (dxl != null) dxl.flush();
					csv.flush();
				}
				Document next = dc.getNextDocument();
				doc.recycle();
				doc = next;
			}
			if (dxl != null) dxl.write("</vcaisexport>\r\n");
		} finally {
			if (dxl != null) {
				try { dxl.close(); } catch (Throwable t) { log("  ERROR closing DXL file: " + describe(t)); }
			}
			if (csv != null) {
				try { csv.close(); } catch (Throwable t) { log("  ERROR closing manifest: " + describe(t)); }
			}
		}

		if (exporter != null) {
			try {
				String exporterLog = exporter.getLog();
				if (exporterLog != null && exporterLog.trim().length() > 0) {
					log("  DXL exporter log: " + exporterLog.trim());
				}
			} catch (Throwable ignored) { }
		}

		log("  documents by Form:");
		for (Iterator it = byForm.entrySet().iterator(); it.hasNext();) {
			Map.Entry e = (Map.Entry) it.next();
			log("    " + e.getValue() + "\t" + e.getKey());
		}
		boolean complete = seen == expected;
		if (WRITE_DXL) {
			boolean ok = dxlFailed == 0 && dxlOk == expected;
			complete = complete && ok;
			log("  DXL " + (ok ? "COMPLETE" : "INCOMPLETE") + ": exported " + dxlOk + ", failed " + dxlFailed
				+ ", expected " + expected);
			log("  wrote " + dxlFile.getAbsolutePath() + " (" + dxlFile.length() + " bytes)");
		}
		if (WRITE_LEGACY_XML) {
			boolean ok = xmlFailed == 0 && xmlOk == expected;
			complete = complete && ok;
			log("  legacy XML " + (ok ? "COMPLETE" : "INCOMPLETE") + ": exported " + xmlOk + ", failed " + xmlFailed
				+ ", expected " + expected);
			log("  wrote " + xmlOk + " files to " + xmlDir.getAbsolutePath());
		}
		if (seen != expected) log("  WARNING: walked " + seen + " documents but expected " + expected);
		log("  wrote " + csvFile.getAbsolutePath());
		dc.recycle();
		if (exporter != null) exporter.recycle();
		db.recycle();
		return complete;
	}

	private static String append(String errors, String more) {
		return errors.length() == 0 ? more : errors + "; " + more;
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
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '&') sb.append("&amp;");
			else if (c == '<') sb.append("&lt;");
			else if (c == '>') sb.append("&gt;");
			else if (c == '\'') sb.append("&apos;");
			else if (c == '"') sb.append("&quot;");
			else sb.append(c);
		}
		return sb.toString();
	}

	private static String csvRow(String[] fields) {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < fields.length; i++) {
			if (i > 0) sb.append(',');
			String f = fields[i] == null ? "" : fields[i];
			if (f.indexOf(',') >= 0 || f.indexOf('"') >= 0 || f.indexOf('\n') >= 0 || f.indexOf('\r') >= 0) {
				StringBuffer q = new StringBuffer("\"");
				for (int j = 0; j < f.length(); j++) {
					char c = f.charAt(j);
					if (c == '"') q.append('"');
					q.append(c);
				}
				f = q.append('"').toString();
			}
			sb.append(f);
		}
		sb.append("\r\n");
		return sb.toString();
	}

	private static void bump(Map m, String key) {
		Integer n = (Integer) m.get(key);
		m.put(key, new Integer(n == null ? 1 : n.intValue() + 1));
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

	private void log(String s) throws Exception {
		System.out.println(s);
		log.write(new SimpleDateFormat("HH:mm:ss").format(new Date()) + " " + s + "\r\n");
		log.flush();
	}
}
