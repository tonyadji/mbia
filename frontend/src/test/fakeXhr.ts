import { act, waitFor } from '@testing-library/react';

/**
 * The direct upload to object storage. Each PUT reports 40 % then waits for {@link FakeXhr.finish}.
 */
export class FakeXhr {
  static sent: { method: string; url: string; headers: Record<string, string>; body: unknown }[] =
    [];
  static waiting: (() => void)[] = [];
  static failNext = false;

  upload: { onprogress: ((event: Partial<ProgressEvent>) => void) | null } = { onprogress: null };
  onload: (() => void) | null = null;
  onerror: (() => void) | null = null;
  onabort: (() => void) | null = null;
  status = 0;
  private method = '';
  private url = '';
  private headers: Record<string, string> = {};

  open(method: string, url: string) {
    this.method = method;
    this.url = url;
  }

  setRequestHeader(name: string, value: string) {
    this.headers[name] = value;
  }

  send(body: unknown) {
    FakeXhr.sent.push({ method: this.method, url: this.url, headers: this.headers, body });
    const fail = FakeXhr.failNext;
    FakeXhr.failNext = false;
    queueMicrotask(() => {
      this.upload.onprogress?.({ lengthComputable: true, loaded: 40, total: 100 });
    });
    FakeXhr.waiting.push(() => {
      if (fail) {
        this.onerror?.();
      } else {
        this.status = 200;
        this.onload?.();
      }
    });
  }

  static async finish() {
    await waitFor(() => {
      expect(FakeXhr.waiting.length).toBeGreaterThan(0);
    });
    await act(async () => {
      FakeXhr.waiting.shift()?.();
      await Promise.resolve();
    });
  }
}
