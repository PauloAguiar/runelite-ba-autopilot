# Penance diagnostics

Enable **Show priorities (debug)** in the client to show runner states, Penance Healer health/poison
estimates, and a dashed fixed **8×8 tile** grid. The replay has separate **8×8 zones** and
**NPC debug** switches; its NPC details appear on hover or keyboard focus rather than covering
the map. New recorder builds retain NPC animation and overhead speech; old captures still work.

Runner labels distinguish observed eating (`EAT`, `EAT GOOD`, `EAT BAD`) from inferred motion
(`WALK?`, `HUNT?`, `POST-BAD?`). `STATE?` means no consecutive movement observation exists.
The second line shows the planner's tactical classification, also marked `?`, and a possible
good/bad food tile (`G@x,y` or `B@x,y`). A nearby bait trigger enables the fixed-zone candidate
search, ordered NE, E, SE, N, self, S, NW, W, SW, with the newest drop winning in a zone.
Candidate food is only available for a captured Defender. The client does not expose the actual
target, line-of-sight result, five-tick phase or separate three-tick hunting timer, so these
labels deliberately do not claim an exact AI state or timer countdown.

Healer labels show spawn order, approximate current HP, wave maximum HP and estimated next
poison strength, for example `H3 HP~41/76 P3`. Wave maxima are 27, 32, 37, 43, 49, 55, 60, 67,
76 and 60. Health-bar quantization produces a range; observed damage supplies a fallback when
there is no bar. Missing health remains `?`, rather than assuming a fresh full-health spawn.

The forecast sums remaining food-poison hits and compares their bounds with the HP bounds.
Confirmed own feeding supplies an anchor; an unanchored observed hit supplies a range within
its poison strength. A lone unanchored one-damage hit cannot identify a food-poison duration.
Results are **dies ~N–Mt**, **survives: short ≥N HP**, **kill? earliest ~Nt**, **no active poison**,
or **poison outcome unknown**. The estimate assumes no further feeding, cannon damage, healing,
stun or missing poison hits. It describes the current poison alone, not future replay actions.
Reserve finishing reuses the bounded HP estimate to compare nearby targets and recognise a
healer that will die to the next 4-poison hit. The poison-only death-time forecast remains a
diagnostic; it does not promise an exact kill time or override first-tag/refresh priorities.

The replay uses colored egg ovals, shaped food icons, quantity badges and counted stacks for
shared tiles. Hovering a stack lists every occupant separately, including each food/egg amount.
Zoom offers full arena, 2× and 4× views, following the captured player. Clicking markers inspects
them; only clicking empty space edits position.

Sources: [Penance Runner](https://oldschool.runescape.wiki/w/Penance_Runner),
[Barbarian Assault advanced strategies](https://oldschool.runescape.wiki/w/Barbarian_Assault/Advanced_strategies),
[Penance Healer](https://oldschool.runescape.wiki/w/Penance_Healer). Health-bar inversion follows
RuneLite's `OpponentInfoOverlay` quantization bounds. Verification covers fixed zone boundaries
and priority, newest-food choice, observed versus inferred states, wave HP, quantization across
all wave health values, poison uncertainty and elapsed death estimates. Live label placement
and grid projection still need the next in-game run.
