import { apiClient } from '../api/client';
import type { components } from '../api/generated/schema';
import type { PhotoType } from './prepareImage';

export type MediaAsset = components['schemas']['MediaAssetResponse'];

/** The direct upload to object storage failed (network, expired or refused URL). */
export class UploadFailedError extends Error {
  constructor() {
    super('Direct upload failed');
    this.name = 'UploadFailedError';
  }
}

/**
 * Sends a prepared photo as a Person photo (`PROFILE_PICTURE`): an upload slot, the direct PUT to
 * the pre-signed URL with its signed headers, then the completion, which returns the READY asset
 * (technical-specification.md §16, ADR-007). `onProgress` receives the uploaded share, 0 to 1;
 * `onProcessing` is called when the server starts checking the photo.
 */
export async function uploadPhoto(
  familyId: string,
  photo: { file: Blob; fileName: string; mimeType: PhotoType },
  { onProgress, onProcessing }: { onProgress: (share: number) => void; onProcessing: () => void },
): Promise<MediaAsset> {
  const { data: slot } = await apiClient.POST('/families/{familyId}/media/uploads', {
    params: { path: { familyId } },
    body: {
      purpose: 'PROFILE_PICTURE',
      fileName: photo.fileName,
      mimeType: photo.mimeType,
      sizeBytes: photo.file.size,
    },
  });
  if (slot === undefined) {
    throw new Error('POST /families/{familyId}/media/uploads returned no body');
  }
  await put(slot.uploadUrl, slot.requiredHeaders, photo.file, onProgress);
  onProcessing();
  const { data: asset } = await apiClient.POST(
    '/families/{familyId}/media/uploads/{mediaAssetId}/complete',
    { params: { path: { familyId, mediaAssetId: slot.mediaAssetId } } },
  );
  if (asset === undefined) {
    throw new Error(
      'POST /families/{familyId}/media/uploads/{mediaAssetId}/complete returned no body',
    );
  }
  return asset;
}

/** `fetch` reports no upload progress: the PUT goes through `XMLHttpRequest`. */
function put(
  url: string,
  headers: Record<string, string>,
  body: Blob,
  onProgress: (share: number) => void,
) {
  return new Promise<void>((resolve, reject) => {
    const request = new XMLHttpRequest();
    request.open('PUT', url);
    for (const [name, value] of Object.entries(headers)) {
      request.setRequestHeader(name, value);
    }
    request.upload.onprogress = (event) => {
      if (event.lengthComputable && event.total > 0) onProgress(event.loaded / event.total);
    };
    request.onload = () => {
      if (request.status >= 200 && request.status < 300) {
        onProgress(1);
        resolve();
      } else {
        reject(new UploadFailedError());
      }
    };
    request.onerror = () => {
      reject(new UploadFailedError());
    };
    request.onabort = request.onerror;
    request.send(body);
  });
}
