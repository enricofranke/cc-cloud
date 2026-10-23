// v0.6 share links: the secret lives only in the URL fragment.

/** Reads (and scrubs) the share id and secret. Exported for tests. */
export function takeShareSecret(loc: { pathname: string; hash: string }, store: Pick<Storage, "getItem" | "setItem"> | null) {
  const id = /^\/s\/([A-Za-z0-9]{1,64})\/?$/.exec(loc.pathname)?.[1];
  if (!id) return { id: undefined, secret: undefined, scrub: false };
  const fromHash = loc.hash.replace(/^#/, "");
  let secret: string | undefined = /^[A-Za-z0-9_-]{8,128}$/.test(fromHash) ? fromHash : undefined;
  try {
    if (secret) store?.setItem(`cccloud.share.${id}`, secret);
    else secret = store?.getItem(`cccloud.share.${id}`) ?? undefined;
  } catch {}
  return { id, secret, scrub: !!fromHash };
}

