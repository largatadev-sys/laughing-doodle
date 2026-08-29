# 09 — Ingest lag: measure, then tune

**What to do:** Reports sometimes take minutes to appear after a reporter submits
(developer experience, 2026-08-29: refreshing the inbox while a report was still absent).
The pipeline explains why no inbox feature can fix it: delivery is Largata's
store-and-forward push — when the Railway service is asleep, Largata's first attempt wakes
it but likely times out before Spring boots, so the report lands on a **later retry**; the
perceived lag is roughly Largata's retry backoff, not worklog processing (intake is a
synchronous insert — arrival *is* visibility). Story 20's polling only shortens the
display wait after arrival, not this delivery wait.

**Measure before touching anything.** Every report already stores the answer:
`receivedAt − submittedAt` is the true end-to-end lag, persisted per row by design.
Pull the distribution across prod reports (API or DB). Caveat: `submittedAt` originates on
the reporter's device — trust the pattern across reports, not single points (clock skew).

**Reading the result:**

- Lags clustering at a repeating interval (~5/15 min) → it's Largata's retry backoff;
  fix lives in **the Largata repo** (tighten the early backoff steps — cheap, aimed at
  the cause). Hand that session this ticket.
- Lags mostly small with rare spikes → the bad experience was an outlier; close as
  measured, no change.
- Genuinely dominated by cold-start first-attempt failures → the worklog-side lever is a
  Railway always-on instance (costs money — developer decision).

**Rejected up front:** a keep-alive ping — it pays for always-on in requests while hiding
the signal that would justify (or kill) the spend.

**Blocked by:** None. Independent of Story 20.

**Status:** ready-for-agent — for the measurement. The lever decision that follows
(Largata backoff vs. Railway plan vs. accept) is **ready-for-human**: it spends either
another repo's time or real money.
