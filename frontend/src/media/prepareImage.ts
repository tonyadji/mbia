/** Photo formats accepted by Mbia (mvp.md §23); also the `accept` of the file picker. */
export const PHOTO_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const;

export type PhotoType = (typeof PHOTO_TYPES)[number];

/** Maximum upload size (`CreateMediaUploadRequest.sizeBytes`, mvp.md §23). */
export const MAX_PHOTO_BYTES = 15 * 1024 * 1024;

/** Long edge of a photo sent from the browser (Phase 3 plan §3.6). */
export const MAX_LONG_EDGE = 2560;

const JPEG_QUALITY = 0.85;

/** Why a file is refused before upload. */
export type PhotoRefusal = 'unsupported' | 'tooLarge';

export type PreparedPhoto =
  | { ok: true; file: Blob; fileName: string; mimeType: PhotoType }
  | { ok: false; refusal: PhotoRefusal };

export function isPhotoType(type: string): type is PhotoType {
  return (PHOTO_TYPES as readonly string[]).includes(type);
}

/** Dimensions within {@link MAX_LONG_EDGE}, proportions kept. */
export function fitWithin(width: number, height: number, maxEdge = MAX_LONG_EDGE) {
  const scale = Math.min(1, maxEdge / Math.max(width, height));
  return { width: Math.round(width * scale), height: Math.round(height * scale) };
}

/**
 * Makes a picked file ready to upload (Phase 3 plan §3.6): a JPEG, PNG or WEBP is decoded, turned
 * upright, reduced to a long edge of 2560 px or less and re-encoded as JPEG with the Canvas API.
 * When the browser cannot decode it, the original is sent as is and the server decides.
 */
export async function prepareImage(file: File): Promise<PreparedPhoto> {
  if (!isPhotoType(file.type)) return { ok: false, refusal: 'unsupported' };
  const encoded = await reencode(file);
  const prepared: PreparedPhoto = encoded
    ? { ok: true, file: encoded, fileName: jpegName(file.name), mimeType: 'image/jpeg' }
    : { ok: true, file, fileName: file.name.slice(0, 500) || 'photo', mimeType: file.type };
  if (prepared.file.size > MAX_PHOTO_BYTES) return { ok: false, refusal: 'tooLarge' };
  return prepared;
}

async function reencode(file: File): Promise<Blob | null> {
  let bitmap: ImageBitmap;
  try {
    // The EXIF orientation is applied here: the re-encoded image carries no metadata.
    bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
  } catch {
    return null;
  }
  try {
    const { width, height } = fitWithin(bitmap.width, bitmap.height);
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext('2d');
    if (!context) return null;
    // A transparent PNG gets a white background rather than a black one.
    context.fillStyle = 'white';
    context.fillRect(0, 0, width, height);
    context.drawImage(bitmap, 0, 0, width, height);
    return await new Promise<Blob | null>((resolve) => {
      canvas.toBlob(resolve, 'image/jpeg', JPEG_QUALITY);
    });
  } finally {
    bitmap.close();
  }
}

function jpegName(name: string) {
  const base = name.replace(/\.[^./\\]*$/, '');
  return `${base === '' ? 'photo' : base.slice(0, 490)}.jpg`;
}
