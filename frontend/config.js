// Configuración del sitio. En producción el navegador habla con el mismo dominio (Netlify reenvía /api al backend).
window.CFG = {
  slug: 'jesuss-sk8',                      // identificador de tu barbería (se crea con BOOTSTRAP_SHOP_SLUG o el nombre)
  devSlug: 'barber-studio-demo',           // barbería de demostración al correr en local (perfil dev)
  devApi: 'http://localhost:8080',         // backend local
  instagram: 'https://www.instagram.com/jesuss.sk8/',
  defaultTheme: 'shelby',                  // tema para quien entra por primera vez: light | dark | street | shelby
  heroTitle: 'El estilo habla por vos.',   // titular de la portada (tema Shelby)
  heroSub: 'Cortes con carácter. Identidad propia. Cultura urbana.',
  tagline: "Gentlemen's Barber",            // bajo el nombre, en la portada
  footerTagline: 'Más que un corte, es una actitud.',
  heroLayout: 'centered',                  // 'centered' (logo al medio, como el tablero) o 'split' (texto a la izquierda, imagen a la derecha)
  // --- Experiencia cinematografica ---
  intro: true,                              // false = sin introduccion
  brandName: 'IKONOSTUDIO',
  descriptor: "GENTLEMEN'S BARBER",
  monogramUrl: '',                          // ej: 'img/monograma.png' (PNG con fondo transparente). Vacio = usa "IK" tipografico
  philosophy: 'Cada corte tiene su técnica. Cada detalle, su intención.',
  pillars: ['Estilo', 'Disciplina', 'Carácter', 'Barbería clásica'],
  experienceImage: '',                      // foto de ambiente propia; vacio = usa el fondo de portada si existe
  country: 'Uruguay'
};
