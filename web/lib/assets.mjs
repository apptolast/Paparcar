/**
 * Content-hashed asset URLs.
 *
 * ## Why this exists
 *
 * The first version served `/assets/css/site.css` under `max-age=900`. That is a trap with a
 * fifteen-minute fuse: HTML is served `must-revalidate`, so a deploy reaches a returning visitor
 * immediately — but their browser keeps the previous stylesheet for up to a quarter of an hour.
 * They get NEW markup painted by OLD css, which is not "slightly stale", it is broken: classes that
 * did not exist yet have no rules, so controls lose their shape and text runs together.
 *
 * It happened. The language switch was rebuilt from `.lang-switch` to `.langs` and, for the next
 * fifteen minutes, anyone who had loaded the site that day saw two bare words stuck together where
 * the control should be.
 *
 * Hashing the filename removes the possibility rather than shortening the window: a changed file is
 * a different URL, so a cached copy can never be the wrong one, and the files can then be cached
 * for a year instead of fifteen minutes.
 *
 * ## Contract
 *
 * Every reference to a file under `assets/` goes through [asset]. The build fills the manifest
 * before rendering a single page; asking for a path that is not in it is a build error, because a
 * typo'd asset URL is a 404 that no test would otherwise catch.
 */

let manifest = null;

/** Called by the build once the assets have been published. */
export function setAssetManifest(map) {
  manifest = map;
}

/** `/assets/css/site.css` → `/assets/css/site.1a2b3c4d.css` */
export function asset(path) {
  const key = path.replace(/^\/?assets\//, '');
  if (!manifest) throw new Error(`asset('${path}') before the manifest was set — the build must publish assets first`);
  const hashed = manifest[key];
  if (!hashed) {
    throw new Error(
      `asset('${path}') is not in the manifest. Known: ${Object.keys(manifest).slice(0, 8).join(', ')}…`,
    );
  }
  return `/assets/${hashed}`;
}
