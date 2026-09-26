import { MAX_PHOTO_BYTES, fitWithin, prepareImage } from './prepareImage';

/** A decoded image of the given size; `close` is checked to be called. */
function bitmap(width: number, height: number) {
  return { width, height, close: vi.fn() };
}

/** The Canvas API, which jsdom lacks: records the drawing and returns a JPEG of `outputBytes`. */
function fakeCanvas(outputBytes = 1000) {
  const drawn: { width: number; height: number }[] = [];
  const toBlob = vi.fn((callback: BlobCallback, type?: string) => {
    callback(new Blob([new Uint8Array(outputBytes)], { type }));
  });
  const context = {
    fillStyle: '',
    fillRect: vi.fn(),
    drawImage: vi.fn((_image: unknown, _x: number, _y: number, width: number, height: number) => {
      drawn.push({ width, height });
    }),
  };
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(
    context as unknown as CanvasRenderingContext2D,
  );
  vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation(toBlob);
  return { drawn, toBlob, context };
}

const file = (type: string, name = 'photo.png', bytes = 10) =>
  new File([new Uint8Array(bytes)], name, { type });

describe('prepareImage', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('keeps proportions within a long edge of 2560 px', () => {
    expect(fitWithin(3000, 2000)).toEqual({ width: 2560, height: 1707 });
    expect(fitWithin(2000, 4000)).toEqual({ width: 1280, height: 2560 });
    expect(fitWithin(800, 600)).toEqual({ width: 800, height: 600 });
  });

  it('refuses a file that is not a JPEG, PNG or WEBP before anything else', async () => {
    const decode = vi.fn();
    vi.stubGlobal('createImageBitmap', decode);

    for (const type of ['image/heic', 'image/gif', 'application/pdf', '']) {
      expect(await prepareImage(file(type))).toEqual({ ok: false, refusal: 'unsupported' });
    }
    expect(decode).not.toHaveBeenCalled();
  });

  it('downscales a large photo, upright, and re-encodes it as JPEG at quality 0.85', async () => {
    const decoded = bitmap(3000, 2000);
    const decode = vi.fn().mockResolvedValue(decoded);
    vi.stubGlobal('createImageBitmap', decode);
    const canvas = fakeCanvas();

    const prepared = await prepareImage(file('image/jpeg', 'IMG_2044.JPEG'));

    expect(decode).toHaveBeenCalledWith(expect.any(File), { imageOrientation: 'from-image' });
    expect(canvas.drawn).toEqual([{ width: 2560, height: 1707 }]);
    expect(canvas.toBlob).toHaveBeenCalledWith(expect.any(Function), 'image/jpeg', 0.85);
    expect(prepared).toMatchObject({ ok: true, fileName: 'IMG_2044.jpg', mimeType: 'image/jpeg' });
    expect(prepared.ok && prepared.file.size).toBe(1000);
    expect(decoded.close).toHaveBeenCalled();
  });

  it('re-encodes a smaller PNG at its size, on a white background', async () => {
    vi.stubGlobal('createImageBitmap', vi.fn().mockResolvedValue(bitmap(800, 600)));
    const canvas = fakeCanvas();

    const prepared = await prepareImage(file('image/png', 'logo.png'));

    expect(canvas.drawn).toEqual([{ width: 800, height: 600 }]);
    expect(canvas.context.fillStyle).toBe('white');
    expect(prepared).toMatchObject({ ok: true, fileName: 'logo.jpg', mimeType: 'image/jpeg' });
  });

  it('sends the original as is when the browser cannot decode it', async () => {
    vi.stubGlobal('createImageBitmap', vi.fn().mockRejectedValue(new Error('cannot decode')));
    const original = file('image/webp', 'grand-mere.webp');

    expect(await prepareImage(original)).toEqual({
      ok: true,
      file: original,
      fileName: 'grand-mere.webp',
      mimeType: 'image/webp',
    });
  });

  it('refuses a photo still above 15 MB', async () => {
    vi.stubGlobal('createImageBitmap', vi.fn().mockRejectedValue(new Error('cannot decode')));

    expect(await prepareImage(file('image/jpeg', 'huge.jpg', MAX_PHOTO_BYTES + 1))).toEqual({
      ok: false,
      refusal: 'tooLarge',
    });
  });
});
