import type { SnapshotCell, SnapshotManifest } from './parkingsCell';

/**
 * A missing file listed in the index is a broken snapshot, never an empty parking area.
 * Retry once against a fresh pointer when Hosting removes the previous version during a deploy.
 * [PARKINGS-FRANCE-SNAPSHOT-001]
 */
export class ParkingSnapshotReader {
  private pointer?: { at: number; manifest: SnapshotManifest };
  private readonly indexes = new Map<string, Record<string, number>>();
  private readonly cells = new Map<string, SnapshotCell>();
  private readonly indexReads = new Map<string, Promise<Record<string, number>>>();

  constructor(
    private readonly baseUrl: string,
    private readonly get: typeof fetch = fetch,
  ) {}

  private async json(path: string): Promise<unknown> {
    const response = await this.get(`${this.baseUrl}/parkings/v1/${path}`, {
      signal: AbortSignal.timeout(15_000),
    });
    if (!response.ok) throw new Error(`snapshot ${path}: HTTP ${response.status}`);
    return response.json();
  }

  private async readPointer(now: number, force: boolean): Promise<SnapshotManifest> {
    if (!force && this.pointer && now - this.pointer.at < 60_000) return this.pointer.manifest;
    const manifest = await this.json('latest.json') as SnapshotManifest;
    if (!/^[a-zA-Z0-9_-]+$/.test(manifest.version) ||
        !Number.isInteger(manifest.cells) || manifest.cells < 1 ||
        !Number.isInteger(manifest.parkings) || manifest.parkings < 1) {
      throw new Error('invalid snapshot pointer');
    }
    this.pointer = { at: now, manifest };
    return manifest;
  }

  private async readIndex(selected: SnapshotManifest): Promise<Record<string, number>> {
    const cached = this.indexes.get(selected.version);
    if (cached) return cached;
    let pending = this.indexReads.get(selected.version);
    if (!pending) {
      pending = (async () => {
        const index = await this.json(`${selected.version}/index.json`) as SnapshotManifest & {
          counts: Record<string, number>;
        };
        if (index.version !== selected.version || index.cells !== selected.cells ||
            index.parkings !== selected.parkings || !index.counts ||
            Object.keys(index.counts).length !== selected.cells ||
            Object.values(index.counts).some(n => !Number.isInteger(n) || n < 1) ||
            Object.values(index.counts).reduce((a, b) => a + b, 0) !== selected.parkings) {
          throw new Error('invalid snapshot index');
        }
        this.indexes.clear();
        this.indexes.set(selected.version, index.counts);
        return index.counts;
      })();
      this.indexReads.set(selected.version, pending);
    }
    try {
      return await pending;
    } finally {
      // Failed reads must be retriable too. Concurrent callers share only the in-flight work.
      if (this.indexReads.get(selected.version) === pending) this.indexReads.delete(selected.version);
    }
  }

  async readBlock(ids: string[], now: number): Promise<{
    manifest: SnapshotManifest; cells: SnapshotCell[];
  }> {
    let manifest = await this.readPointer(now, false);
    for (let attempt = 0; attempt < 2; attempt++) {
      const selected = manifest;
      try {
        if (selected.serve === false) return { manifest: selected, cells: [] };
        const expected = await this.readIndex(selected);
        const cells = await Promise.all(ids.filter(id => Object.hasOwn(expected, id)).map(async id => {
          const key = `${selected.version}/${id}`;
          const cached = this.cells.get(key);
          if (cached) return cached;
          const cell = await this.json(`${key}.json`) as SnapshotCell;
          if (cell.version !== selected.version || cell.cell !== id ||
              !Array.isArray(cell.parkings) || cell.parkings.length !== expected[id]) {
            throw new Error(`invalid snapshot cell ${key}`);
          }
          // Bound a long-lived instance, including entries from earlier versions.
          if (this.cells.size >= 1024) this.cells.clear();
          this.cells.set(key, cell);
          return cell;
        }));
        return { manifest: selected, cells };
      } catch (error) {
        if (attempt === 1) throw error;
        const fresh = await this.readPointer(now, true);
        if (fresh.version === manifest.version) throw error;
        manifest = fresh;
      }
    }
    throw new Error('snapshot unavailable');
  }
}
