package com.vladmihalcea.hibernate.masterclass.laboratory.idgenerator;

import java.util.Properties;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

import org.hibernate.annotations.GenericGenerator;

import org.junit.Test;

import com.vladmihalcea.hibernate.masterclass.laboratory.util.AbstractTest;

public class IdentityVsSequenceIdentifierTest extends AbstractTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[] {
                IdentityIdentifier.class,
                SequenceIdentifier.class,
                TableSequenceIdentifier.class,
                AssignTableSequenceIdentifier.class
        };
    }

    @Override
    protected Properties getProperties() {
        Properties properties = super.getProperties();
        properties.put("hibernate.order_inserts", "true");
        properties.put("hibernate.order_updates", "true");
        properties.put("hibernate.jdbc.batch_size", "2");
        return properties;
    }

    @Test
    public void testIdentityIdentifierGenerator() {
        LOGGER.debug("testIdentityIdentifierGenerator");
        doInTransaction(session -> {
                for (int i = 0; i < 5; i++) {
                    session.persist(new IdentityIdentifier());
                }
                session.flush();
                return null;

        });
    }

    @Test
    public void testSequenceIdentifierGenerator() {
        LOGGER.debug("testSequenceIdentifierGenerator");
        doInTransaction(session -> {
                for (int i = 0; i < 5; i++) {
                    session.persist(new SequenceIdentifier());
                }
                session.flush();
                return null;

        });
    }

    @Test
    public void testTableSequenceIdentifierGenerator() {
        LOGGER.debug("testTableSequenceIdentifierGenerator");
        doInTransaction(session -> {
                for (int i = 0; i < 5; i++) {
                    session.persist(new TableSequenceIdentifier());
                }
                session.flush();
                return null;

        });
    }

    @Test
    public void testAssignTableSequenceIdentifierGenerator() {
        LOGGER.debug("testAssignTableSequenceIdentifierGenerator");
        doInTransaction(session -> {
            for (int i = 0; i < 5; i++) {
                session.persist(new AssignTableSequenceIdentifier());
            }
            AssignTableSequenceIdentifier tableSequenceIdentifier = new AssignTableSequenceIdentifier();
            tableSequenceIdentifier.id = -1L;
            session.merge(tableSequenceIdentifier);
            session.flush();
        });
    }

    @Entity(name = "identityIdentifier")
    public static class IdentityIdentifier {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
    }

    @Entity(name = "sequenceIdentifier")
    public static class SequenceIdentifier {

        @Id
        @GenericGenerator(name = "sequence", strategy = "sequence", parameters = {
            @org.hibernate.annotations.Parameter(name = "sequence", value = "sequence")
        })
        @GeneratedValue(generator = "sequence")
        private Long id;
    }

    @Entity(name = "tableIdentifier")
    public static class TableSequenceIdentifier {

        @Id
        @GenericGenerator(name = "table", strategy = "enhanced-table", parameters = {
                @org.hibernate.annotations.Parameter(name = "table_name", value = "sequence_table")
        })
        @GeneratedValue(generator = "table", strategy=GenerationType.TABLE)
        private Long id;
    }

    @Entity(name = "assigneTableIdentifier")
    public static class AssignTableSequenceIdentifier implements Identifiable<Long> {

        @Id
        @GenericGenerator(name = "table", strategy = "com.vladmihalcea.hibernate.masterclass.laboratory.idgenerator.AssignedTableGenerator",
            parameters = {
                @org.hibernate.annotations.Parameter(name = "table_name", value = "sequence_table")
        })
        @GeneratedValue(generator = "table", strategy=GenerationType.TABLE)
        private Long id;

        @Override
        public Long getId() {
            return id;
        }
    }

}
