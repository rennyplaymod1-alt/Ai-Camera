# Backend de AI Camera Ultra

Servicio Node.js que guarda la clave de OpenAI y reenvía las fotos a `POST /v1/images/edits`.
La app Android nunca conoce la clave.

## Requisitos
- Node.js 20 o superior.
- Una clave de API de OpenAI con facturación activa.

## Instalación y prueba local
```bash
cd backend
npm install
cp .env.example .env      # edítalo y pon tu OPENAI_API_KEY
npm test                  # pruebas de validación (no llaman a OpenAI)
npm start                 # arranca en http://localhost:3000
curl http://localhost:3000/health
```

## Endpoint
`POST /api/enhance` · `multipart/form-data`
- `image`: archivo JPEG, PNG o WEBP, máximo 15 MB.
- `preset`: `light`, `color`, `denoise`, `sharpen`, `full` o `custom`.
- `instructions`: texto opcional, máximo 300 caracteres (obligatorio si `preset=custom`).

Respuesta `200`: `{ "imageBase64": "...", "mimeType": "image/jpeg", "model": "..." }`.
Errores: `{ "error": { "message": "..." } }` con código 400, 413, 415, 422, 429, 502, 503 o 504.

## Seguridad aplicada
- La clave solo existe en la variable de entorno del servidor.
- Validación del tipo real del archivo por cabecera, no por nombre.
- Límite de tamaño (15 MB) y de campos.
- Límite de peticiones por IP: 10 cada 15 minutos.
- Límite global diario (`DAILY_REQUEST_LIMIT`) para acotar el gasto.
- Prompts definidos en el servidor, con una guardia que mantiene la composición y las personas.
- Timeout explícito y un único reintento ante errores transitorios.
- Las imágenes no se registran en logs ni se guardan en disco.
- Cabeceras de seguridad con Helmet.

## Límites conocidos
- El límite diario y de peticiones está en memoria: se reinicia al reiniciar el servidor y no se comparte entre varias instancias. Para varias instancias, usa Redis.
- No hay autenticación de usuarios. Cualquiera con la dirección puede consumir cuota hasta el límite diario. Para publicarla, añade Firebase App Check o un inicio de sesión.
- El límite de gasto real debe configurarse en el panel de facturación de OpenAI (límites de uso o de gasto mensual). Hazlo siempre antes de publicar el servidor.
- Los tamaños y calidades aceptados (`OPENAI_IMAGE_SIZE`, `OPENAI_IMAGE_QUALITY`) deben comprobarse en la documentación vigente de OpenAI para el modelo elegido.
- La IA edita la imagen de forma generativa. No es superresolución: el servicio no garantiza más detalle real ni una resolución concreta.

## Despliegue con HTTPS
La app exige `https://` (salvo emulador). Opciones habituales:
1. **Render, Railway o Fly.io**: sube la carpeta `backend`, define las variables de `.env.example` en el panel y usa `npm start` como comando. Activa `TRUST_PROXY=1`.
2. **VPS propio**: ejecuta el servidor con `pm2` o `systemd` y pon delante Caddy o Nginx con un certificado (Let's Encrypt). Caddy gestiona el certificado automáticamente.

Después pega la dirección `https://tu-dominio` en Ajustes → Servidor de IA de la app.

## Prueba desde el teléfono
Un teléfono real no puede llegar a `localhost` de tu PC. Usa un túnel HTTPS temporal (por ejemplo, `cloudflared tunnel --url http://localhost:3000`) y pega la URL `https://` que te dé.
