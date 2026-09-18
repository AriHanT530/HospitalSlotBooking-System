package com.hospital.dao;

import com.hospital.util.AppLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Unit 5: JDBC layer.
 *
 * Driver co-ordinates live in config/db.properties, never in source - this is
 * the "specifying JDBC driver information externally" requirement of the
 * syllabus. If the properties file or the driver jar is absent the
 * application simply stays on CSV storage, so the project always runs with a
 * plain JDK and no third-party jars.
 */
public final class DatabaseManager {

    private static final AppLogger LOG = AppLogger.getInstance();
    private static final Path CONFIG = Paths.get("config", "db.properties");
    private static Boolean available;
    private static Properties props;

    private DatabaseManager() { }

    /** True when a usable JDBC driver and URL were configured. */
    public static synchronized boolean isAvailable() {
        if (available != null) return available;
        available = false;
        try {
            props = loadProperties();
            if (props == null || !Boolean.parseBoolean(props.getProperty("db.enabled", "false"))) {
                LOG.info("JDBC disabled - running on CSV storage");
                return false;
            }
            String driver = props.getProperty("db.driver", "").trim();
            if (!driver.isEmpty()) {
                Class.forName(driver);   // Unit 2: reflection-based driver loading
            }
            try (Connection c = getConnection()) {
                available = c != null && !c.isClosed();
            }
            if (available) {
                initialiseSchema();
                LOG.info("JDBC connection established: " + props.getProperty("db.url"));
            }
        } catch (ClassNotFoundException e) {
            LOG.warn("JDBC driver class not on the classpath - staying on CSV storage");
        } catch (SQLException | IOException e) {
            LOG.warn("JDBC unavailable (" + e.getMessage() + ") - staying on CSV storage");
        }
        return available;
    }

    public static Connection getConnection() throws SQLException {
        if (props == null) {
            try {
                props = loadProperties();
            } catch (IOException e) {
                throw new SQLException("Cannot read config/db.properties", e);
            }
        }
        return DriverManager.getConnection(
                props.getProperty("db.url"),
                props.getProperty("db.user", ""),
                props.getProperty("db.password", ""));
    }

    private static Properties loadProperties() throws IOException {
        if (Files.exists(CONFIG)) {
            try (InputStream in = Files.newInputStream(CONFIG)) {
                Properties p = new Properties();
                p.load(in);
                return p;
            }
        }
        return null;
    }

    /** Creates the appointment table if the schema is not there yet. */
    private static void initialiseSchema() {
        String ddl =
            "CREATE TABLE IF NOT EXISTS appointments (" +
            " id VARCHAR(16) PRIMARY KEY," +
            " patient_id VARCHAR(16) NOT NULL," +
            " doctor_id VARCHAR(16) NOT NULL," +
            " appt_date DATE NOT NULL," +
            " start_time TIME NOT NULL," +
            " end_time TIME NOT NULL," +
            " status VARCHAR(16) NOT NULL," +
            " fee DOUBLE PRECISION NOT NULL," +
            " reason VARCHAR(255)," +
            " created_at TIMESTAMP NOT NULL)";
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate(ddl);
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_doc_date ON appointments(doctor_id, appt_date)");
        } catch (SQLException e) {
            LOG.warn("Schema initialisation skipped: " + e.getMessage());
        }
    }
}
