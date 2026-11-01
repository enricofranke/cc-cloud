// lib/icons.ts in the demo: item icons are static files under the demo's base path (<img> does not go through fetch).
import { iconUrl as real, namespaceHue } from "../../../web/src/lib/icons.ts?original";

export { namespaceHue };

export const iconUrl = (name: string) => {
  const url = real(name);
  return url && `${import.meta.env.BASE_URL}${url.slice(1)}`;
};
