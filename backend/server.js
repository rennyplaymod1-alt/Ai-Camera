'use strict';

require('dotenv').config();
const express = require('express');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const multer = require('multer');
const {
  MAX_UPLOAD_BYTES,
  ALLOWED_SIZES,
  detectImageType,
  validateEnhanceRequest
} = require('./lib/validate');

const config = {
  apiKey: process.env.OPENAI_API_KEY || '',
  model: process.env.OPENAI_IMAGE_MODEL || 'gpt-image-1.5',
  size: process.env.OPENAI_IMAGE_SIZE || 'auto',
  quality: process.env.OPENAI_IMAGE_QUALITY || 'medium',
  timeoutMs: Number(process.env.OPENAI_TIMEOUT_MS || 120000),
  dailyLimit: Number(process.env.DAILY_REQUEST_LIMIT || 200),
  port: Number(process.env.PORT || 3000)
};

if (!config.apiKey || config.apiKey.startsWith('sk-REEMPLAZAR')) {
  console.error('Falta OPENAI_API_KEY. Copia .env.example a .env y configúrala.');
  process.exit(1);
}
if (!ALLOWED_SIZES.includes(config.size)) {
  console.error(`OPENAI_IMAGE_SIZE no válido. Usa uno de: ${ALLOWED_SIZES.join(', ')}`);
  process.exit(1);
}

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

// Presupuesto diario en memoria. Se reinicia al reiniciar el servidor; para producción usa Redis o similar.
const daily = { day: '', count: 0 };
function consumeDailyBudget() {
  const today = new Date().toISOString().slice(0, 10);
  if (daily.day !== today) {
    daily.day = today;
    daily.count = 0;
  }
  if (daily.count >= config.dailyLimit) return false;
  daily.count += 1;
  return true;
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const EXT = { 'image/jpeg': 'jpg', 'image/png': 'png', 'image/webp': 'webp' };

/** Llama a POST /v1/images/edits. Un único reintento y timeout explícito. */
async function callOpenAIEdit(imageBuffer, mime, prompt) {
  for (let attempt = 0; attempt < 2; attempt++) {
    const form = new FormData();
    form.append('model', config.model);
    form.append('prompt', prompt);
    form.append('size', config.size);
    form.append('quality', config.quality);
    form.append('output_format', 'jpeg');
    form.append('output_compression', '90');
    form.append('n', '1');
    form.append('image', new Blob([imageBuffer], { type: mime }), `input.${EXT[mime]}`);

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), config.timeoutMs);
    try {
      const response = await fetch('https://api.openai.com/v1/images/edits', {
        method: 'POST',
        headers: { Authorization: `Bearer ${config.apiKey}` },
        body: form,
        signal: controller.signal
      });

      if (!response.ok) {
        console.error('OpenAI respondió con estado', response.status);
        const retryable = response.status === 429 || response.status >= 500;
        if (retryable && attempt === 0) {
          await sleep(2000);
          continue;
        }
        if (response.status === 429) throw new HttpError(503, 'El servicio de IA está saturado. Inténtalo más tarde.');
        if (response.status === 400) throw new HttpError(422, 'El servicio de IA no pudo procesar esta imagen o instrucción.');
        throw new HttpError(502, 'El servicio de IA devolvió un error.');
      }

      const json = await response.json();
      const b64 = json && json.data && json.data[0] && json.data[0].b64_json;
      if (!b64) throw new HttpError(502, 'Respuesta no válida del servicio de IA.');
      return { base64: b64, mimeType: 'image/jpeg' };
    } catch (err) {
      if (err instanceof HttpError) throw err;
      if (attempt === 0) {
        await sleep(2000);
        continue;
      }
      throw new HttpError(504, 'El servicio de IA tardó demasiado o no responde.');
    } finally {
      clearTimeout(timer);
    }
  }
  throw new HttpError(502, 'El servicio de IA no respondió.');
}

function sendError(res, err) {
  if (err instanceof HttpError) {
    return res.status(err.status).json({ error: { message: err.message } });
  }
  // No registrar cuerpos de imagen ni la clave: solo el tipo de error.
  console.error('Error interno:', err && err.name, err && err.message);
  return res.status(500).json({ error: { message: 'Error interno del servidor.' } });
}

const app = express();
app.disable('x-powered-by');
if (process.env.TRUST_PROXY === '1') app.set('trust proxy', 1);
app.use(helmet());

app.get('/health', (req, res) => res.json({ ok: true }));

const enhanceLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  limit: 10,
  standardHeaders: 'draft-7',
  legacyHeaders: false,
  message: { error: { message: 'Demasiadas solicitudes. Espera unos minutos.' } }
});

const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: MAX_UPLOAD_BYTES, files: 1, fields: 5, fieldSize: 4096 }
});

app.post(
  '/api/enhance',
  enhanceLimiter,
  (req, res, next) => {
    upload.single('image')(req, res, (err) => {
      if (err) {
        if (err.code === 'LIMIT_FILE_SIZE') {
          return res.status(413).json({ error: { message: 'La imagen supera el tamaño máximo (15 MB).' } });
        }
        return res.status(400).json({ error: { message: 'Solicitud no válida.' } });
      }
      next();
    });
  },
  async (req, res) => {
    try {
      if (!req.file) throw new HttpError(400, 'Falta la imagen.');
      const mime = detectImageType(req.file.buffer);
      if (!mime) throw new HttpError(415, 'Solo se admiten imágenes JPEG, PNG o WEBP.');

      const validated = validateEnhanceRequest(req.body || {});
      if (!validated.ok) throw new HttpError(400, validated.error);

      if (!consumeDailyBudget()) {
        throw new HttpError(429, 'Se ha alcanzado el límite diario del servicio. Inténtalo mañana.');
      }

      const result = await callOpenAIEdit(req.file.buffer, mime, validated.prompt);
      res.json({ imageBase64: result.base64, mimeType: result.mimeType, model: config.model });
    } catch (err) {
      sendError(res, err);
    }
  }
);

app.use((req, res) => res.status(404).json({ error: { message: 'No encontrado.' } }));
app.use((err, req, res, next) => sendError(res, err));

app.listen(config.port, () => {
  console.log(`AI Camera Ultra backend escuchando en el puerto ${config.port}`);
});
