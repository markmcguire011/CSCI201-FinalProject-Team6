package edu.usc.marketplace.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared code for every DAO (section 15).
 *
 * Every query uses a PreparedStatement with ? placeholders, so text typed by a user is always sent as data
 * and can never change the SQL (no SQL injection). Each method borrows a connection from the pool and
 * returns it when done. The versions that take a Connection are for statements inside a transaction
 * (see Database.inTransaction).
 *
 * @param <T> the entity class a row is turned into
 */
public abstract class BaseDAO<T> {
    protected final Database db;

    protected BaseDAO(Database db) {
        this.db = db;
    }

    protected BaseDAO() {
        this(Database.get());
    }

    /** Turns the current row of the result into an entity. */
    protected abstract T mapRow(ResultSet rs) throws SQLException;

    // ----- with their own connection -----

    protected T queryOne(String sql, Object... args) {
        try (Connection c = db.getConnection()) {
            return queryOne(c, sql, args);
        } catch (SQLException e) {
            throw new DataException(e);
        }
    }

    protected List<T> queryList(String sql, Object... args) {
        try (Connection c = db.getConnection()) {
            return queryList(c, sql, args);
        } catch (SQLException e) {
            throw new DataException(e);
        }
    }

    /** INSERT, UPDATE or DELETE. Returns how many rows changed. */
    protected int update(String sql, Object... args) {
        try (Connection c = db.getConnection()) {
            return update(c, sql, args);
        } catch (SQLException e) {
            throw new DataException(e);
        }
    }

    /** INSERT that returns the new row's AUTO_INCREMENT id. */
    protected int insert(String sql, Object... args) {
        try (Connection c = db.getConnection()) {
            return insert(c, sql, args);
        } catch (SQLException e) {
            throw new DataException(e);
        }
    }

    /** A single number, e.g. SELECT COUNT(*). Returns 0 when there is no row. */
    protected long queryLong(String sql, Object... args) {
        try (Connection c = db.getConnection()) {
            return queryLong(c, sql, args);
        } catch (SQLException e) {
            throw new DataException(e);
        }
    }

    // ----- inside a transaction -----

    protected T queryOne(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    protected List<T> queryList(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (rs.next()) rows.add(mapRow(rs));
                return rows;
            }
        }
    }

    protected int update(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            return ps.executeUpdate();
        }
    }

    protected int insert(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, args);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    protected long queryLong(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    // ----- helpers -----

    /** Puts each value into its ? placeholder, in order. */
    protected static void bind(PreparedStatement ps, Object... args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            Object v = args[i];
            if (v instanceof Enum<?> e) v = e.name();
            else if (v instanceof LocalDateTime t) v = Timestamp.valueOf(t);
            ps.setObject(i + 1, v);
        }
    }

    protected static LocalDateTime time(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toLocalDateTime();
    }

    /** For nullable INT columns: returns null instead of 0. */
    protected static Integer intOrNull(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : v;
    }

    /** Escapes % and _ so a search for "50%" looks for that text, not for a pattern. */
    protected static String likePattern(String text) {
        String escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
