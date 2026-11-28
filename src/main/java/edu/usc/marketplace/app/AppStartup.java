package edu.usc.marketplace.app;

import edu.usc.marketplace.dao.Database;
import edu.usc.marketplace.service.Mailer;
import edu.usc.marketplace.storage.FileStorageService;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs once when Tomcat starts the application, before any request:
 * reads WEB-INF/config.properties, opens the pool of 10 database connections, and sets up the upload folders.
 * When Tomcat stops the application, it closes the connections.
 *
 * config.properties:
 *   db.url=jdbc:mysql://localhost:3306/usc_marketplace?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Los_Angeles
 *   db.user=marketplace_app
 *   db.password=...
 *   upload.dir=C:/marketplace/uploads      (optional; default: a usc-marketplace-uploads folder in the user's home folder)
 *   mail.host, mail.port, mail.user, mail.password, mail.from      (optional; see Mailer)
 */
@WebListener
public class AppStartup implements ServletContextListener {
    private static final Logger LOG = Logger.getLogger(AppStartup.class.getName());
    private Database db;
    private ListingJobs listingJobs;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext ctx = event.getServletContext();
        Properties config = loadConfig(ctx);

        try {
            db = new Database(required(config, "db.url"), required(config, "db.user"), config.getProperty("db.password", ""));
            Database.init(db);
            LOG.info("Database pool started with " + Database.POOL_SIZE + " connections.");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not connect to MySQL. Check that MySQL is running and that db.url, "
                    + "db.user and db.password in WEB-INF/config.properties are correct. (" + e.getMessage() + ")", e);
        }

        Mailer mailer = new Mailer(config);
        Mailer.init(mailer);
        LOG.info(mailer.isConfigured() ? "Email is set up: verification codes are sent with " + config.getProperty("mail.host").trim() + "."
                : "Email is not set up (no mail.host in config.properties): verification codes are written to this console instead.");

        String uploadDir = config.getProperty("upload.dir",
                Paths.get(System.getProperty("user.home"), "usc-marketplace-uploads").toString());
        try {
            Path folder = Paths.get(uploadDir);
            FileStorageService.init(new FileStorageService(folder));
            listingJobs = new ListingJobs(db);
            LOG.info("Uploads are saved in " + folder.toAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException("Could not create the upload folder " + uploadDir + ". Check upload.dir.", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        if (listingJobs != null) listingJobs.close();
        if (db != null) db.shutdown();
        // Unregister the MySQL driver this application loaded, so Tomcat can unload it cleanly.
        ClassLoader appLoader = Thread.currentThread().getContextClassLoader();
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver d = drivers.nextElement();
            if (d.getClass().getClassLoader() == appLoader) {
                try {
                    DriverManager.deregisterDriver(d);
                } catch (SQLException e) {
                    LOG.log(Level.WARNING, "Could not unregister " + d, e);
                }
            }
        }
    }

    private static Properties loadConfig(ServletContext ctx) {
        Properties p = new Properties();
        try (InputStream in = ctx.getResourceAsStream("/WEB-INF/config.properties")) {
            if (in == null) {
                throw new IllegalStateException("WEB-INF/config.properties is missing. "
                        + "See the deployment document, Database setup, step 4.");
            }
            p.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read WEB-INF/config.properties.", e);
        }
        return p;
    }

    private static String required(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) throw new IllegalStateException(key + " is missing from WEB-INF/config.properties.");
        return v.trim();
    }
}
