// screens/Pair.tsx in the demo: there is nothing to pair with. Says so and shows how it works on a real server.
import { navigate } from "../../../web/src/lib/ui.ts";
import { BASE_ID } from "../session.ts";
import { lang, s } from "../strings.ts";

export function PairScreen(_props: { initialCode?: string; link?: unknown }) {
  return (
    <main class="page page-narrow demo-pair" id="main" tabIndex={-1}>
      <h1 class="px-heading">{s.pairTitle}</h1>
      <p class="muted">{s.pairLead}</p>
      <ol class="setup-steps">
        {s.pairSteps.map((text, i) => (
          <li key={i}>
            <span class="setup-num">{i + 1}</span>
            <div>{text}</div>
          </li>
        ))}
      </ol>
      <div class="demo-pair-actions">
        <button type="button" class="btn" onClick={() => navigate(`/m/${BASE_ID}`)}>
          {s.pairBack}
        </button>
        <a class="btn btn-ghost" href={`/${lang}/download/`}>
          {s.pairDownload}
        </a>
      </div>
    </main>
  );
}
