//
// $Id$
//

package org.opennms.install;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.StringReader;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.opennms.test.ThrowableAnticipator;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

public class InstallerDBTest extends TestCase {
    private static final String s_constraint = "fk_nodeid6";

    private static final String s_runProperty = "mock.rundbtests";

    private String m_testDatabase;

    private boolean m_leaveDatabase = false;

    private Installer m_installer;

    protected void setUp() throws Exception {
        if (!isDBTestEnabled()) {
            return;
        }

        m_testDatabase = "opennms_test_" + System.currentTimeMillis();

        m_installer = new Installer();
        // Create a ByteArrayOutputSteam to effectively throw away output.
        m_installer.m_out = new PrintStream(new ByteArrayOutputStream());
        m_installer.m_database = m_testDatabase;
        m_installer.m_pg_driver = "org.postgresql.Driver";
        m_installer.m_pg_url = "jdbc:postgresql://localhost:5432/";
        m_installer.m_pg_user = "postgres";
        m_installer.m_pg_pass = "";
        m_installer.m_user = "opennms";
        
        // XXX this makes bad assumptions that are not always true with Maven
        // 2.
        //m_installer.m_create_sql = "../opennms-daemon/src/main/filtered/etc/create.sql";
        
        URL sql = getClass().getResource("/create.sql");
        assertNotNull("Could not find create.sql", sql);
        m_installer.m_create_sql = sql.getFile();
        
        m_installer.m_fix_constraint = true;
        m_installer.m_fix_constraint_name = s_constraint;

        m_installer.m_debug = false;

        // Create test database.
        m_installer.databaseConnect("template1");
        m_installer.databaseAddDB();
        m_installer.databaseDisconnect();

        // Connect to test database.
        m_installer.databaseConnect(m_testDatabase);

        // Read in the table definitions
        m_installer.readTables();
    }

    public void tearDown() throws Exception {
        if (!isDBTestEnabled()) {
            return;
        }

        m_installer.databaseDisconnect();

        /*
         * Sleep after disconnecting from the database because PostgreSQL
         * doesn't seem to notice immediately that we have disconnected. Yeah,
         * it's a hack.
         */
        Thread.sleep(100);

        m_installer.databaseConnect("template1");
        destroyDatabase();
        m_installer.databaseDisconnect();

        // Sleep again. Man, I hate this.
        Thread.sleep(100);
    }

    public boolean isDBTestEnabled() {
        String property = System.getProperty(s_runProperty);
        return "true".equals(property);
    }

    public void destroyDatabase() throws SQLException {
        if (m_leaveDatabase) {
            System.err.println("Not dropping database '" + m_testDatabase
                    + "'");
        } else {
            Statement st = m_installer.m_dbconnection.createStatement();
            st.execute("DROP DATABASE " + m_testDatabase);
            st.close();
        }
    }

    // XXX this should be an integration test
    public void testParseSQLTables() throws Exception {
        Iterator i = m_installer.m_tables.iterator();
        while (i.hasNext()) {
            String table = ((String) i.next()).toLowerCase();
            m_installer.getTableFromSQL(table);
        }
    }

    // XXX this should be an integration test
    public void testCreateTables() throws Exception {
        if (!isDBTestEnabled()) {
            return;
        }

        m_installer.createTables();
    }

