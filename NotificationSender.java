package com.barberstudio.notification;

/** Canal de salida de los avisos (hoy: email con Resend). Permite cambiarlo o simularlo en pruebas. */
public interface NotificationSender {
    /** @param idempotencyKey misma clave = el proveedor no entrega dos veces el mismo aviso aunque se reintente */
    void send(String subject, String text, String html, String idempotencyKey) throws Exception;

    /** true si el canal esta configurado; si no, el aviso se omite sin error. */
    boolean enabled();
}
