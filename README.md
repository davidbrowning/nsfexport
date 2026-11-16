Background: VCAIS is a very old, unmaintained case management software. As is the case sometimes with legacy software, much of it refuses to die. 

I am leaving these snippets in case it comes in handy for the next person who has to deal with the .nsf format or migrating away from VCAIS.

The only reliable way I have found to run programs against lotus is on a windows environment inside of the lotus notes program (notes.exe). The opensource community has not as of yet been able to fully document the file format. Though I will shout out [libnsfdb](https://github.com/libyal/libnsfdb) as having made what I perceive as the most progress with it.

Your user must have sufficient access in order to create and run what lotus calls "Agents".

If you can create a new agent in the notes application, you should be able to use these Java programs.

These agent will produce a ".dxl" dump which is at least plain text. 

## Census and export agents (recommended)

The two original agents above export a whole database in one go. That can fail on big databases: the full export is built in memory as one string, the output file is never closed, and errors only show in the Java Debug Console. The two agents below fix that.

1. **`VcaisCensus.java`** is read-only. It lists every database in the local Notes data folder with its document count and your access level. For databases whose name or title contains `vcais`, it also counts documents by Form and by status, counts response, encrypted and Readers-protected documents, and lists the field names on each Form. The report holds no case data, so you can share it while debugging.
2. **`VcaisExport.java`** exports each database listed in `DATABASES` one document at a time. It writes:
   - a `.dxl` file with all documents under one root element
   - a manifest CSV with one row per document (note ID, UNID, form, status, result)
   - a log that ends with `COMPLETE` or `INCOMPLETE`, comparing the exported count with the database's own count

   A document that fails is logged and skipped, and the export carries on.

To use either one: edit the settings at the top of the file. Run the census first to find the database file names and the status field. Then, in Designer, create a Java agent, paste the code in as class `JavaAgent`, set the trigger to "Action menu selection" and the target to "None", and run it from the Actions menu. Output goes to `C:\vcais-export` by default.

Both agents use Java 6 syntax only, so they run on old Notes clients.

**Readers fields:** Notes hides documents whose Readers field leaves out your ID, and no agent can count what it can't see. If the census reports Readers fields, run it again under an ID with full access and compare the counts.
