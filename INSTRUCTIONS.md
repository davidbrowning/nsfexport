# Exporting VCAIS data: step-by-step

You will copy two files in, change one line, and click through some menus.

**VCAIS** is the Virginia prosecutors' case management system. It runs on Lotus Notes (now HCL Notes). Its data lives in `.nsf` database files on the computer that runs Notes. This guide gets that data out of those files and into plain-text files that a new system can import.

**About 1–2 hours** for setup and the census. The export itself can take hours on a big database, but you can leave it running.

---

## What you will do

1. Get the two program files onto the Notes computer.
2. Back up the VCAIS database files.
3. Make a separate, empty "tools" database to hold the programs. **You do not change VCAIS itself.**
4. Add and run the **census**. It counts documents and writes a short report with no case data.
5. Read the census to find the right database file names.
6. Add and run the **export**.
7. Check the export log. It says `COMPLETE` or `INCOMPLETE`.

The programs only *read* VCAIS. They never change or delete case data.

---

## Before you start

You need:

- **The Windows computer that has Notes and the VCAIS databases on it.**
- **Domino Designer** on that computer. This is the programming tool that comes with Notes.
  To check, look in the Start menu for **Domino Designer** (also called *IBM Domino Designer* or *Lotus Domino Designer*).
  If it is missing, install it from the same Notes installer the county used. Choose the option that includes Designer.
