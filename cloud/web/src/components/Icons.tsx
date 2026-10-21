// Inline SVG icons (no icon font, nothing fetched at runtime).
import type { JSX } from "preact";

type P = JSX.SVGAttributes<SVGSVGElement> & { size?: number };
const Svg = ({ size = 20, children, ...p }: P & { children: any }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="square" stroke-linejoin="miter" aria-hidden="true" {...p}>
    {children}
  </svg>
);

export const IconBack = (p: P) => (<Svg {...p}><path d="M15 5l-7 7 7 7" /></Svg>);
export const IconPlus = (p: P) => (<Svg {...p}><path d="M12 5v14M5 12h14" /></Svg>);
export const IconBell = (p: P) => (<Svg {...p}><path d="M6 16V11a6 6 0 0 1 12 0v5l2 2H4zM10 21h4" /></Svg>);
export const IconGear = (p: P) => (
  <Svg {...p}>
    <circle cx="12" cy="12" r="3" />
    <path d="M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1" />
  </Svg>
);
export const IconClose = (p: P) => (<Svg {...p}><path d="M6 6l12 12M18 6L6 18" /></Svg>);
export const IconChevron = (p: P) => (<Svg {...p}><path d="M9 5l7 7-7 7" /></Svg>);
export const IconLock = (p: P) => (<Svg {...p}><rect x="5" y="11" width="14" height="10" /><path d="M8 11V7a4 4 0 0 1 8 0v4" /></Svg>);
export const IconSun = (p: P) => (
  <Svg {...p}>
    <circle cx="12" cy="12" r="4" />
    <path d="M12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.5 1.5M17.5 17.5L19 19M5 19l1.5-1.5M17.5 6.5L19 5" />
  </Svg>
);
export const IconMoon = (p: P) => (<Svg {...p}><path d="M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5z" /></Svg>);
export const IconAuto = (p: P) => (<Svg {...p}><circle cx="12" cy="12" r="8" /><path d="M12 4v16" /><path d="M12 4a8 8 0 0 1 0 16z" fill="currentColor" /></Svg>);
export const IconWifiOff = (p: P) => (<Svg {...p}><path d="M3 3l18 18M8.5 16.5a5 5 0 0 1 7 0M5 13a10 10 0 0 1 5-2.7M14 10.3A10 10 0 0 1 19 13M2 9.5a15 15 0 0 1 4.5-2.8M11 5a15 15 0 0 1 11 4.5" /><circle cx="12" cy="20" r="0.5" /></Svg>);
export const IconPower = (p: P) => (<Svg {...p}><path d="M12 3v9M6.3 7.3a8 8 0 1 0 11.4 0" /></Svg>);
export const IconAlert = (p: P) => (<Svg {...p}><path d="M12 3l10 18H2zM12 10v5M12 18v.5" /></Svg>);
export const IconInfo = (p: P) => (<Svg {...p}><rect x="3" y="3" width="18" height="18" /><path d="M12 11v6M12 7v.5" /></Svg>);
export const IconTrash = (p: P) => (<Svg {...p}><path d="M4 7h16M9 7V4h6v3M6 7l1 14h10l1-14" /></Svg>);

/** Pixel-art cloud with a terminal cursor: the CC: Cloud mark. 16x16 grid. */
export function Logo({ size = 28 }: { size?: number }) {
  // rows of the cloud silhouette (x0, x1 inclusive) on a 16 grid
  const cloud: [number, number, number][] = [
    [3, 6, 9], [4, 5, 10], [5, 4, 12], [6, 2, 13], [7, 1, 14], [8, 1, 14], [9, 1, 14], [10, 2, 13],
  ];
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" shape-rendering="crispEdges" aria-hidden="true">
      <rect x="0" y="0" width="16" height="16" fill="var(--logo-bg)" />
      {cloud.map(([y, a, b]) => (
        <rect x={a} y={y} width={b - a + 1} height="1" fill="var(--cyan)" />
      ))}
      <rect x="2" y="10" width="12" height="1" fill="var(--cyan-dk)" />
      <rect x="4" y="7" width="1" height="1" fill="var(--logo-bg)" />
      <rect x="5" y="8" width="1" height="1" fill="var(--logo-bg)" />
      <rect x="4" y="9" width="1" height="1" fill="var(--logo-bg)" />
      <rect x="7" y="9" width="3" height="1" fill="var(--logo-bg)" />
      <rect x="0" y="13" width="16" height="3" fill="var(--gold)" />
      <rect x="0" y="13" width="16" height="1" fill="var(--gold-hi)" />
    </svg>
  );
}
