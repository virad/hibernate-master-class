package com.vladmihalcea.hibernate.masterclass.laboratory.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.spi.PersistenceUnitInfo;
import javax.sql.DataSource;

import org.hibernate.Interceptor;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl;
import org.hibernate.jpa.boot.internal.PersistenceUnitInfoDescriptor;
import org.hibernate.stat.SecondLevelCacheStatistics;

import org.junit.After;
import org.junit.Before;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;
import net.sourceforge.jtds.jdbcx.JtdsDataSource;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.ChainListener;
import net.ttddyy.dsproxy.listener.DefaultQueryLogEntryCreator;
import net.ttddyy.dsproxy.listener.SLF4JQueryLoggingListener;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import oracle.jdbc.pool.OracleDataSource;
import org.hsqldb.jdbc.JDBCDataSource;
import org.postgresql.ds.PGSimpleDataSource;

public abstract class AbstractTest {
	
	protected interface DataSourceProvider {

        enum IdentifierStrategy {
            IDENTITY,
            SEQUENCE
        }

        enum Database {
            HSQLDB,
            POSTGRESQL,
            ORACLE,
            MYSQL,
            SQLSERVER
        }

        String hibernateDialect();

        DataSource dataSource();

        Class<? extends DataSource> dataSourceClassName();

        Properties dataSourceProperties();

        List<IdentifierStrategy> identifierStrategies();

        Database database();
    }

    protected static class DataAccessException extends RuntimeException {
        public DataAccessException() {
        }

        public DataAccessException(String message) {
            super(message);
        }

        public DataAccessException(String message, Throwable cause) {
            super(message, cause);
        }

        public DataAccessException(Throwable cause) {
            super(cause);
        }

        public DataAccessException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }

    static {
        Thread.currentThread().setName("Alice");
    }

    protected enum LockType {
        LOCKS,
        MVLOCKS,
        MVCC
    }

    public static class HsqldbDataSourceProvider implements DataSourceProvider {

        @Override
        public String hibernateDialect() {
            return "org.hibernate.dialect.HSQLDialect";
        }