- **Your Notes ID and password**, ideally an ID with full (Manager) access to the VCAIS databases. See [Readers fields](#if-the-census-mentions-readers-fields).
- **A USB stick**, or another approved way to move two small text files onto the computer.
- **About 2× the size of the VCAIS database files in free disk space on `C:`.** The export files are larger than the database.

To check your Notes version: open Notes and choose **Help → About Notes**. This guide uses the menus from Notes and Designer **8.5 and later**. Older versions have the same steps, but some menu names differ.

---

## Step 1: Get the program files

On any computer with internet access:

1. Open each link below.
2. Right-click the page and choose **Save as…**. Keep the file name exactly as shown.
   - Census: <https://raw.githubusercontent.com/davidbrowning/nsfexport/main/VcaisCensus.java>
   - Export: <https://raw.githubusercontent.com/davidbrowning/nsfexport/main/VcaisExport.java>
3. Copy both files to the USB stick, then to the Notes computer's desktop.

They are plain text. You can open them in Notepad to look at them.

---

## Step 2: Back up the VCAIS databases

Do this even though the programs only read data.

1. **Close Notes and Designer completely.**
2. Find the Notes data folder. It is usually one of these:
   - `C:\Program Files (x86)\IBM\Notes\Data`
   - `C:\Program Files\IBM\Lotus\Notes\Data`
   - `C:\Users\<your name>\AppData\Local\IBM\Notes\Data`
   - `C:\Notes\Data`

   The VCAIS files may also sit in a separate folder outside the Notes data folder, such as `C:\Users\<name>\Desktop\Domino\data\VCAIS`. Note where they are; you'll need the folder in Step 4.
3. Find the VCAIS `.nsf` files. They may be in a subfolder. Copy them all to a safe place, such as a folder on another drive named `VCAIS backup <today's date>`.

---

## Step 3: Make a "tools" database

The programs go in their own empty database. VCAIS stays untouched, and the VCAIS screens never open, so its startup scripts don't run.

1. Start **Domino Designer**. Enter your Notes password if asked.
2. Choose **File → New → Application**. (In some versions: **File → Application → New**.)
3. Fill in:
   - **Server:** `Local`
   - **Title:** `VCAIS Export Tools`
   - **File name:** `vcais-tools.nsf`
   - **Template:** `-Blank-`
4. Click **OK**.

`VCAIS Export Tools` now shows in the **Applications** list on the left side of Designer.

---

## Step 4: Add the census program

1. In the Applications list, click the small arrow beside **VCAIS Export Tools** to open it.
2. Open **Code**, then click **Agents**.
3. Click **New Agent** (the button at the top of the agent list).
4. In the box that opens:
   - **Name:** `VCAIS Census`
   - **Type:** `Java` ← important. The default is *Simple action*.
   - Click **OK**.
5. An editor opens with some starting code in it. Click inside the code, press **Ctrl+A** to select it all, then press **Delete**. The editor is now empty.
6. On the desktop, open `VcaisCensus.java` in **Notepad**. Press **Ctrl+A**, then **Ctrl+C**.
7. Click back in the empty Designer editor and press **Ctrl+V**.
8. Set when the agent runs. Look for the **Properties** panel, usually below the editor. If you can't see it, choose **Window → Show Eclipse Views → Properties**. Then, on its **Basics** tab, set:
   - **Trigger:** `On event` and `Action menu selection`
   - **Target:** `None`
9. On the **Security** tab of the same Properties panel (in some versions it's the tab with a key icon), set **Set runtime security level** to **2. Allow restricted operations**. The program needs this to save its report file.
10. Press **Ctrl+S** to save.

**How to tell it worked:** saving shows no red marks, and the **Problems** panel (next to Properties) lists no errors. If there are errors, the paste was usually incomplete. Repeat steps 5–7 and save again.

> The code starts with a few lines of settings in capital letters. Change at most one of them here:
>
> **If the VCAIS files are outside the Notes data folder** (see Step 2), add their folder to `EXTRA_FOLDERS`. **Use forward slashes `/`**, not backslashes. For example, change
>
> ```java
> private static final String[] EXTRA_FOLDERS = { };
> ```
>
> to
>
> ```java
> private static final String[] EXTRA_FOLDERS = { "C:/Users/user/Desktop/Domino/data" };
> ```
>
> Leave everything else as it is.

---

## Step 5: Run the census

1. Open the **Notes** client, not Designer.
2. Choose **File → Open → Lotus Notes Application** (or **File → Application → Open**). Choose **Local** and open **VCAIS Export Tools** (`vcais-tools.nsf`). The database is empty, which is normal.
3. Choose **Actions → VCAIS Census**.
4. If an **Execution Security Alert** appears, choose **Execute once**. Notes asks this because the program is new and signed with your ID.
5. Wait. A small database takes seconds. A large one can take several minutes. Notes may look frozen. **Don't click anything** until it responds again.

   If the VCAIS databases don't appear in the report, they are outside the Notes data folder. Set `EXTRA_FOLDERS` (Step 4) and run the census again.
6. Open `C:\vcais-export` in File Explorer. You should see a file like `vcais-census-20261201-101500.txt`. Open it in Notepad.

**If no file appears:** see [Troubleshooting](#troubleshooting).

---

## Step 6: Read the census

The report lists every database Notes can see. Look for the VCAIS ones. Each section looks like this (numbers made up):

```
== C:/Users/user/Desktop/Domino/data/VCAIS/ClosedVCAIS.nsf
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
```

Write down:

1. **The file path after `==`** for each VCAIS database you need, such as `C:/Users/user/Desktop/Domino/data/VCAIS/ClosedVCAIS.nsf`. It may be a full path like this, or a shorter path such as `VCAIS\ClosedVCAIS.nsf`. You'll type it in Step 7.
2. **`documents visible to you`.** The export should end with this same number.
3. **Whether `documents with a Readers field` is above 0.** If it is, read [the Readers section](#if-the-census-mentions-readers-fields) before you export.

The census holds counts and field names, not case contents. It is safe to send to your consultant.

---

## Step 7: Add the export program

Do Step 4 again with these changes:

- **Name:** `VCAIS Export`
- Paste from **`VcaisExport.java`** instead.

Before saving, change **one line**. Near the top, find:

```java
private static final String[] DATABASES = { "ClosedVCAIS.nsf" };
```

Change the name in quotes to the path from the census. **Use forward slashes `/`.** If the census shows backslashes `\`, change each one to `/`. For example:

```java
private static final String[] DATABASES = { "C:/Users/user/Desktop/Domino/data/VCAIS/ClosedVCAIS.nsf" };
```

To export more than one database, separate them with commas:

```java
private static final String[] DATABASES = { "C:/Users/user/Desktop/Domino/data/VCAIS/ClosedVCAIS.nsf", "C:/Users/user/Desktop/Domino/data/VCAIS/Archive2015.nsf" };
```

Keep the quotes, the braces `{ }` and the semicolon `;` exactly as shown. Then set the trigger, target and security level as in Step 4 and save with **Ctrl+S**. Check that there are no errors.

---

## Step 8: Run the export

1. In **Notes**, with **VCAIS Export Tools** open, choose **Actions → VCAIS Export**.
2. Choose **Execute once** if asked.
3. Wait. **A big database can take hours.** Notes will look frozen the whole time. Leave it alone. Don't let the computer sleep. In Windows power settings, set sleep to **Never** for the day.
4. To check progress, open the newest `vcais-export-*.log` in `C:\vcais-export` with Notepad. It writes a progress line every 500 documents. Close and reopen it to see new lines.

When it finishes, `C:\vcais-export` holds, for each database:

| File | What it is | Share it? |
|---|---|---|
| `<name>-<time>.dxl` | **The exported case data** (XML text) | **No.** This is the data. Handle it under your data rules. |
| `<name>-<time>-manifest.csv` | One row per document: ID, form, status, ok or failed | Keep it local |
| `vcais-export-<time>.log` | Progress, errors and the final count check | Yes, safe to send |

---

## Step 9: Check the result

Open the log and go to the bottom. For each database, look for one of these lines:

- `COMPLETE: exported 48211, failed 0, ...` **Done.** The number should match `documents visible to you` from the census.
- `INCOMPLETE: exported 48190, failed 21, ...` Some documents failed. The lines starting with `FAILED` name each one. Send the log to your consultant.

The last line of the log is `ALL COMPLETE` or `SOME EXPORTS INCOMPLETE`.

Running the export again is safe. Each run writes new files with a new time in the name and leaves the old ones alone.

---

## If the census mentions Readers fields

VCAIS can restrict some documents to certain people. Those documents are **invisible** to anyone not on the list, including these programs, and no program can count what it can't see. If `documents with a Readers field` is above 0:

1. Run the census with an ID that has full access. Usually that's an administrator ID with **Full Access Administration**, or the ID used to set up VCAIS.
2. Compare `documents visible to you` across the two IDs. If the numbers differ, run the export with the ID that sees more.

---

## Troubleshooting

| What you see | What to do |
|---|---|
| No **VCAIS Census** under **Actions** | Make sure the **VCAIS Export Tools** database is open, not another one. In Designer, check the agent's trigger is **Action menu selection** and that you saved it. |
| Red marks or errors when saving | The paste was incomplete or the settings line was mistyped. Paste the whole file again. If you edited `DATABASES`, check the quotes, the forward slashes, `{ }` and `;`. |
| Nothing happens, no `C:\vcais-export` folder | In Notes, choose **Tools → Show Java Debug Console** (or **File → Tools → Show Java Debug Console**) and run the agent again. Send a photo or a copy of the console text. Also check the runtime security level is **2** (Step 4, item 9). |
| `could not open` in the census or log | Your ID can't open that database, or the path is wrong. Check the spelling, and that `DATABASES` uses forward slashes `/`. |
| `Execution Security Alert` every time | Normal. Choose **Execute once**, or **Start trusting the signer** if your policy allows. |
| Notes says it's not responding | Normal during a long export. Wait. Only restart if the log hasn't added a line in 30 minutes. |
| The disk filled up | Free space or set `OUTPUT_DIR` to a bigger drive, for example `"D:/vcais-export"`, then run again. |

---

## What to send your consultant

- The census report: `vcais-census-*.txt`
- The export log: `vcais-export-*.log`
- A screenshot of any error message

**Do not send** the `.dxl` files or the manifest unless your data-handling rules allow it.
