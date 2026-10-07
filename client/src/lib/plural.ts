// "report" or "reports" for a count — pure, no React, so lib/handoffText.ts can use it too.
export function reportNoun(count: number): 'report' | 'reports' {
  return count === 1 ? 'report' : 'reports';
}
