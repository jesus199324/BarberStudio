package com.barberstudio.inventory;

/** Motivo de un cambio de stock. PURCHASE y ADJUSTMENT los carga el dueno; el resto los genera el sistema. */
public enum MovementReason { SALE, ORDER, PURCHASE, ADJUSTMENT, VOID, ORDER_CANCEL }
