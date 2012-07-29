package com.jcabi.jdbc;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.sql.DataSource;
import org.apache.commons.dbutils.DbUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//import com.jcabi.log.Logger;

/**
 * Universal JDBC wrapper.
 *
 * <p>Execute a simple SQL query over a JDBC data source:
 *
 * <pre>String name = new JdbcSession(source)
 *   .sql("SELECT name FROM foo WHERE id = ?")
 *   .set(123)
 *   .select(
 *     new JdbcSession.Handler&lt;String&gt;() {
 *       &#64;Override
 *       public String handle(final ResultSet rset) throws SQLException {
 *         rset.next();
 *         return rset.getString(1);
 *       }
 *     }
 *   );</pre>
 *
 * <p>There are a number of convenient pre-defined handlers, like
 * {@link VoidHandler}, {@link NotEmptyHandler}, {@link SingleHandler}, etc.
 *
 * <p>Methods {@link #insert(Handler)}, {@link #update()}, and
 * {@link #select(Handler)} clean the list of arguments pre-set by
 * {@link #set(Object)}. The class can be used for a complex transaction, when
 * it's necessary to perform a number of SQL statements in a group. For
 * example, the following construct will execute two SQL queries, in a single
 * transaction and will "commit" at the end (or rollback the entire transaction
 * in case of any error in between):
 *
 * <pre>new JdbcSession(source)
 *   .autocommit(false)
 *   .sql("START TRANSACTION")
 *   .update()
 *   .sql("DELETE FROM foo WHERE id = ?")
 *   .set(444)
 *   .update()
 *   .set(555)
 *   .update()
 *   .commit();</pre>
 *
 * <p>The following SQL queries will be sent to the database:
 *
 * <pre>START TRANSACTION;
 * DELETE FROM foo WHERE id = 444;
 * DELETE FROM foo WHERE id = 555;
 * COMMIT;</pre>
 *
 * <p>{@link #autocommit(boolean)} (with {@code false} as an argument)
 * can be used when it's necessary to execute
 * a statement and leave the connection open. For example when shutting down
 * the database through SQL:
 *
 * <pre>
 * new JdbcSession(&#47;* H2 Database data source *&#47;)
 *   .autocommit(false)
 *   .sql("SHUTDOWN COMPACT")
 *   .update();
 * </pre>
 *
 * <p>This class is thread-safe.
 *
 * @author Yegor Bugayenko (yegor@jcabi.com)
 * @version $Id$
 * @since 0.1.8
 */
@SuppressWarnings("PMD.TooManyMethods")
public final class JdbcSession {
    final  Logger logger = LoggerFactory.getLogger(JdbcSession.class);

    /**
     * Connection to use.
     */
    private final transient Connection conn;

    /**
     * Shall we close/autocommit automatically?
     */
    private transient boolean auto = true;

    /**
     * Arguments.
     */
    private final transient List<Object> args =
        new CopyOnWriteArrayList<Object>();

    /**
     * The query to use.
     */
    private transient String query;

    /**
     * Handler or ResultSet.
     * @param <T> Type of expected result
     */
    public interface Handler<T> {
        /**
         * Process the result set and return some value.
         * @param rset The result set to process
         * @return The result
         * @throws SQLException If something goes wrong inside
         */
        T handle(ResultSet rset) throws SQLException;
    }

