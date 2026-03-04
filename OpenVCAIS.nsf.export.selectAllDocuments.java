import lotus.domino.*;

public class JavaAgent extends AgentBase {

	public void NotesMain() {

		try {
			Session session = getSession();
			AgentContext agentContext = session.getAgentContext();
               String serverName = null;
               DbDirectory dbDir = session.getDbDirectory(serverName);
               Database db = dbDir.openDatabase("OpenVCAIS.nsf");
               DocumentCollection dc = db.getAllDocuments();
               // dc.selectAllDocuments(true); not part of the DocumentCollection methods
               // dc.buildCollection(); also not part of the DocumentCollection methods
               String filename = "OpenVCAIS.nsf.export.selectAllDocuments.dxl";
               Stream stream = session.createStream();
               if (stream.open(filename)) {
               	stream.truncate();
               	DxlExporter exporter = session.createDxlExporter();
               	System.out.println("Exported " + stream.writeText(exporter.exportDxl(dc)) + " bytes to OpenVCAIS.nsf.export.dxl");
               }
               else {
               	System.out.println("Can't open file: " + filename);
               }

		} catch(Exception e) {
			e.printStackTrace();
		}
	}
}

