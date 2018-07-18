package com.vladmihalcea.hibernate.masterclass.laboratory.concurrency;

import com.vladmihalcea.hibernate.masterclass.laboratory.util.AbstractTest;
import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.Session;
import org.hibernate.dialect.lock.OptimisticEntityLockException;
import org.junit.Before;
import org.junit.Test;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.fail;

/**
 * AbstractLockModeOptimisticTest - Base Test to check LockMode.OPTIMISTIC
 *
 * @author Vlad Mihalcea
 */
public abstract class AbstractLockModeOptimisticTest extends AbstractTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[] {
            Product.class,
            OrderLine.class
        };
    }

    @Before
    public void init() {
        super.init();
        doInTransaction(session -> {
            Product product = new Product();
            product.setId(1L);
            product.setDescription("USB Flash Drive");
            product.setPrice(BigDecimal.valueOf(12.99));
            session.persist(product);
        });
    }

    /**
     * Product - Product
     *
     * @author Vlad Mihalcea
     */
    @Entity(name = "product")
    @Table(name = "product")
    public static class Product {

        @Id
        private Long id;

        private String description;

        private BigDecimal price;

        @Version
        private int version;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }
    }

    /**
     * OrderLine - Order Line
     *
     * @author Vlad Mihalcea
     */
    @Entity(name = "OrderLine")
    @Table(name = "order_line")
    public static class OrderLine {

        @Id
        @GeneratedValue(strategy = GenerationType.AUTO)
        private Long id;

        @ManyToOne
        private Product product;

        private BigDecimal unitPrice;

        @Version
        private int version;

        public OrderLine(Product product) {
            this.product = product;
            this.unitPrice = product.getPrice();
        }

        public Long getId() {
            return id;
        }

        public Product getProduct() {
            return product;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }
    }
}
