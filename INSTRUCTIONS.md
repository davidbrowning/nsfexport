# Exporting VCAIS data: step-by-step

You will copy two files in, change a line or two, and click through some menus.

**VCAIS** is the Virginia prosecutors' case management system. It runs on Lotus Notes (now HCL Notes). Its data lives in `.nsf` database files. This guide gets that data out into files that a new system can import.

**About 1–2 hours** for setup and the census. The export itself can take hours on a big database, but you can leave it running.

---

## What you will do

1. Get a fresh copy of the VCAIS databases onto the export computer.
2. Make a separate "tools" database to hold the programs. It stays put when you replace the VCAIS copy.
3. Add and run the **census**. It counts documents and writes a short report with no case data.
4. Read the census.
5. Add and run the **export**.
6. Check the export log. It says `COMPLETE` or `INCOMPLETE`.
7. Package the output and send it on.

The programs only *read* VCAIS. They never change or delete case data.

**You don't need to open a case first.** Earlier export steps had you open a case as a "home base" before running the agent. These programs find the databases by their file path, so you can skip that.

---

## Before you start

You need:

- **The computer with Notes and Domino Designer installed.** Designer is the programming tool that comes with Notes. It's in the Start menu as **Domino Designer** (or *Lotus Domino Designer*).
- **Your Notes ID and password.** Ideally the ID has full (Manager) access to the VCAIS databases. See [Readers fields](#if-the-census-mentions-readers-fields).
- **About 3× the size of the VCAIS database files in free disk space.** The export writes two copies of the data, and each is larger than the database.

**Which Notes version?** In Notes, choose **Help → About Notes**. Menu names changed between versions. This guide shows the menus for **Notes 6 and 7** first, and the **Notes 8.5 and later** menus in brackets, like this: **File → Database → Open** *(8.5+: File → Application → Open)*.

---

## Step 1: Get a fresh copy of the VCAIS databases

Use your usual procedure to copy the `Domino` folder from the VCAIS server to the export computer's Desktop:

1. Rename the old `Domino` folder on the Desktop to `Domino.backup`. That's your backup.
2. Copy the whole `Domino` folder from the server. The VCAIS databases sometimes depend on each other, so copy the entire folder, not just the files you think you need.
3. Disconnect the export computer from the network before you go on.

The VCAIS files are then in a folder like `C:\Users\user\Desktop\Domino\data\VCAIS`. Some file names have a doubled extension, such as `ClosedVCAIS.nsf.NSF`. That's normal for these files. Leave the names as they are.

---

## Step 2: Make a "tools" database

The programs go in their own small database, separate from the VCAIS copy. Because it lives in the Notes data folder, not in the `Domino` folder, it survives when you replace the VCAIS copy next time. **Do this once.**

1. In **Notes**, choose **File → Database → New** *(8.5+: File → Application → New)*.
2. Fill in:
   - **Server:** `Local`
   - **Title:** `VCAIS Export Tools`
   - **File name:** `vcais-tools.nsf`
   - **Template:** `-Blank-`
3. Click **OK**. An empty database opens. That's expected.

---

## Step 3: Get the program files

On any computer with internet access:

1. Open each link below. Right-click the page and choose **Save as…**, keeping the file name exactly as shown.
   - Census: <https://raw.githubusercontent.com/davidbrowning/nsfexport/main/VcaisCensus.java>
   - Export: <https://raw.githubusercontent.com/davidbrowning/nsfexport/main/VcaisExport.java>
2. Move both files to the export computer's Desktop, for example with a USB stick.

They are plain text, so you can open them in Notepad.

---

## Step 4: Add the census program

1. Start **Domino Designer**.
2. Choose **File → Database → Open** *(8.5+: File → Application → Open)*. Choose **Local** and open **VCAIS Export Tools**.
3. In the list on the left, open **Shared Code** *(8.5+: **Code**)* and click **Agents**.
4. Click **New Agent** at the top of the agent list.
5. Set the agent's properties. In Notes 6/7 a properties box opens. In 8.5+, a dialog opens first, and the rest is in the **Properties** panel below the editor.
   - **Name:** `VCAIS Census`
   - **Trigger:** `On event`, then `Action menu selection`
   - **Target:** `None`
   - On the **Security** tab (in 6/7, the tab with the key icon): **Set runtime security level** to **2. Allow restricted operations**. The program needs this to save its report.
   - *(8.5+ only)* **Type:** `Java`
6. *(Notes 6/7)* Close the properties box. Above the code area, find the drop-down that says **Simple action(s)** and change it to **Java**.
7. Starting code appears in the code area. Click inside it, press **Ctrl+A** to select it all, then press **Delete**.
8. Open `VcaisCensus.java` in **Notepad**. Press **Ctrl+A**, then **Ctrl+C**. Click in the empty code area in Designer and press **Ctrl+V**.
9. Change one setting. Near the top of the code, find:

   ```java
   private static final String[] EXTRA_FOLDERS = { };
   ```

   and change it to the folder from Step 1. **Use forward slashes `/`**, not backslashes:

   ```java
   private static final String[] EXTRA_FOLDERS = { "C:/Users/user/Desktop/Domino/data" };
   ```

   Replace `user` with the Windows user name in your path. Leave everything else as it is.
10. Press **Ctrl+S** to save.

**How to tell it worked:** saving shows no error message. In 6/7, errors appear at the bottom of the code area; in 8.5+, they appear in the **Problems** panel. If there is an error, the paste was usually incomplete, or the line in item 9 has a typo. Check the quotes, the braces `{ }` and the semicolon `;`. Then repeat items 7–10.

---

## Step 5: Run the census

1. In **Notes**, open **VCAIS Export Tools**: **File → Database → Open** *(8.5+: File → Application → Open)*, choose **Local**, pick it.
2. Choose **Actions → VCAIS Census**.
3. If a security alert appears, choose **Execute the action this one time** *(8.5+: Execute once)*. Notes asks because the program is new and signed with your ID.
4. Wait. Notes may look frozen for a few minutes. **Don't click anything** until it responds again.
5. On the Desktop, open the new **`vcais-export`** folder. Open the file named like `vcais-census-20261201-101500.txt` in Notepad.

---

## Step 6: Read the census

The report lists every database it found. Look for the VCAIS ones. Each looks like this (numbers made up):

```
== C:/Users/user/Desktop/Domino/data/VCAIS/ClosedVCAIS.nsf.NSF
  title: VCAIS Closed Cases
  size: 812 MB
  your access: Manager
  documents visible to you: 48211
  response documents: 9120
  documents with a Readers field: 0
  by Form:
    31002	Case
    9120	Note
    8089	Defendant
  by status (field: Status):
    ...
  fields by Form:
    Case: CaseNumber, Charge, ...
    Defendant: ...
```

Check four things:

1. **The path after `==`** for each VCAIS database. You'll copy it in Step 7.
2. **`documents visible to you`.** The export should end with this same number.
3. **Every kind of record you need is there.** For example, if you need defendant information, look for a Form like `Defendant` under **by Form**, or defendant fields under **fields by Form**. If something is missing, tell your consultant before you export: that data may be kept in another database.
4. **`documents with a Readers field`.** If this is above 0, read [the Readers section](#if-the-census-mentions-readers-fields) first.

If the VCAIS databases aren't in the report at all, check the `EXTRA_FOLDERS` line from Step 4.

The census holds counts and field names, not case contents, so it's safe to send to your consultant.

---

## Step 7: Add the export program

Do Step 4 again, with these changes:

- **Name:** `VCAIS Export`
- Paste from **`VcaisExport.java`**.
- Instead of `EXTRA_FOLDERS`, change the `DATABASES` line. Find:

  ```java
  private static final String[] DATABASES = { "ClosedVCAIS.nsf" };
  ```

  Put in the path or paths from the census, with **forward slashes `/`**. Copy the file name exactly, including a doubled extension like `.nsf.NSF`. To export several databases, separate them with commas:

  ```java
  private static final String[] DATABASES = { "C:/Users/user/Desktop/Domino/data/VCAIS/ClosedVCAIS.nsf.NSF", "C:/Users/user/Desktop/Domino/data/VCAIS/OpenVCAIS.nsf" };
  ```

Set the trigger, target and security level the same way, save with **Ctrl+S**, and check there are no errors.

---

## Step 8: Run the export

1. In **Notes**, with **VCAIS Export Tools** open, choose **Actions → VCAIS Export**.
2. Answer the security alert as in Step 5.
3. Wait. **A big database can take hours.** Notes will look frozen the whole time. Leave it alone. In Windows power settings, set sleep to **Never** for the day.
4. To check progress, open the newest `vcais-export-*.log` in the Desktop `vcais-export` folder with Notepad. It adds a line every 500 documents. Close and reopen it to see new lines.

When it finishes, the `vcais-export` folder holds the log, plus one folder per database named like `ClosedVCAIS-20261201-103000`:

| File | What it is | Share it? |
|---|---|---|
| `vcais-export-<time>.log` | Progress, errors and the final count check | Yes, safe to send |
| `<name>-<time>\xml\document.0.xml`, `document.1.xml`, … | **Case data**, one file per document, in the same format as the earlier export. This is what the new system's importer reads today. | **No.** Handle it under your data rules. |
| `<name>-<time>\dxl\<name>.dxl` | **Case data**, every document in one file, in Notes' full format (DXL). It includes rich text and attachments, which the older format may leave out. | **No.** Same as above. |
| `<name>-<time>\manifest.csv` | One row per document: IDs, form, status, which `document.N.xml` it is, and ok or failed for each format | Keep it with the data |

There's no need to move files out of the Notes program folder as in the earlier steps. Everything is written to the Desktop.

---

## Step 9: Check the result

Open the log and go to the bottom. For each database there is one line per format:

- `DXL COMPLETE: exported 48211, failed 0, ...` and `legacy XML COMPLETE: exported 48211, failed 0, ...` **Done.** The number should match `documents visible to you` from the census.
- `... INCOMPLETE: exported 48190, failed 21, ...` Some documents failed in that format. The lines starting with `PROBLEM` name each one. Send the log to your consultant.

The last line of the log is `ALL COMPLETE` or `SOME EXPORTS INCOMPLETE`.

Running the export again is safe. Each run writes a new folder with a new time in its name and leaves the old ones alone.

---

## Step 10: Package and send the output

1. Open the database's output folder, for example `ClosedVCAIS-20261201-103000`.
2. Right-click it and choose **Send to → Compressed (zipped) folder**. Zip the whole folder so the `xml`, `dxl` and `manifest.csv` stay together.
3. Move the zip file on through your usual secure route.

The numbers in `document.N.xml` only show the order of this run. They won't match file numbers from an earlier export. Use `manifest.csv` to match each file to the document's permanent ID (its UNID).

---

## If the census mentions Readers fields

VCAIS can restrict some documents to certain people. Anyone not on the list can't see those documents, and that includes these programs. If `documents with a Readers field` is above 0:

1. Run the census again with an ID that has full access, such as an administrator ID or the ID used to set up VCAIS.
2. Compare `documents visible to you` between the two IDs. If the numbers differ, run the export with the ID that sees more.

---

## Troubleshooting

| What you see | What to do |
|---|---|
| The agent isn't in the **Actions** menu | Make sure **VCAIS Export Tools** is the open database. In some versions, agents are listed under **Actions → Other…**. In Designer, check the trigger is **Action menu selection**, and save again. |
| An error when saving | Paste the whole file again. If you edited a settings line, check the quotes, the forward slashes, `{ }` and `;`. |
| No `vcais-export` folder on the Desktop | In Notes, choose **File → Tools → Show Java Debug Console**, run the agent again, and send a photo of the console. Also check the runtime security level is **2** (Step 4). |
| `could not open` in the census or log | The path is wrong, or your ID can't open that database. Copy the path from the census exactly, with forward slashes. |
| A security alert every time | Normal. Choose to run it this one time, or trust the signer if your policy allows. |
| Notes says it's not responding | Normal during a long export. Wait. Only restart if the log hasn't added a line in 30 minutes. |
| The disk filled up | Free some space, or set `OUTPUT_DIR` to a bigger drive, for example `"D:/vcais-export"`, then run again. |

---

## What to send your consultant

- The census report: `vcais-census-*.txt`
- The export log: `vcais-export-*.log`
- A photo or screenshot of any error message

**Do not send** the `xml` or `dxl` folders, or the manifest, unless your data rules allow it.