        @Override
        public DataSource dataSource() {
            JDBCDataSource dataSource = new JDBCDataSource();
            dataSource.setUrl("jdbc:hsqldb:mem:test");
            dataSource.setUser("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Override
        public Class<? extends DataSource> dataSourceClassName() {
            return JDBCDataSource.class;
        }

        @Override
        public Properties dataSourceProperties() {
            Properties properties = new Properties();
            properties.setProperty("url", "jdbc:hsqldb:mem:test");
            properties.setProperty("user", "sa");
            properties.setProperty("password", "");
            return properties;
        }

        @Override
        public List<IdentifierStrategy> identifierStrategies() {
            return Arrays.asList(IdentifierStrategy.IDENTITY, IdentifierStrategy.SEQUENCE);
        }

        @Override
        public Database database() {
            return Database.HSQLDB;
        }
    }

    public static class PostgreSQLDataSourceProvider implements DataSourceProvider {
        @Override
        public String hibernateDialect() {
            return "org.hibernate.dialect.PostgreSQL9Dialect";
        }

        @Override
        public DataSource dataSource() {
            PGSimpleDataSource dataSource = new PGSimpleDataSource();
            dataSource.setDatabaseName("high_performance_java_persistence");
            dataSource.setServerName("localhost");
            dataSource.setUser("postgres");
            dataSource.setPassword("admin");
            return dataSource;
        }

        @Override
        public Class<? extends DataSource> dataSourceClassName() {
            return PGSimpleDataSource.class;
        }

        @Override
        public Properties dataSourceProperties() {
            Properties properties = new Properties();
            properties.setProperty("databaseName", "high_performance_java_persistence");
            properties.setProperty("serverName", "localhost");
            properties.setProperty("user", "postgres");
            properties.setProperty("password", "admin");
            return properties;
        }

        @Override
        public List<IdentifierStrategy> identifierStrategies() {
            return Arrays.asList(IdentifierStrategy.SEQUENCE);
        }

        @Override
        public Database database() {
            return Database.POSTGRESQL;
        }
    }

    public static class OracleDataSourceProvider implements DataSourceProvider {
        @Override
        public String hibernateDialect() {
            return "org.hibernate.dialect.Oracle10gDialect";
        }

        @Override
        public DataSource dataSource() {
            try {
                OracleDataSource dataSource = new OracleDataSource();
                dataSource.setDatabaseName("high_performance_java_persistence");
                dataSource.setURL("jdbc:oracle:thin:@localhost:1521/xe");
                dataSource.setUser("oracle");
                dataSource.setPassword("admin");
                return dataSource;
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public Class<? extends DataSource> dataSourceClassName() {
            return OracleDataSource.class;
        }

        @Override
        public Properties dataSourceProperties() {
            Properties properties = new Properties();
            properties.setProperty("databaseName", "high_performance_java_persistence");
            properties.setProperty("URL", "jdbc:oracle:thin:@localhost:1521/xe");
            properties.setProperty("user", "oracle");
            properties.setProperty("password", "admin");
            return properties;
        }

        @Override
        public List<IdentifierStrategy> identifierStrategies() {
            return Arrays.asList(IdentifierStrategy.SEQUENCE);
        }

        @Override
        public Database database() {
            return Database.ORACLE;
        }
    }

    public static class MySQLDataSourceProvider implements DataSourceProvider {

        private boolean rewriteBatchedStatements = true;

        private boolean cachePrepStmts = false;

        private boolean useServerPrepStmts = false;

        public boolean isRewriteBatchedStatements() {
            return rewriteBatchedStatements;
        }

        public void setRewriteBatchedStatements(boolean rewriteBatchedStatements) {
            this.rewriteBatchedStatements = rewriteBatchedStatements;
        }

        public boolean isCachePrepStmts() {
            return cachePrepStmts;
        }

        public void setCachePrepStmts(boolean cachePrepStmts) {
            this.cachePrepStmts = cachePrepStmts;
        }

        public boolean isUseServerPrepStmts() {
            return useServerPrepStmts;
        }

        public void setUseServerPrepStmts(boolean useServerPrepStmts) {
            this.useServerPrepStmts = useServerPrepStmts;
        }

        @Override
        public String hibernateDialect() {
            return "org.hibernate.dialect.MySQL5Dialect";
        }

        @Override
        public DataSource dataSource() {
            MysqlDataSource dataSource = new MysqlDataSource();
            dataSource.setURL("jdbc:mysql://localhost/high_performance_java_persistence?user=mysql&password=admin" +
                    "&rewriteBatchedStatements=" + rewriteBatchedStatements +
                    "&cachePrepStmts=" + cachePrepStmts +
                    "&useServerPrepStmts=" + useServerPrepStmts
            );
            return dataSource;
        }

        @Override
        public Class<? extends DataSource> dataSourceClassName() {
            return MysqlDataSource.class;
        }

        @Override
        public Properties dataSourceProperties() {
            Properties properties = new Properties();
            properties.setProperty("url", "jdbc:mysql://localhost/high_performance_java_persistence?user=mysql&password=admin");
            return properties;
        }

        @Override
        public List<IdentifierStrategy> identifierStrategies() {
            return Arrays.asList(IdentifierStrategy.IDENTITY);
        }

        @Override
        public Database database() {
            return Database.MYSQL;
        }

        @Override
        public String toString() {
            return "MySQLDataSourceProvider{" +
                    "rewriteBatchedStatements=" + rewriteBatchedStatements +
                    ", cachePrepStmts=" + cachePrepStmts +
                    ", useServerPrepStmts=" + useServerPrepStmts +
                    '}';
        }
    }

    public static class SQLServerDataSourceProvider implements DataSourceProvider {
        @Override
        public String hibernateDialect() {
            return "org.hibernate.dialect.SQLServer2012Dialect";
        }

        @Override
        public DataSource dataSource() {
            SQLServerDataSource dataSource = new SQLServerDataSource();
            dataSource.setURL("jdbc:sqlserver://localhost;instance=SQLEXPRESS;databaseName=high_performance_java_persistence;user=sa;password=adm1n");
            return dataSource;
        }

        @Override
        public Class<? extends DataSource> dataSourceClassName() {
            return SQLServerDataSource.class;
        }

        @Override
        public Properties dataSourceProperties() {
            Properties properties = new Properties();
            properties.setProperty("URL", "jdbc:sqlserver://localhost;instance=SQLEXPRESS;databaseName=high_performance_java_persistence;user=sa;password=adm1n");
            return properties;
        }

        @Override
        public List<IdentifierStrategy> identifierStrategies() {
            return Arrays.asList(IdentifierStrategy.IDENTITY, IdentifierStrategy.SEQUENCE);
        }

        @Override
        public Database database() {
            return Database.SQLSERVER;
        }
    }

    public static class JTDSDataSourceProvider implements DataSourceProvider {
        @Override
        public String hibernateDialect() {
            return "org.hibernate.dialect.SQLServer2012Dialect";
        }

        @Override
        public DataSource dataSource() {
            JtdsDataSource dataSource = new JtdsDataSource();
            dataSource.setServerName("localhost");
            dataSource.setDatabaseName("high_performance_java_persistence");
            dataSource.setInstance("SQLEXPRESS");
            dataSource.setUser("sa");
            dataSource.setPassword("adm1n");
            return dataSource;
        }

        @Override
        public Class<? extends DataSource> dataSourceClassName() {
            return JtdsDataSource.class;
        }

        @Override
        public Properties dataSourceProperties() {
            Properties properties = new Properties();
            properties.setProperty("databaseName", "high_performance_java_persistence");
            properties.setProperty("serverName", "localhost");
            properties.setProperty("instance", "SQLEXPRESS");
            properties.setProperty("user", "sa");
            properties.setProperty("password", "adm1n");
            return properties;
        }

        @Override
        public List<IdentifierStrategy> identifierStrategies() {
            return Arrays.asList(IdentifierStrategy.IDENTITY, IdentifierStrategy.SEQUENCE);
        }

        @Override
        public Database database() {
            return Database.SQLSERVER;
        }
    }


    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    @FunctionalInterface
    protected interface VoidCallable extends Callable<Void> {

        void execute();

        default Void call() throws Exception {
            execute();
            return null;
        }
    }

    @FunctionalInterface
    protected interface HibernateTransactionFunction<T> extends Function<Session, T> {
        default void beforeTransactionCompletion() {

        }

        default void afterTransactionCompletion() {

        }
    }

    @FunctionalInterface
    protected interface HibernateTransactionConsumer extends Consumer<Session> {
        default void beforeTransactionCompletion() {

        }

        default void afterTransactionCompletion() {

        }
    }

    @FunctionalInterface
    protected interface JPATransactionFunction<T> extends Function<EntityManager, T> {
        default void beforeTransactionCompletion() {

        }

        default void afterTransactionCompletion() {

        }
    }

    @FunctionalInterface
    protected interface JPATransactionVoidFunction extends Consumer<EntityManager> {
        default void beforeTransactionCompletion() {

        }

        default void afterTransactionCompletion() {

        }
    }

    @FunctionalInterface
    protected interface ConnectionCallable<T> {
        T execute(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    protected interface ConnectionVoidCallable {
        void execute(Connection connection) throws SQLException;
    }

    private EntityManagerFactory emf;
    private SessionFactory sf;

    @Before
    public void init() {
        if( nativeHibernateSessionFactoryBootstrap()) {
            sf = newSessionFactory();
        } else {
            emf = newEntityManagerFactory();
        }
    }

    @After
    public void destroy() {
        if( nativeHibernateSessionFactoryBootstrap()) {
            sf.close();
        } else {
            emf.close();
        }
    }

    public EntityManagerFactory getEntityManagerFactory() {
        return emf;
    }

    public SessionFactory getSessionFactory() {
        return nativeHibernateSessionFactoryBootstrap() ? sf : emf.unwrap( SessionFactory.class);
    }
    protected boolean nativeHibernateSessionFactoryBootstrap() {
        return true;
    }

    protected abstract Class<?>[] entities();

    protected List<String> entityClassNames() {
        return Arrays.asList(entities()).stream().map(Class::getName).collect(Collectors.toList());
    }

    protected String[] packages() {
        return null;
    }

    protected Interceptor interceptor() {
        return null;
    }

    private SessionFactory newSessionFactory() {
        Properties properties = getProperties();
        Configuration configuration = new Configuration().addProperties(properties);
        for(Class<?> entityClass : entities()) {
            configuration.addAnnotatedClass(entityClass);
        }
        String[] packages = packages();
        if(packages != null) {
            for(String scannedPackage : packages) {
                configuration.addPackage(scannedPackage);
            }
        }
        Interceptor interceptor = interceptor();
        if(interceptor != null) {
            configuration.setInterceptor(interceptor);
        }
        return configuration.buildSessionFactory(
                new StandardServiceRegistryBuilder()
                        .applySettings(properties)
                        .build()
        );
    }

    protected EntityManagerFactory newEntityManagerFactory() {
        PersistenceUnitInfo persistenceUnitInfo = new PersistenceUnitInfoImpl(
            getClass().getSimpleName(), entityClassNames(), getProperties()
        );

        Map<String, Object> configuration = new HashMap<>();
        configuration.put(org.hibernate.jpa.AvailableSettings.INTERCEPTOR, interceptor());
        EntityManagerFactoryBuilderImpl entityManagerFactoryBuilder = new EntityManagerFactoryBuilderImpl(
            new PersistenceUnitInfoDescriptor(persistenceUnitInfo), configuration
        );
        return entityManagerFactoryBuilder.build();
    }

    protected Properties getProperties() {
        Properties properties = new Properties();
        properties.put("hibernate.dialect", getDataSourceProvider().hibernateDialect());
        properties.put("hibernate.hbm2ddl.auto", "create-drop");
        //log settings
        //properties.put("hibernate.show_sql", Boolean.TRUE.toString());
        //properties.put("hibernate.format_sql", Boolean.TRUE.toString());
        //properties.put("hibernate.use_sql_coments", Boolean.FALSE.toString());
        properties.put("hibernate.generate_statistics", Boolean.TRUE.toString());
        
        //data source settings
        properties.put("hibernate.connection.datasource", newDataSource());
        return properties;
    }

    protected DataSource newDataSource() {
        if (proxyDataSource()) {
            ChainListener listener = new ChainListener();
            SLF4JQueryLoggingListener loggingListener = new SLF4JQueryLoggingListener();
            loggingListener.setQueryLogEntryCreator(new AbstractTest.InlineQueryLogEntryCreator());
            listener.addListener(loggingListener);
            return ProxyDataSourceBuilder
                    .create(getDataSourceProvider().dataSource())
                    .name(getClass().getName())
                    .listener(listener)
                    .build();
        } else {
            return getDataSourceProvider().dataSource();
        }
    }

    protected boolean proxyDataSource() {
        return true;
    }

    protected DataSourceProvider getDataSourceProvider() {
        return new HsqldbDataSourceProvider();
    }

    protected <T> T doInTransaction(HibernateTransactionFunction<T> callable) {
        T result = null;
        Session session = null;
        Transaction txn = null;
        try {
            session = getSessionFactory().openSession();
            callable.beforeTransactionCompletion();
            txn = session.beginTransaction();

            result = callable.apply(session);
            txn.commit();
        } catch (RuntimeException e) {
            if ( txn != null && txn.isActive() ) txn.rollback();
            throw e;
        } finally {
            callable.afterTransactionCompletion();
            if (session != null) {
                session.close();
            }
        }
        return result;
    }

    protected void doInTransaction(HibernateTransactionConsumer callable) {
        Session session = null;
        Transaction txn = null;
        try {
            session = getSessionFactory().openSession();
            callable.beforeTransactionCompletion();
            txn = session.beginTransaction();

            callable.accept(session);
            txn.commit();
        } catch (RuntimeException e) {
            if ( txn != null && txn.isActive() ) txn.rollback();
            throw e;
        } finally {
            callable.afterTransactionCompletion();
            if (session != null) {
                session.close();
            }
        }
    }

    protected <T> T doInJPA(JPATransactionFunction<T> function) {
        T result = null;
        EntityManager entityManager = null;
        EntityTransaction txn = null;
        try {
            entityManager = emf.createEntityManager();
            function.beforeTransactionCompletion();
            txn = entityManager.getTransaction();
            txn.begin();
            result = function.apply(entityManager);
            txn.commit();
        } catch (RuntimeException e) {
            if ( txn != null && txn.isActive()) txn.rollback();
            throw e;
        } finally {
            function.afterTransactionCompletion();
            if (entityManager != null) {
                entityManager.close();
            }
        }
        return result;
    }

    protected void doInJPA(JPATransactionVoidFunction function) {
        EntityManager entityManager = null;
        EntityTransaction txn = null;
        try {
            entityManager = emf.createEntityManager();
            function.beforeTransactionCompletion();
            txn = entityManager.getTransaction();
            txn.begin();
            function.accept(entityManager);
            txn.commit();
        } catch (RuntimeException e) {
            if ( txn != null && txn.isActive()) txn.rollback();
            throw e;
        } finally {
            function.afterTransactionCompletion();
            if (entityManager != null) {
                entityManager.close();
            }
        }
    }

    protected <T> T doInJDBC(ConnectionCallable<T> callable) {
        AtomicReference<T> result = new AtomicReference<>();
        Session session = null;
        Transaction txn = null;
        try {
            session = getSessionFactory().openSession();
            txn = session.beginTransaction();
            txn.commit();
        } catch (RuntimeException e) {
            if ( txn != null && txn.isActive() ) txn.rollback();
            throw e;
        } finally {
            if (session != null) {
                session.close();
            }
        }
        return result.get();
    }

    protected void doInJDBC(ConnectionVoidCallable callable) {
        Session session = null;
        Transaction txn = null;
        try {
            session = getSessionFactory().openSession();
            txn = session.beginTransaction();
            session.doWork(callable::execute);
            txn.commit();
        } catch (RuntimeException e) {
            if ( txn != null && txn.isActive() ) txn.rollback();
            throw e;
        } finally {
            if (session != null) {
                session.close();
            }
        }
    }

    protected void executeSync(VoidCallable callable) {
        executeSync(Collections.singleton(callable));
    }

    protected void executeSync(Collection<VoidCallable> callables) {
        try {
            List<Future<Void>> futures = executorService.invokeAll(callables);
            for (Future<Void> future : futures) {
                future.get();
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    protected <T> void executeAsync(Runnable callable, final Runnable completionCallback) {
        final Future future = executorService.submit(callable);
    }

    protected Future<?> executeAsync(Runnable callable) {
        return executorService.submit(callable);
    }

    protected  void transact(Consumer<Connection> callback) {
        transact(callback, null);
    }

    protected  void transact(Consumer<Connection> callback, Consumer<Connection> before) {
        Connection connection = null;
        try {
            connection = newDataSource().getConnection();
            if (before != null) {
                before.accept(connection);
            }
            connection.setAutoCommit(false);
            callback.accept(connection);
            connection.commit();
        } catch (Exception e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException ex) {
                    throw new DataAccessException(e);
                }
            }
            throw (e instanceof DataAccessException ?
                    (DataAccessException) e : new DataAccessException(e));
        } finally {
            if(connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    throw new DataAccessException(e);
                }
            }
        }
    }

    protected LockType lockType() {
        return LockType.LOCKS;
    }

    protected void awaitOnLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    protected void sleep(int millis) {
        sleep(millis, null);
    }

    protected <V> V sleep(int millis, Callable<V> callable) {
        V result = null;
        try {
            //LOGGER.debug("Wait {} ms!", millis);
            if (callable != null) {
                result = callable.call();
            }
            Thread.sleep(millis);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return result;
    }

    protected String selectStringColumn(Connection connection, String sql) {
        try {
            try(Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(1);
                ResultSet resultSet = statement.executeQuery(sql);
                if(!resultSet.next()) {
                    throw new IllegalArgumentException("There was no row to be selected!");
                }
                return resultSet.getString(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    protected int update(Connection connection, String sql) {
        try {
            try(Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(1);
                return statement.executeUpdate(sql);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    protected int update(Connection connection, String sql, Object[] params) {
        try {
            try(PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(1);
                for (int i = 0; i < params.length; i++) {
                    statement.setObject(i + 1, params[i]);
                }
                return statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    protected int count(Connection connection, String sql) {
        try {
            try(Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(1);
                ResultSet resultSet = statement.executeQuery(sql);
                if(!resultSet.next()) {
                    throw new IllegalArgumentException("There was no row to be selected!");
                }
                return ((Number) resultSet.getObject(1)).intValue();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    protected void printEntityCacheStats(String region, boolean printEntries) {
		SecondLevelCacheStatistics stats = getCacheStats(region);
		LOGGER.info(region + " Stats:  \n\n\t" + stats + "\n");
		if (printEntries) {
			@SuppressWarnings("rawtypes")
			Map cacheEntries = stats.getEntries();
			LOGGER.info(Arrays.toString(cacheEntries.entrySet().toArray()));
		}
	}
	
	protected void printEntityCacheStats(String region) {
		printEntityCacheStats(region, false);
	}
	
	protected void printQueryCacheStats(String region) {
		SecondLevelCacheStatistics stats = getCacheStats(region);
		LOGGER.info(region + " Stats:  \n\n\t" + stats + "\n");
	}

	protected SecondLevelCacheStatistics getCacheStats(String region) {
		SecondLevelCacheStatistics stats = getSessionFactory().getStatistics().getSecondLevelCacheStatistics(region);
		if (stats == null){
			LOGGER.warn("No such cache:  " + region);
		}
		return stats;
	}
    
	protected void print2LCRegionNames(){
		String[] arr = getSessionFactory().getStatistics().getSecondLevelCacheRegionNames();

		LOGGER.info("2LC Region names:");
		for (String rn : arr) {
			LOGGER.info("\t --->" + rn);
		}		
	}

    public static class InlineQueryLogEntryCreator extends
            DefaultQueryLogEntryCreator {
        @Override
        protected void writeParamsEntry(StringBuilder sb, ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
            sb.append("Params:[");
            for (QueryInfo queryInfo : queryInfoList) {
                boolean firstArg = true;
                for (Map<String, Object> paramMap : queryInfo.getQueryArgsList()) {

                    if(!firstArg) {
                        sb.append(", ");
                    } else {
                        firstArg = false;
                    }

                    SortedMap<String, Object> sortedParamMap = new TreeMap<>( new StringAsIntegerComparator());
                    sortedParamMap.putAll(paramMap);

                    sb.append("(");
                    boolean firstParam = true;
                    for (Map.Entry<String, Object> paramEntry : sortedParamMap.entrySet()) {
                        if(!firstParam) {
                            sb.append(", ");
                        } else {
                            firstParam = false;
                        }
                        Object parameter = paramEntry.getValue();
                        if(parameter != null && parameter.getClass().isArray()) {
                            sb.append(arrayToString(parameter));
                        } else {
                            sb.append(parameter);
                        }
                    }
                    sb.append(")");
                }
            }
            sb.append("]");
        }

        private String arrayToString(Object object) {
            if(object.getClass().isArray()) {
                if(object instanceof byte[]) {
                    return Arrays.toString((byte []) object);
                }
                if(object instanceof short[]) {
                    return Arrays.toString((short []) object);
                }
                if(object instanceof char[]) {
                    return Arrays.toString((char []) object);
                }
                if(object instanceof int[]) {
                    return Arrays.toString((int []) object);
                }
                if(object instanceof long[]) {
                    return Arrays.toString((long []) object);
                }
                if(object instanceof float[]) {
                    return Arrays.toString((float []) object);
                }
                if(object instanceof double[]) {
                    return Arrays.toString((double []) object);
                }
                if(object instanceof boolean[]) {
                    return Arrays.toString((boolean []) object);
                }
                if(object instanceof Object[]) {
                    return Arrays.toString((Object []) object);
                }
            }
            throw new UnsupportedOperationException("Arrat type not supported: " + object.getClass());
        }
    };
}
