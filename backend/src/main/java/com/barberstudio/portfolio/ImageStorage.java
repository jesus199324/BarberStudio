package com.barberstudio.portfolio;

/** Abstraccion del almacenamiento: disco local en desarrollo; un proveedor externo (Cloudinary/S3/R2) en produccion. */
public interface ImageStorage {
    record StoredImage(String key, String url) {}

    StoredImage store(long shopId, byte[] bytes, String extension);

    void delete(String key);
}