    // XXX this should be an integration test
    public void testCreateTablesTwice() throws Exception {
        if (!isDBTestEnabled()) {
            return;
        }

        m_installer.createTables();

        // Create a new ByteArrayOutputStream so we can look for UPTODATE for
        // every table
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        m_installer.m_out = new PrintStream(out);
        m_installer.createTables();

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.matches("- creating tables\\.\\.\\.")) {
                continue;
            }
            if (line.matches("  - checking table \"\\S+\"\\.\\.\\. UPTODATE")) {
                continue;
            }
            if (line.matches("- creating tables\\.\\.\\. DONE")) {
                continue;
            }
            fail("Unexpected line output by createTables(): \"" + line + "\"");
        }
    }

    /**
     * Call Installer.checkOldTables, which should *not* throw an exception
     * because we have not created a table matching "_old_".
     */
    public void testBug1006NoOldTables() {
        if (!isDBTestEnabled()) {
            return;
        }

        ThrowableAnticipator ta = new ThrowableAnticipator();

        try {
            m_installer.checkOldTables();
        } catch (Throwable t) {
            ta.throwableReceived(t);
        }

        ta.verifyAnticipated();
    }

    /**
     * Call Installer.checkOldTables, which *should* throw an exception
     * because we have created a table matching "_old_". We check the
     * exception message to ensure that it is the exception we are expecting,
     * and fail otherwise.
     */
    public void testBug1006HasOldTables() throws SQLException {
        if (!isDBTestEnabled()) {
            return;
        }

        // final String errorSubstring = "One or more backup tables from a
        // previous install still exists";

        String table = "testBug1006_old_" + System.currentTimeMillis();

        Statement st = m_installer.m_dbconnection.createStatement();
        st.execute("CREATE TABLE " + table + " ( foo integer )");
        st.close();

        ThrowableAnticipator ta = new ThrowableAnticipator();
        LinkedList<String> l = new LinkedList<String>();
        l.add(table);
        ta.anticipate(new BackupTablesFoundException(l));

        try {
            m_installer.checkOldTables();
        } catch (Throwable t) {
            ta.throwableReceived(t);
        }

        ta.verifyAnticipated();
    }

    public void executeSQL(String[] commands) {
        if (!isDBTestEnabled()) {
            return;
        }

        Statement st = null;;
        try {
            st = m_installer.m_dbconnection.createStatement();
        } catch (SQLException e) {
            fail("Could not create statement", e);
        }
        
        for (String command : commands) {
            try {
                st.execute(command);
            } catch (SQLException e) {
                fail("Could not execute statement: '" + command + "'", e);
            }
        }
        try {
            st.close();
        } catch (SQLException e) {
            fail("Could not close database connection", e);
        }
    }
    
    public void fail(String message, Throwable t) throws AssertionFailedError {
        AssertionFailedError e = new AssertionFailedError(message + ": "
                                                          + t.getMessage());
        e.initCause(t);
        throw e;
    }

    public void executeSQL(String command) {
        executeSQL(new String[] { command });
    }

    public void setupBug931(boolean breakConstraint, boolean dropForeignTable)
            throws SQLException {
        final String[] commands = { "CREATE TABLE events ( nodeID integer )",
                "CREATE TABLE node ( nodeID integer )",
                "INSERT INTO events ( nodeID ) VALUES ( 1 )",
                "INSERT INTO node ( nodeID ) VALUES ( 1 )",
                "INSERT INTO events ( nodeID ) VALUES ( 2 )",
                "INSERT INTO node ( nodeID ) VALUES ( 2 )" };

        executeSQL(commands);

        if (breakConstraint) {
            executeSQL("DELETE FROM node where nodeID = 2");
        }

        if (dropForeignTable) {
            executeSQL("DROP TABLE node");
            if (!breakConstraint) {
                executeSQL("UPDATE events SET nodeID = NULL WHERE nodeID IS NOT NULL");
            }
        }
    }

    public void testBug931ConstraintsOkayTwoTables() throws Exception {
        if (!isDBTestEnabled()) {
            return;
        }

        doTestBug931(false, 0, false);
    }

    public void testBug931ConstraintsOkayOneTable() throws Exception {
        if (!isDBTestEnabled()) {
            return;
        }

        doTestBug931(true, 0, false);
    }

    public void testBug931ConstraintsBadTwoTables() throws Exception {
        if (!isDBTestEnabled()) {
            return;
        }

        doTestBug931(false, 1, false);
    }

    public void testBug931ConstraintsBadOneTable() throws Exception {
        if (!isDBTestEnabled()) {
            return;
        }

        doTestBug931(true, 2, false);
    }

    public void testConstraintsFixedNullTwoTables() throws Exception {
        if (!isDBTestEnabled()) {
            return;
        }

        doTestBug931(false, 0, true);
    }

    public void testConstraintsFixedNullOneTable() throws Exception {
        if (!isDBTestEnabled()) {
            return;
        }

        doTestBug931(true, 0, true);
    }

    public void testConstraintsFixedDelTwoTables() throws Exception {
        if (!isDBTestEnabled()) {
            return;
        }

        m_installer.m_fix_constraint_remove_rows = true;
        doTestBug931(false, 0, true);
    }

    public void testConstraintsFixedDelOneTable() throws Exception {
        if (!isDBTestEnabled()) {
            return;
        }

        m_installer.m_fix_constraint_remove_rows = true;
        doTestBug931(true, 0, true);
    }

    public void testBogusConstraintName() throws Exception {
        if (!isDBTestEnabled()) {
            return;
        }

        String constraint = "bogus_test_" + System.currentTimeMillis();
        doTestBogusConstraint(constraint, "Did not find constraint "
                + constraint + " in the database.");
    }

    public void testBogusConstraintTable() throws Exception {
        if (!isDBTestEnabled()) {
            return;
        }

        String constraint = "fk_nodeid1";
        doTestBogusConstraint(
                              constraint,
                              "Constraint "
                                      + constraint
                                      + " is on table "
                                      + "ipinterface, but table does not exist (so fixing this constraint does nothing).");
    }

    public void testBogusConstraintColumn() throws Exception {
        if (!isDBTestEnabled()) {
            return;
        }

        String constraint = "fk_dpname";
        doTestBogusConstraint(constraint, "Constraint " + constraint
                + " is on column "
                + "dpname of table node, but column does not "
                + "exist (so fixing this constraint does nothing).");
    }

    public void testConstraintAfterConstrainedColumn() throws Exception {
        if (!isDBTestEnabled()) {
            return;
        }

        String s_create_sql = "            create table distPoller (\n"
                + "                    dpName            varchar(12),\n"
                + "                                constraint pk_dpName primary key (dpName),\n"
                + "                    dpIP            varchar(16) not null,\n"
                + "                    dpComment        varchar(256),\n"
                + "                    dpDiscLimit        numeric(5,2),\n"
                + "                    dpLastNodePull        timestamp without time zone,\n"
                + "                    dpLastEventPull        timestamp without time zone,\n"
                + "                    dpLastPackagePush    timestamp without time zone,\n"
                + "                    dpAdminState         integer,\n"
                + "                    dpRunState        integer );\n";

        m_installer.readTables(new StringReader(s_create_sql));
        m_installer.getTableColumnsFromSQL("distpoller");
    }

    public void testConstraintAtEndOfTable() throws Exception {
        if (!isDBTestEnabled()) {
            return;
        }

        String s_create_sql = "            create table distPoller (\n"
                + "                    dpName            varchar(12),\n"
                + "                    dpIP            varchar(16) not null,\n"
                + "                    dpComment        varchar(256),\n"
                + "                    dpDiscLimit        numeric(5,2),\n"
                + "                    dpLastNodePull        timestamp without time zone,\n"
                + "                    dpLastEventPull        timestamp without time zone,\n"
                + "                    dpLastPackagePush    timestamp without time zone,\n"
                + "                    dpAdminState         integer,\n"
                + "                    dpRunState        integer,\n"
                + "                                constraint pk_dpName primary key (dpName) );\n";

        m_installer.readTables(new StringReader(s_create_sql));
        m_installer.getTableColumnsFromSQL("distpoller");
    }

    public void testConstraintOnBogusColumn() throws Exception {
        if (!isDBTestEnabled()) {
            return;
        }

        String s_create_sql = "            create table distPoller (\n"
                + "                    dpName            varchar(12),\n"
                + "                    dpIP            varchar(16) not null,\n"
                + "                    dpComment        varchar(256),\n"
                + "                    dpDiscLimit        numeric(5,2),\n"
                + "                    dpLastNodePull        timestamp without time zone,\n"
                + "                    dpLastEventPull        timestamp without time zone,\n"
                + "                    dpLastPackagePush    timestamp without time zone,\n"
                + "                    dpAdminState         integer,\n"
                + "                    dpRunState        integer,\n"
                + "                                constraint pk_dpName primary key (dpNameBogus) );\n";

        ThrowableAnticipator ta = new ThrowableAnticipator();
        ta.anticipate(new Exception(
                                    "constraint pk_dpname references column \"dpnamebogus\", which is not a column in the table distpoller"));

        m_installer.readTables(new StringReader(s_create_sql));
        try {
            m_installer.getTableColumnsFromSQL("distpoller");
        } catch (Throwable t) {
            ta.throwableReceived(t);
        }

        ta.verifyAnticipated();
    }

    public void doTestBogusConstraint(String constraint,
            String exceptionMessage) throws Exception {
        m_installer.m_fix_constraint_name = constraint;

        setupBug931(false, false);

        ThrowableAnticipator ta = new ThrowableAnticipator();
        ta.anticipate(new Exception(exceptionMessage));

        try {
            m_installer.fixConstraint();
        } catch (Throwable t) {
            ta.throwableReceived(t);
        }

        ta.verifyAnticipated();
    }

    public void doTestBug931(boolean dropForeignTable, int badRows,
            boolean fixConstraint) throws Exception {
        final String errorSubstring = "Table events contains "
                + badRows
                + " rows (out of 2) that violate new constraint "
                + s_constraint
                + ".  See the install guide for details on how to correct this problem.";

        setupBug931((badRows != 0) || fixConstraint, dropForeignTable);

        if (fixConstraint) {
            m_installer.fixConstraint();
        }

        ThrowableAnticipator ta = new ThrowableAnticipator();
        if (badRows > 0) {
            ta.anticipate(new Exception(errorSubstring));
        }

        try {
            m_installer.checkConstraints();
        } catch (Throwable t) {
            ta.throwableReceived(t);
        }

        ta.verifyAnticipated();
    }

    public void testParsePrimaryKeyMultipleColumns() throws Exception {
        if (!isDBTestEnabled()) {
            return;
        }

        // Make sure that every table, column, and key ID has at least one
        // upper case character
        final String createSQL = "create table Element (\n"
                + "    mapId           integer,\n"
                + "    elementId       integer,\n"
                + "    somethingElse       varchar(80),\n"
                + "    constraint pk_Element primary key (mapId, elementId)\n"
                + ");";

        m_installer.readTables(new StringReader(createSQL));
        Table table = m_installer.getTableFromSQL("element");

        List<Column> columns = table.getColumns();
        assertNotNull("column list is not null", columns);
        assertEquals("column count", 3, columns.size());
        assertEquals("column zero toString()", "mapid integer(4) NOT NULL",
                     columns.get(0).toString());
        assertEquals("column one toString()",
                     "elementid integer(4) NOT NULL",
                     columns.get(1).toString());
        assertEquals("column two toString()",
                     "somethingelse character varying(80)",
                     columns.get(2).toString());

        List<Constraint> foo = table.getConstraints();

        assertNotNull("constraint list is not null", foo);
        assertEquals("constraint count is one", 1, foo.size());
        Constraint f = foo.get(0);
        assertNotNull("constraint zero is not null", f);
        assertEquals("constraint getTable()", "element", f.getTable());
        assertEquals("constraint zero toString()",
                     "constraint pk_element primary key (mapid, elementid)",
                     f.toString());
    }

    public void testInsertMultipleColumns() throws SQLException {
        if (!isDBTestEnabled()) {
            return;
        }

        String command = "CREATE TABLE qrtz_job_details (\n"
                + "  JOB_NAME  VARCHAR(80) NOT NULL,\n"
                + "  JOB_GROUP VARCHAR(80) NOT NULL,\n"
                + "  CONSTRAINT pk_qrtz_job_details PRIMARY KEY (JOB_NAME,JOB_GROUP)\n"
                + ")";
        executeSQL(command);
    }

    public void testInsertMultipleColumnsGetFromDB() throws Exception {
        if (!isDBTestEnabled()) {
            return;
        }

        String command = "CREATE TABLE qrtz_job_details (\n"
                + "  JOB_NAME  VARCHAR(80) NOT NULL,\n"
                + "  JOB_GROUP VARCHAR(80) NOT NULL,\n"
                + "  CONSTRAINT pk_qrtz_job_details PRIMARY KEY (JOB_NAME,JOB_GROUP)\n"
                + ")";
        executeSQL(command);

        m_installer.getTableColumnsFromDB("qrtz_job_details");
    }

    public void testInsertMultipleColumnsGetFromDBCompare() throws Exception {
        if (!isDBTestEnabled()) {
            return;
        }

        String command = "CREATE TABLE qrtz_job_details (\n"
                + "  JOB_NAME  VARCHAR(80) NOT NULL,\n"
                + "  JOB_GROUP VARCHAR(80) NOT NULL,\n"
                + "  CONSTRAINT pk_qrtz_job_details PRIMARY KEY (JOB_NAME,JOB_GROUP)\n"
                + ")";
        executeSQL(command);

        Table table = m_installer.getTableFromDB("qrtz_job_details");
        assertNotNull("table not null", table);

        List<Constraint> constraints = table.getConstraints();
        assertNotNull("constraints not null", constraints);
        assertEquals("constraints size equals one", 1, constraints.size());
        assertEquals(
                     "constraint zero toString()",
                     "constraint pk_qrtz_job_details primary key (job_name, job_group)",
                     constraints.get(0).toString());
    }

    public void testGetColumnsFromDB() throws Exception {
        if (!isDBTestEnabled()) {
            return;
        }

        String command = "CREATE TABLE qrtz_job_details (\n"
                + "  JOB_NAME  VARCHAR(80) NOT NULL,\n"
                + "  JOB_GROUP VARCHAR(80) NOT NULL,\n"
                + "  CONSTRAINT pk_qrtz_job_details PRIMARY KEY (JOB_NAME,JOB_GROUP)\n"
                + ")";
        executeSQL(command);

        List<Column> columns = m_installer.getColumnsFromDB("qrtz_job_details");
        assertNotNull("column list not null", columns);
        assertEquals("column list size", 2, columns.size());
        assertEquals("column zero toString()",
                     "job_name character varying(80) NOT NULL",
                     columns.get(0).toString());
        assertEquals("column one toString()",
                     "job_group character varying(80) NOT NULL",
                     columns.get(1).toString());
    }

    public void testGetConstraintsFromDB() throws Exception {
        if (!isDBTestEnabled()) {
            return;
        }

        String command = "CREATE TABLE qrtz_job_details (\n"
                + "  JOB_NAME  VARCHAR(80) NOT NULL,\n"
                + "  JOB_GROUP VARCHAR(80) NOT NULL,\n"
                + "  CONSTRAINT pk_qrtz_job_details PRIMARY KEY (JOB_NAME,JOB_GROUP)\n"
                + ")";
        executeSQL(command);

        List<Column> columns = m_installer.getColumnsFromDB("qrtz_job_details");
        assertNotNull("column list not null", columns);
        List<Constraint> constraints = m_installer.getConstraintsFromDB("qrtz_job_details");
        assertNotNull("constraint list not null", constraints);
        assertEquals("constraint list size", 1, constraints.size());
        assertEquals(
                     "constraint zero toString()",
                     "constraint pk_qrtz_job_details primary key (job_name, job_group)",
                     constraints.get(0).toString());
    }
    
    public void testSetEventSource() throws Exception {
        if (!isDBTestEnabled()) {
            return;
        }
        
        addTableFromSQL("distpoller");
        addTableFromSQL("node");
        addTableFromSQLWithReplacements("events", new String[][] {
                new String[] { "eventSource\\s+varchar\\(\\d+\\) not null,", "" }
            });
        
        executeSQL("INSERT INTO events (eventID, eventUei, eventTime, eventDpName, eventCreateTime, eventSeverity, eventLog, eventDisplay) "
                   + "VALUES ( 1, 'uei.opennms.org/eatmyshorts', now(), 'Duh', now(), 1, 'n', 'n' )");

        m_installer.createTables();
        

        Statement st = m_installer.m_dbconnection.createStatement();
        ResultSet rs = st.executeQuery("SELECT eventsource from events");
        int count = 0;
        while (rs.next()) {
            assertEquals("expected events eventsrource", "OpenNMS.Eventd", rs.getString(1));
            count++;
        }
        assertEquals("expected column count", 1, count);
    
    }
    
    public void testSetOutageId() throws Exception {
        if (!isDBTestEnabled()) {
            return;
        }
        
        addTableFromSQL("distpoller");
        addTableFromSQL("node");
        addTableFromSQL("ipinterface");
        addTableFromSQL("service");
        addTableFromSQL("ifservices");
        addTableFromSQL("events");
        addTableFromSQLWithReplacements("outages", new String[][] {
                new String[] { "outageID\\s+integer not null,", "" },
                new String[] { "constraint pk_outageID primary key \\(outageID\\),", "" }
            });
        
        executeSQL("INSERT INTO outages (ipAddr, ifLostService) "
                   + "VALUES ( '1.2.3.4', now() )");

        m_installer.createTables();
        
        Statement st = m_installer.m_dbconnection.createStatement();
        ResultSet rs = st.executeQuery("SELECT outageid from outages");
        int count = 0;
        for (int expected = 1; rs.next(); expected++) {
            assertEquals("expected outages outageid", expected, rs.getInt(1));
            count++;
        }
        assertEquals("expected column count", 1, count);

    }
    
    public void testSetOutagesSvcRegainedEventId() throws Exception {
        if (!isDBTestEnabled()) {
            return;
        }
        
        addTableFromSQL("distpoller");
        addTableFromSQL("node");
        addTableFromSQL("ipinterface");
        addTableFromSQL("service");
        addTableFromSQL("ifservices");
        addTableFromSQL("events");
        addTableFromSQL("outages");
        
        executeSQL("INSERT INTO outages (outageID, ipAddr, ifLostService) "
                   + "VALUES ( 1, '1.2.3.4', now() )");

        m_installer.createTables();
        
        Statement st = m_installer.m_dbconnection.createStatement();
        ResultSet rs = st.executeQuery("SELECT svcregainedeventid from outages");
        int count = 0;
        while (rs.next()) {
            assertEquals("expected outages svcregainedeventid", 0, rs.getInt(1));
            count++;
        }
        assertEquals("expected column count", 1, count);

    }
    
    public void testSetNotificationsEventId() throws Exception {
        if (!isDBTestEnabled()) {
            return;
        }
        
        addTableFromSQL("distpoller");
        addTableFromSQL("node");
        addTableFromSQL("ipinterface");
        addTableFromSQL("service");
        addTableFromSQL("ifservices");
        addTableFromSQL("events");
        addTableFromSQLWithReplacements("notifications", new String[][] {
                new String[] { "eventID\\s+integer,", "" },
                new String[] { ",\\s+constraint fk_eventID3 foreign key \\(eventID\\) references events \\(eventID\\) ON DELETE CASCADE", "" }
            });
        
        executeSQL("INSERT INTO notifications (textMsg, notifyID, eventUEI) "
                   + "VALUES ('DJ broke it... it is always his fault', 1, "
                   + "'We ain\\\'t got no UEIs here, no sir.')");

        m_installer.createTables();
        
        Statement st = m_installer.m_dbconnection.createStatement();
        ResultSet rs = st.executeQuery("SELECT eventID from notifications");
        int count = 0;
        while (rs.next()) {
            assertEquals("expected notifications eventID", 0, rs.getInt(1));
            count++;
        }
        assertEquals("expected column count", 1, count);
    }

    
    public void testSetUsersNotifiedId() throws Exception {
        if (!isDBTestEnabled()) {
            return;
        }
        
        addTableFromSQL("distpoller");
        addTableFromSQL("node");
        addTableFromSQL("ipinterface");
        addTableFromSQL("service");
        addTableFromSQL("ifservices");
        addTableFromSQL("events");
        addTableFromSQL("notifications");
        addTableFromSQLWithReplacements("usersnotified", new String[][] {
                new String[] { "id\\s+integer not null, ", "" },
                new String[] { "constraint pk_userNotificationID primary key \\(id\\),", "" }
            });
        
        executeSQL("INSERT INTO usersNotified (userID) "
                   + "VALUES ('DJ... it is always his fault')");

        m_installer.createTables();
        
        Statement st = m_installer.m_dbconnection.createStatement();
        ResultSet rs = st.executeQuery("SELECT id from usersnotified");
        int count = 0;
        for (int expected = 1; rs.next(); expected++) {
            assertEquals("expected usersNotified id", expected, rs.getInt(1));
            count++;
        }
        assertEquals("expected column count", 1, count);
    }

    public void testUpdateFoo() throws Exception {
        if (!isDBTestEnabled()) {
            return;
        }
        
        addTableFromSQL("distPoller");
        addTableFromSQL("node");
      
        // No ID column
        addTableFromSQLWithReplacements("ipinterface", new String[][] {
                new String[] { "id\\s+integer,", "" },
                new String[] { "constraint pk_ipInterface_id primary key \\(id\\),", "" }
                });
        
        addTableFromSQL("service");
        
        // No ID or ipInterfaceID column
        addTableFromSQLWithReplacements("ifservices", new String[][] {
                new String[] { "id\\s+integer,", "" },
                new String[] { "constraint pk_ifServices_id primary key \\(id\\),", "" },
                new String[] { "ipInterfaceID\\s+integer not null,", "" },
                new String[] { "constraint fk_ipInterfaceID foreign key \\(id\\) references ipInterface ON DELETE CASCADE,", "" }
                });

        executeSQL("INSERT INTO node (nodeId, nodeCreateTime) VALUES ( 1, now() )");
        executeSQL("INSERT INTO ipInterface (nodeId, ipAddr, ifIndex) VALUES ( 1, '1.2.3.4', null )");
        executeSQL("INSERT INTO ipInterface (nodeId, ipAddr, ifIndex) VALUES ( 1, '1.2.3.9', 1 )");
        executeSQL("INSERT INTO service (serviceID, serviceName) VALUES ( 1, 'COFFEE-READY' )");
        executeSQL("INSERT INTO ifServices (nodeID, ipAddr, ifIndex, serviceID) VALUES ( 1, '1.2.3.4', null, 1)");
        executeSQL("INSERT INTO ifServices (nodeID, ipAddr, ifIndex, serviceID) VALUES ( 1, '1.2.3.9', 1, 1)");
        
        m_installer.createTables();
        
        Statement st = m_installer.m_dbconnection.createStatement();
        ResultSet rs = st.executeQuery("SELECT id from ipInterface");
        int count = 0;
        for (int expected = 1; rs.next(); expected++) {
            assertEquals("expected ipInterface id", expected, rs.getInt(1));
            count++;
        }
        assertEquals("expected column count", 2, count);

        rs = st.executeQuery("SELECT id, ipInterfaceID from ifServices");
        count = 0;
        for (int expected = 1; rs.next(); expected++) {
            assertEquals("expected ifServices id", expected, rs.getInt(1));
            assertEquals("expected ifServices ipInterfaceId", expected, rs.getInt(2));
            count++;
        }
        assertEquals("expected column count", 2, count);
    }
    
    public void addTableFromSQL(String tableName) {
        String partialSQL = null;
        try {
            partialSQL = m_installer.getTableCreateFromSQL(tableName);
        } catch (Exception e) {
            fail("Could not get SQL for table '" + tableName + "'", e);
        }
        
        executeSQL("CREATE TABLE " + tableName + " ( " + partialSQL + " )");
    }
    
    public void addTableFromSQLWithReplacements(String tableName, String[][] replacements) {
        String partialSQL = null;
        try {
            partialSQL = m_installer.getTableCreateFromSQL(tableName);
        } catch (Exception e) {
            fail("Could not get SQL for table '" + tableName + "'", e);
        }
        
        for (String[] replacement : replacements) {
            partialSQL = partialSQL.replaceAll(replacement[0], replacement[1]);
        }

        executeSQL("CREATE TABLE " + tableName + " ( " + partialSQL + " )");
    }
}