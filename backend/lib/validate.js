'use strict';

const MAX_UPLOAD_BYTES = 15 * 1024 * 1024;   // 15 MB
const MAX_INSTRUCTIONS = 300;
const ALLOWED_SIZES = ['auto', '1024x1024', '1536x1024', '1024x1536'];

/** Siempre se antepone: protege la identidad y composición de la foto frente a instrucciones del usuario. */
const GUARDRAIL =
  'Edit this photograph to improve it. Keep the same subject, composition, people, and objects. ' +
  'Do not add text, watermarks, logos or new objects. Keep the result photorealistic.';

/** Los prompts viven aquí, en el servidor. La app solo envía el identificador del preset. */
const PRESETS = {
  light: 'Improve exposure and lighting: recover shadows and highlights naturally.',
  color: 'Improve contrast and color balance while keeping skin tones natural.',
  denoise: 'Reduce digital noise and grain while preserving fine detail.',
  sharpen: 'Improve perceived sharpness and edge definition without halos or artifacts.',
  full: 'Apply a balanced overall enhancement: lighting, contrast, color, noise reduction and sharpness.',
  custom: ''
};

/** Detecta el tipo real por cabecera (no por el nombre ni el Content-Type declarado). */
function detectImageType(buf) {
  if (!Buffer.isBuffer(buf) || buf.length < 12) return null;
  if (buf[0] === 0xff && buf[1] === 0xd8 && buf[2] === 0xff) return 'image/jpeg';
  if (buf.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))) return 'image/png';
  if (buf.subarray(0, 4).toString('ascii') === 'RIFF' && buf.subarray(8, 12).toString('ascii') === 'WEBP') return 'image/webp';
  return null;
}

/** Quita caracteres de control y limita la longitud. */
function sanitizeInstructions(value) {
  if (typeof value !== 'string') return '';
  return value.replace(/[\u0000-\u001f\u007f]/g, ' ').trim().slice(0, MAX_INSTRUCTIONS);
}

function buildPrompt(presetId, instructions) {
  const parts = [GUARDRAIL];
  if (PRESETS[presetId]) parts.push(PRESETS[presetId]);
  if (instructions) parts.push(`User request: ${instructions}`);
  return parts.join('\n');
}

function validateEnhanceRequest(body) {
  const presetId = typeof body.preset === 'string' ? body.preset : '';
  if (!Object.prototype.hasOwnProperty.call(PRESETS, presetId)) {
    return { ok: false, error: 'Tipo de mejora no válido.' };
  }
  const instructions = sanitizeInstructions(body.instructions);
  if (presetId === 'custom' && !instructions) {
    return { ok: false, error: 'Escribe una instrucción para la opción personalizada.' };
  }
  return { ok: true, preset: presetId, prompt: buildPrompt(presetId, instructions) };
}

module.exports = {
  MAX_UPLOAD_BYTES,
  MAX_INSTRUCTIONS,
  ALLOWED_SIZES,
  GUARDRAIL,
  detectImageType,
  sanitizeInstructions,
  buildPrompt,
  validateEnhanceRequest
};
