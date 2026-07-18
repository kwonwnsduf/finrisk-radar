import type { DocumentRiskMatch } from "@/lib/api/documents";

export function RiskHighlight({ match }: { match: DocumentRiskMatch }) {
  const start = Math.max(0, match.matchStartOffset);
  const end = Math.min(match.sentenceText.length, match.matchEndOffset);
  return (
    <p className="text-sm leading-7 text-slate-800">
      {match.sentenceText.slice(0, start)}
      <mark className="rounded bg-amber-200 px-1 text-amber-950">
        {match.sentenceText.slice(start, end)}
      </mark>
      {match.sentenceText.slice(end)}
    </p>
  );
}
