# Cigaro addon API (experimental)

Build Cigaro first. `examples/addon` contains a minimal Paper plugin: build it with
`../../gradlew -p examples/addon build --offline`. Put Cigaro and the addon jars in
`plugins/`; the addon declares `depend: [Cigaro]`. Register types on the server
thread during the addon's `onEnable`, before the first server tick, when Cigaro
restores saved stations and plants. IDs must use an addon-specific ASCII prefix,
for example `EXAMPLE_FOREST`, and must never be reused; duplicates throw.

`CigaroAPI.registerTobacco` registers a variety, its default flavor, and sets of
matching and clashing flavors. `registerFlavor` registers a flavor with a list of
`SmokeEffect`s. Addon IDs and models remain the addon's responsibility: supply
item-model cases and crop models via a compatible merged resource pack. Names fall
back to the type's display name if language entries are missing. Register the same
IDs consistently after every restart, before their saved data is loaded.

`CigaroTobaccoEvent` reports successful planting, natural growth, harvesting,
manual cutting/blending, manual product collection, cigar use, pipe and hookah
puffs. Its item is a snapshot; it is not cancellable. `CigaroPuffEvent` reports
a completed cigarette puff, and `CigaroAddictionChangeEvent` reports score changes
from smoking and natural recovery. The current API does not yet provide events
for all automation or cancellable pre-operation hooks. Station extraction and player-data methods must
be called on the server thread; some return `null` when extraction is not safe.
Direct `PlayerData` mutation can bypass invariants and should be avoided.

This is an experimental API, not a stable compatibility guarantee. Reloading or
removing an addon while its types exist in the world is not supported. Missing
addon types must not be treated as safe to load or save. Full namespace-safe IDs,
resource-pack composition, unregistering and stronger lifecycle guarantees are
not implemented.
