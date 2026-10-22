// Configuración del sitio. En producción el navegador habla con el mismo dominio (Netlify reenvía /api al backend).
window.CFG = {
  slug: 'jesuss-sk8',                      // identificador de tu barbería (se crea con BOOTSTRAP_SHOP_SLUG o el nombre)
  devSlug: 'barber-studio-demo',           // barbería de demostración al correr en local (perfil dev)
  devApi: 'http://localhost:8080',         // backend local
  instagram: 'https://www.instagram.com/jesuss.sk8/'
};
