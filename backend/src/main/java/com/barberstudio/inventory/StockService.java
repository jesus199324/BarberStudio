package com.barberstudio.inventory;

import com.barberstudio.common.error.BusinessRuleException;
import com.barberstudio.common.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Unico punto que modifica el stock: bloquea la fila, valida y deja registro en la misma transaccion. */
@Service
@RequiredArgsConstructor
public class StockService {
    private final ProductRepository products;
    private final StockMovementRepository movements;

    @Transactional
    public StockMovement move(StockChange c) {
        if (c.delta() == 0) throw new IllegalArgumentException("delta no puede ser 0");
        Product p = products.lockById(c.productId(), c.shopId())
            .orElseThrow(() -> new NotFoundException("Producto no encontrado."));
        int next = p.getStock() + c.delta();
        if (next < 0) throw new BusinessRuleException("Stock insuficiente: quedan " + p.getStock() + ".");
        p.setStock(next);

        StockMovement m = new StockMovement();
        m.setBarbershopId(c.shopId());
        m.setProductId(p.getId());
        m.setDelta(c.delta());
        m.setReason(c.reason());
        m.setUserId(c.userId());
        m.setSaleId(c.saleId());
        m.setOrderId(c.orderId());
        m.setNote(c.note());
        return movements.save(m);
    }
}
