package edu.usc.marketplace.dao;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;


public final class Database {
    public static final int POOL_SIZE = 10;
    private static final long WAIT_SECONDS = 5;

    private static volatile Database instance;

    private final String url, user, password;
    private final BlockingQueue<Connection> idle = new ArrayBlockingQueue<>(POOL_SIZE);
    private final List<Connection> all = new ArrayList<>();
    private volatile boolean closed;

    public Database(String url, String user, String password) throws SQLException {
        this.url = url;
        this.user = user;
        this.password = password;
        loadDriver();
        try {
            for (int i = 0; i < POOL_SIZE; i++) {
                Connection c = open();
                all.add(c);
                idle.add(c);
            }
        } catch (SQLException e) {
            shutdown();
            throw e;
        }
    }

    /** Called once by AppStartup when Tomcat starts the application. */
    public static void init(Database db) {
        instance = db;
    }

    /** The shared pool. DAOs created with their no-argument constructor use this one. */
    public static Database get() {
        Database db = instance;
        if (db == null) {
            throw new IllegalStateException("The database pool has not been started. Check WEB-INF/config.properties.");
        }
        return db;
    }

    /**
     * Borrows a connection. Always use it in try-with-resources so it goes back to the pool:
     * try (Connection c = db.getConnection()) { ... }
     */
    public Connection getConnection() throws SQLException {
        if (closed) throw new SQLException("The database pool is shut down.");
        Connection raw;
        try {
            raw = idle.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for a database connection.", e);
        }
        if (raw == null) throw new SQLException("All " + POOL_SIZE + " database connections are busy.");
        // MySQL closes connections that sit unused for hours, so replace a dead one before handing it out.
        if (!raw.isValid(2)) {
            raw = replace(raw);
        }
        return wrap(raw);
    }

    /**
     * Runs several statements as one transaction (Mark as Sold, resolving a report).
     * If anything fails, every change is undone and the exception is rethrown.
     */
    public <T> T inTransaction(SqlWork<T> work) {
        try (Connection c = getConnection()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataException(e);
        }
    }

    /** The statements inside one transaction. */
    @FunctionalInterface
    public interface SqlWork<T> {
        T run(Connection c) throws SQLException;
    }

  
    /** Called when Tomcat stops the application. */
    public void shutdown() {
        closed = true;
        synchronized (all) {
            for (Connection c : all) {
                try {
                    c.close();
                } catch (SQLException ignored) {
                    // already closed
                }
            }
            all.clear();
        }
        idle.clear();
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private Connection replace(Connection dead) throws SQLException {
        try {
            dead.close();
        } catch (SQLException ignored) {
            // it was already broken
        }
        Connection fresh;
        try {
            fresh = open();
        } catch (SQLException e) {
            synchronized (all) {
                all.remove(dead);
            }
            throw e;
        }
        synchronized (all) {
            all.remove(dead);
            all.add(fresh);
        }
        return fresh;
    }

    private static void loadDriver() {
        // Tomcat does not always find the driver in WEB-INF/lib on its own, so load it by name.
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            // Not on the classpath: DriverManager will report a clear error when connecting.
        }
    }

    /**
     * Wraps a real connection so that close() returns it to the pool instead of closing it.
     * A connection left in the middle of a transaction is rolled back before it is reused.
     */
    private Connection wrap(Connection raw) {
        InvocationHandler handler = new InvocationHandler() {
            private boolean returned;

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String name = method.getName();
                if (name.equals("close")) {
                    if (!returned) {
                        returned = true;
                        giveBack(raw);
                    }
                    return null;
                }
                if (name.equals("isClosed")) return returned || raw.isClosed();
                if (returned) throw new SQLException("This connection was already returned to the pool.");
                try {
                    return method.invoke(raw, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
        };
        return (Connection) Proxy.newProxyInstance(
                Database.class.getClassLoader(), new Class<?>[]{Connection.class}, handler);
    }

    private void giveBack(Connection raw) {
        if (closed) {
            try {
                raw.close();
            } catch (SQLException ignored) {
                // shutting down anyway
            }
            return;
        }
        try {
            if (!raw.getAutoCommit()) {
                raw.rollback();
                raw.setAutoCommit(true);
            }
            idle.offer(raw);
        } catch (SQLException e) {
            // The connection is broken: open a new one so the pool keeps 10.
            try {
                idle.offer(replace(raw));
            } catch (SQLException ignored) {
                // MySQL is down; getConnection() will report it
            }
        }
    }
}