    /**
     * Public ctor.
     * @param source Data source
     */
    public JdbcSession(final DataSource source) {
        try {
            this.conn = source.getConnection();
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Use this SQL query (with optional parameters inside).
     *
     * <p>The query will be used in {@link PreparedStatement}, that's why
     * you can use the same formatting as there. Arguments shall be marked
     * as {@code "?"} (question marks). For example:
     *
     * <pre>
     * String name = new JdbcSession(source)
     *   .sql("INSERT INTO foo (id, name) VALUES (?, ?)")
     *   .set(556677)
     *   .set("Jeffrey Lebowski")
     *   .insert(new VoidHandler());
     * </pre>
     *
     * @param sql The SQL query to use
     * @return This object
     */
    public JdbcSession sql(final String sql) {
        synchronized (this.conn) {
            this.query = sql;
        }
        return this;
    }

    /**
     * Shall we auto-commit?
     *
     * <p>By default this flag is set to TRUE, which means that methods
     * {@link #insert(Handler)}, {@link #update()}, and
     * {@link #select(Handler)} will call {@link Connection#commit()} after
     * their successful execution.
     *
     * @param autocommit Shall we?
     * @return This object
     */
    public JdbcSession autocommit(final boolean autocommit) {
        synchronized (this.conn) {
            this.auto = autocommit;
        }
        return this;
    }

    /**
     * Set new param for the query.
     *
     * <p>The following types are supported: {@link Boolean}, {@link Date},
     * {@link Utc}, {@link Long}, {@link Integer}. All other types will be
     * converted to {@link String} using their {@code toString()} methods.
     *
     * @param value The value to add
     * @return This object
     */
    public JdbcSession set(final Object value) {
        this.args.add(value);
        return this;
    }

    /**
     * Commit the transation (calls {@link Connection#commit()} and then
     * {@link Connection#close()}).
     */
    public void commit() {
        try {
            this.conn.commit();
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
        DbUtils.closeQuietly(this.conn);
    }

    /**
     * Make SQL {@code INSERT} request.
     *
     * <p>{@link Handler} will receive a {@link ResultSet} of generated keys.
     *
     * @param handler The handler or result
     * @return The result
     * @param <T> Type of response
     */
    public <T> T insert(final Handler<T> handler) {
        return this.run(
            handler,
            new Fetcher() {
                @Override
                public ResultSet fetch(final PreparedStatement stmt)
                    throws SQLException {
                    stmt.execute();
                    return stmt.getGeneratedKeys();
                }
            }
        );
    }

    /**
     * Make SQL {@code UPDATE} request.
     * @return This object
     */
    public JdbcSession update() {
        this.run(
            new VoidHandler(),
            new Fetcher() {
                @Override
                public ResultSet fetch(final PreparedStatement stmt)
                    throws SQLException {
                    stmt.executeUpdate();
                    return null;
                }
            }
        );
        return this;
    }

    /**
     * Make SQL {@code SELECT} request.
     * @param handler The handler or result
     * @return The result
     * @param <T> Type of response
     */
    public <T> T select(final Handler<T> handler) {
        return this.run(
            handler,
            new Fetcher() {
                @Override
                public ResultSet fetch(final PreparedStatement stmt)
                    throws SQLException {
                    return stmt.executeQuery();
                }
            }
        );
    }

    /**
     * The fetcher.
     */
    private interface Fetcher {
        /**
         * Fetch result set from statement.
         * @param stmt The statement
         * @return The result set
         * @throws SQLException If some problem
         */
        ResultSet fetch(PreparedStatement stmt) throws SQLException;
    }

    /**
     * Run this handler, and this fetcher.
     * @param handler The handler or result
     * @param fetcher Fetcher of result set
     * @return The result
     * @param <T> Type of response
     * @checkstyle ExecutableStatementCount (100 lines)
     */
    @SuppressWarnings("PMD.CloseResource")
    private <T> T run(final Handler<T> handler, final Fetcher fetcher) {
        if (this.query == null) {
            throw new IllegalStateException("call #sql() first");
        }
        final long start = System.currentTimeMillis();
        T result;
        try {
            this.conn.setAutoCommit(false);
            final PreparedStatement stmt = this.conn.prepareStatement(
                this.query,
                Statement.RETURN_GENERATED_KEYS
            );
            try {
                this.parametrize(stmt);
                final ResultSet rset = fetcher.fetch(stmt);
                // @checkstyle NestedTryDepth (5 lines)
                try {
                    result = handler.handle(rset);
                } finally {
                    DbUtils.closeQuietly(rset);
                }
            } finally {
                DbUtils.closeQuietly(stmt);
            }
        } catch (SQLException ex) {
            if (!this.auto) {
                DbUtils.rollbackAndCloseQuietly(this.conn);
            }
            logger.error("#run(..): '%s': %[exception]s", this.query, ex);
            //Logger.error(this, "#run(..): '%s': %[exception]s", this.query, ex);
            throw new IllegalArgumentException(ex);
        } finally {
            if (this.auto) {
                this.commit();
            }
            this.args.clear();
        }
        logger.debug("#run(): '%s' done in %[ms]s", this.query, System.currentTimeMillis() - start); 
      //Logger.debug(this, "#run(): '%s' done in %[ms]s", this.query, System.currentTimeMillis() - start);
        return result;
    }

    /**
     * Add params to the statement.
     * @param stmt The statement to parametrize
     * @throws SQLException If some problem
     */
    private void parametrize(final PreparedStatement stmt) throws SQLException {
        int pos = 1;
        for (Object arg : this.args) {
            if (arg == null) {
                stmt.setString(pos, null);
            } else if (arg instanceof Long) {
                stmt.setLong(pos, Long.class.cast(arg));
            } else if (arg instanceof Boolean) {
                stmt.setBoolean(pos, Boolean.class.cast(arg));
            } else if (arg instanceof Date) {
                stmt.setDate(pos, Date.class.cast(arg));
            } else if (arg instanceof Integer) {
                stmt.setInt(pos, Integer.class.cast(arg));
            } else if (arg instanceof Utc) {
                Utc.class.cast(arg).setTimestamp(stmt, pos);
            } else {
                stmt.setString(pos, arg.toString());
            }
            ++pos;
        }
    }

}
