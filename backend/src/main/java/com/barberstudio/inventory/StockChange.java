package com.barberstudio.inventory;

/** Pedido de cambio de stock: delta positivo entra, negativo sale. saleId/orderId indican el origen. */
public record StockChange(long shopId, long productId, int delta, MovementReason reason,
                          Long userId, Long saleId, Long orderId, String note) {}
