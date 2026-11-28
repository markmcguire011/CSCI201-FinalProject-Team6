package listingsupport;

import edu.usc.marketplace.dao.Database;
import edu.usc.marketplace.dao.ListingActions;
import edu.usc.marketplace.dao.ListingDAO;
import edu.usc.marketplace.model.AccountType;
import edu.usc.marketplace.model.ListingStatus;
import edu.usc.marketplace.model.Member;
import edu.usc.marketplace.storage.FileStorageService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/** Each test gets an empty, uniquely named MySQL schema and a private upload directory. */
public abstract class ListingDatabaseFixture {
    protected final Member seller = new Member(1, "Sam Park", null, AccountType.STUDENT, true);
    protected final int buyer = 2;
    @TempDir protected Path uploads;
    protected ListingDAO listings;
    protected ListingActions actions;
    protected Database db;
    private Connection admin;
    private String schema;
    private boolean created;

    @BeforeEach
    void createSchema() throws Exception {
        int port = Integer.parseInt(System.getenv("MARKETPLACE_TEST_MYSQL_PORT"));
        String base = "jdbc:mysql://127.0.0.1:" + port + "/";
        String options = "?allowPublicKeyRetrieval=true&serverTimezone=UTC";
        String user = System.getenv("MARKETPLACE_TEST_MYSQL_USER");
        String password = System.getenv("MARKETPLACE_TEST_MYSQL_PASSWORD");
        admin = DriverManager.getConnection(base + options, user, password);
        schema = "usc_listing_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + schema);
            created = true;
        }
        admin.setCatalog(schema);
        try (var statement = admin.createStatement()) {
            // Apply the team's schema to this test database. Skip its application database selector.
            StringBuilder ddl = new StringBuilder();
            for (String line : Files.readAllLines(Path.of("database", "schema.sql"))) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--") || trimmed.equals("USE usc_marketplace;")) continue;
                ddl.append(line).append('\n');
                if (trimmed.endsWith(";")) {
                    statement.execute(ddl.toString());
                    ddl.setLength(0);
                }
            }
            if (!ddl.toString().isBlank()) throw new IllegalStateException("Incomplete schema statement.");
        }
        db = new Database(base + schema + options, user, password);
        listings = new ListingDAO(db);
        actions = new ListingActions(db, new FileStorageService(uploads));
        execute("INSERT INTO users(user_id,full_name,email,password_hash,account_type,verified) VALUES "
                + "(1,'Sam Park','seller@usc.edu','fixture','STUDENT',TRUE),"
                + "(2,'Taylor Lee','buyer@usc.edu','fixture','STUDENT',TRUE),"
                + "(3,'Alex Kim','other@usc.edu','fixture','STUDENT',TRUE)");
    }

    @AfterEach
    void removeSchema() throws Exception {
        try {
            if (db != null) db.shutdown();
            if (created) {
                try (var statement = admin.createStatement()) { statement.execute("DROP DATABASE " + schema); }
            }
        } finally {
            if (admin != null) admin.close();
        }
    }

    protected int listing(String title, int price, ListingStatus status) throws Exception {
        String sql = "INSERT INTO listings(seller_id,title,category,price,item_condition,course_code,pickup_spot,description,status)"
                + " VALUES(1,?,'TEXTBOOKS',?,'GOOD','CSCI 201','Library','Used book',?)";
        try (var ps = admin.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title);
            ps.setInt(2, price);
            ps.setString(3, status.name());
            ps.executeUpdate();
            try (var keys = ps.getGeneratedKeys()) { keys.next(); return keys.getInt(1); }
        }
    }

    protected void execute(String sql, Object... args) throws Exception {
        try (var ps = admin.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            ps.executeUpdate();
        }
    }

    protected long count(String sql) throws Exception {
        try (var statement = admin.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
