package com.vladmihalcea.hibernate.masterclass.laboratory.cache;

import com.vladmihalcea.hibernate.masterclass.laboratory.util.AbstractTest;
import org.hibernate.ObjectNotFoundException;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.jdbc.Work;
import org.junit.Before;
import org.junit.Test;

import javax.persistence.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


/**
 * CollectionCacheTest - Test to check Collection Cache
 *
 * @author Vlad Mihalcea
 */
public class CollectionCacheTest extends AbstractTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[] {
                Repository.class,
                Commit.class
        };
    }

    @Override
    protected Properties getProperties() {
        Properties properties = super.getProperties();
        properties.put("hibernate.cache.use_second_level_cache", Boolean.TRUE.toString());
        properties.put("hibernate.cache.region.factory_class", "org.hibernate.cache.ehcache.EhCacheRegionFactory");
        return properties;
    }

    @Before
    public void init() {
        super.init();
        doInTransaction(session -> {
            Repository repository = new Repository("Hibernate-Master-Class");
            session.persist(repository);

            Commit commit1 = new Commit();
            commit1.getChanges().add(new Change("README.txt", "0a1,5..."));
            commit1.getChanges().add(new Change("web.xml", "17c17..."));

            Commit commit2 = new Commit();
            commit2.getChanges().add(new Change("README.txt", "0b2,5..."));

            repository.addCommit(commit1);
            repository.addCommit(commit2);
            session.persist(commit1);
        });
        doInTransaction(session -> {
            LOGGER.info("Load collections for the first time");
            Repository repository = (Repository) session.get(Repository.class, 1L);
            for (Commit commit : repository.getCommits()) {
                assertFalse(commit.getChanges().isEmpty());
            }
        });
    }

    @Test
    public void testLoadFromCollectionCache() {
        LOGGER.info("Load collections from cache");
        doInTransaction(session -> {
            Repository repository = (Repository) session.get(Repository.class, 1L);
            assertEquals(2, repository.getCommits().size());
        });
    }

    @Test
    public void testInvalidateEntityCollectionCacheOnAddingEntries() {
        LOGGER.info("Adding invalidates Collection Cache");
        doInTransaction(session -> {
            Repository repository = (Repository)
                    session.get(Repository.class, 1L);
            assertEquals(2, repository.getCommits().size());

            Commit commit = new Commit();
            commit.getChanges().add(
                    new Change("Main.java", "0b3,17...")
            );
            repository.addCommit(commit);
        });
        doInTransaction(session -> {
            Repository repository = (Repository)
                session.get(Repository.class, 1L);
            assertEquals(3, repository.getCommits().size());
        });
    }

    @Test
    public void testInvalidateEntityCollectionCacheOnRemovingEntries() {
        LOGGER.info("Removing invalidates Collection Cache");
        doInTransaction(session -> {
            Repository repository = (Repository)
                session.get(Repository.class, 1L);
            assertEquals(2, repository.getCommits().size());
            Commit removable = repository.getCommits().get(0);
            repository.removeCommit(removable);
        });
        doInTransaction(session -> {
            Repository repository = (Repository)
                session.get(Repository.class, 1L);
            assertEquals(1, repository.getCommits().size());
        });
    }

    @Test
    public void testConsistencyIssuesWhenRemovingChildDirectly() {
        LOGGER.info("Removing Child causes inconsistencies");
        doInTransaction(session -> {
            Commit commit = (Commit) session.get(Commit.class, 1L);
            session.delete(commit);
        });
        try {
            doInTransaction(session -> {
                Repository repository = (Repository) session.get(Repository.class, 1L);
                assertEquals(1, repository.getCommits().size());
            });
        } catch (ObjectNotFoundException e) {
            LOGGER.warn("Object not found", e);
        }
    }

    @Test
    public void testConsistencyWhenHQLUpdating() {
        LOGGER.info("Updating Child entities using HQL");
        doInTransaction(session -> {
            Repository repository = (Repository)
                 session.get(Repository.class, 1L);
            for (Commit commit : repository.getCommits()) {
                assertFalse(commit.review);
            }
        });
        doInTransaction(session -> {
            session.createQuery(
                    "update Commit c " +
                            "set c.review = true ")
                    .executeUpdate();
        });
        doInTransaction(session -> {
            Repository repository = (Repository)
                session.get(Repository.class, 1L);
            for(Commit commit : repository.getCommits()) {
                assertTrue(commit.review);
            }
        });
    }

    @Test
    public void testConsistencyWhenSQLUpdating() {
        LOGGER.info("Updating Child entities using SQL");
        doInTransaction(session -> {
            Repository repository = (Repository)
                session.get(Repository.class, 1L);
            for (Commit commit : repository.getCommits()) {
                assertFalse(commit.review);
            }
        });
        doInTransaction(session -> {
            session.createSQLQuery(
                "update Commit c " +
                "set c.review = true ")
                    .addSynchronizedEntityClass(Commit.class)
            .executeUpdate();
        });
        doInTransaction(session -> {
            Repository repository = (Repository)
                session.get(Repository.class, 1L);
            for(Commit commit : repository.getCommits()) {
                assertTrue(commit.review);
            }
        });
    }

    @Test
    public void testConsistencyWhenManuallySQLUpdating() {
        LOGGER.info("Manually updating Child entities using SQL");
        final Repository repository = doInTransaction(session -> {
            Repository _repository = (Repository)
                    session.get(Repository.class, 1L);
            for (Commit commit : _repository.getCommits()) {
                assertFalse(commit.review);
            }
            return _repository;
        });
        doInTransaction(session -> {
            session.doWork(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "update Commit c " +
                                "set c.review = true "
                )) {
                    statement.executeUpdate();
                }
            });
            session.getSessionFactory().getCache().evictCollection(
                Repository.class.getName() + ".commits",
                repository.getId()
            );
        });
        doInTransaction(session -> {
            Repository _repository = (Repository)
                    session.get(Repository.class, 1L);
            for(Commit commit : _repository.getCommits()) {
                assertTrue(commit.review);
            }
        });
    }

    @Test
    public void testInvalidateEmbeddableCollectionCacheOnRemovingEntries() {
        LOGGER.info("Invalidate embeddable collection cache on removing entries");
        doInTransaction(session -> {
            Commit commit = (Commit) session.get(Commit.class, 1L);
            assertEquals(2, commit.getChanges().size());
            commit.getChanges().remove(0);
        });
        doInTransaction(session -> {
            Commit commit = (Commit) session.get(Commit.class, 1L);
            assertEquals(1, commit.getChanges().size());
        });
    }

    @Test
    public void testInvalidateEmbeddableCollectionCacheOnAddingEntries() {
        LOGGER.info("Invalidate embeddable collection cache on adding entries");
        doInTransaction(session -> {
            Commit commit = (Commit) session.get(Commit.class, 1L);
            assertEquals(2, commit.getChanges().size());
            commit.getChanges().add(new Change("Main.java", "0b3,17..."));
        });
        doInTransaction(session -> {
            Commit commit = (Commit) session.get(Commit.class, 1L);
            assertEquals(3, commit.getChanges().size());
        });
    }

    /**
     * Repository - Repository
     *
     * @author Vlad Mihalcea
     */
    @Entity(name = "Repository")
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    public static class Repository {

        @Id
        @GeneratedValue(strategy = GenerationType.AUTO)
        private Long id;

        private String name;

        @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
        @OneToMany(mappedBy = "repository", cascade = CascadeType.ALL, orphanRemoval = true)
        private List<Commit> commits = new ArrayList<>();

        public Repository() {
        }

        public Repository(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public List<Commit> getCommits() {
            return commits;
        }

        public void addCommit(Commit commit) {
            commits.add(commit);
            commit.setRepository(this);
        }

        public void removeCommit(Commit commit) {
            commits.remove(commit);
            commit.setRepository(null);
        }
    }

    /**
     * Commit - Commit
     *
     * @author Vlad Mihalcea
     */
    @Entity(name = "Commit")
    @Table(name = "commit")
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    public static class Commit {

        @Id
        @GeneratedValue(strategy = GenerationType.AUTO)
        private Long id;

        private boolean review;

        @ManyToOne(fetch = FetchType.LAZY)
        private Repository repository;

        @ElementCollection
        @CollectionTable(
                name="commit_change",
                joinColumns=@JoinColumn(name="commit_id")
        )
        @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
        @OrderColumn(name = "index_id")
        private List<Change> changes = new ArrayList<>();

        public Commit() {
        }

        public Repository getRepository() {
            return repository;
        }

        public void setRepository(Repository repository) {
            this.repository = repository;
        }

        public List<Change> getChanges() {
            return changes;
        }
    }

    /**
     * Change - Change
     *
     * @author Vlad Mihalcea
     */
    @Embeddable
    public static class Change {

        @Column(name = "path", nullable = false)
        private String path;

        @Column(name = "diff", nullable = false)
        private String diff;

        public Change() {
        }

        public Change(String path, String diff) {
            this.path = path;
            this.diff = diff;
        }

        public String getPath() {
            return path;
        }

        public String getDiff() {
            return diff;
        }
    }
}
