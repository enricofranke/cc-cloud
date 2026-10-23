// v0.6 share links: the secret lives only in the URL fragment and, after that, only in memory.

/** Reads the share id and secret from /s/<id>#<secret>. The secret is never persisted. Exported for tests. */
export function takeShareSecret(loc: { pathname: string; hash: string }) {
  const id = /^\/s\/([A-Za-z0-9]{1,64})\/?$/.exec(loc.pathname)?.[1];
  if (!id) return { id: undefined, secret: undefined, scrub: false };
  const fromHash = loc.hash.replace(/^#/, "");
  const secret: string | undefined = /^[A-Za-z0-9_-]{8,128}$/.test(fromHash) ? fromHash : undefined;
  return { id, secret, scrub: !!fromHash };
}

/** Removes share secrets older builds kept in sessionStorage. */
export function forgetStoredShareSecrets(store: Pick<Storage, "length" | "key" | "removeItem"> | null) {
  if (!store) return;
  try {
    const keys: string[] = [];
    for (let i = 0; i < store.length; i++) {
      const k = store.key(i);
      if (k?.startsWith("cccloud.share.")) keys.push(k);
    }
    keys.forEach((k) => store.removeItem(k));
  } catch {}
}
