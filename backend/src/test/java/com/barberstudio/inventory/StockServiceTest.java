package com.barberstudio.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.barberstudio.common.error.BusinessRuleException;
import com.barberstudio.common.error.NotFoundException;
import com.barberstudio.support.IntegrationTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class StockServiceTest extends IntegrationTestBase {

    @Autowired StockService stock;
    @Autowired ProductRepository products;
    TransactionTemplate tx;

    @Autowired
    void setTx(PlatformTransactionManager tm) { tx = new TransactionTemplate(tm); }

    long shopA, shopB, product;

    @BeforeEach
    void setUp() {
        truncateAll();
        shopA = seedShop("shop-a");
        shopB = seedShop("shop-b");
        product = jdbc.queryForObject(
            "INSERT INTO product (barbershop_id, name, price, stock) VALUES (?, 'Cera mate', 250, 1) RETURNING id",
            Long.class, shopA);
    }

    private int stockOf(long id) {
        return jdbc.queryForObject("SELECT stock FROM product WHERE id = ?", Integer.class, id);
    }

    private int movements(long id) {
        return jdbc.queryForObject("SELECT count(*) FROM stock_movement WHERE product_id = ?", Integer.class, id);
    }

    private StockChange change(long shop, int delta, MovementReason reason) {
        return new StockChange(shop, product, delta, reason, null, null, null, null);
    }

    @Test
    void moveUpdatesStockAndRecordsMovement() {
        StockMovement m = stock.move(change(shopA, 5, MovementReason.PURCHASE));

        assertThat(stockOf(product)).isEqualTo(6);
        assertThat(movements(product)).isEqualTo(1);
        assertThat(m.getDelta()).isEqualTo(5);
        assertThat(m.getReason()).isEqualTo(MovementReason.PURCHASE);
        assertThat(jdbc.queryForObject("SELECT reason FROM stock_movement WHERE product_id = ?", String.class, product))
            .isEqualTo("PURCHASE");
    }

    @Test
    void moveRejectsNegativeStockAndChangesNothing() {
        assertThatThrownBy(() -> stock.move(change(shopA, -2, MovementReason.ADJUSTMENT)))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessage("Stock insuficiente: quedan 1.");

        assertThat(stockOf(product)).isEqualTo(1);
        assertThat(movements(product)).isZero();
    }

    @Test
    void moveFromOtherShopIsNotFound() {
        assertThatThrownBy(() -> stock.move(change(shopB, 1, MovementReason.PURCHASE)))
            .isInstanceOf(NotFoundException.class);
        assertThat(stockOf(product)).isEqualTo(1);
    }

    @Test
    void zeroDeltaIsRejected() {
        assertThatThrownBy(() -> stock.move(change(shopA, 0, MovementReason.ADJUSTMENT)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(movements(product)).isZero();
    }

    @Test
    void editingProductDoesNotOverwriteConcurrentStockChange() throws Exception {
        ExecutorService other = Executors.newSingleThreadExecutor();
        tx.executeWithoutResult(s -> {
            Product p = products.findById(product).orElseThrow();      // lee stock = 1
            try {
                other.submit(() -> stock.move(change(shopA, 4, MovementReason.PURCHASE))).get(30, TimeUnit.SECONDS);
            } catch (Exception e) { throw new IllegalStateException(e); }
            p.setName("Cera mate premium");                              // edición de datos, no de stock
            products.saveAndFlush(p);
        });
        other.shutdown();

        assertThat(stockOf(product)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT 1 + sum(delta) FROM stock_movement WHERE product_id = ?", Integer.class, product))
            .isEqualTo(stockOf(product));
    }

    @Test
    void concurrentSalesOfLastUnitOnlyOneSucceeds() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    stock.move(change(shopA, -1, MovementReason.SALE));
                    return true;
                } catch (BusinessRuleException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        int ok = 0;
        for (Future<Boolean> f : results) if (f.get(30, TimeUnit.SECONDS)) ok++;
        pool.shutdown();

        assertThat(ok).isEqualTo(1);
        assertThat(stockOf(product)).isZero();
        assertThat(movements(product)).isEqualTo(1);
    }
}
