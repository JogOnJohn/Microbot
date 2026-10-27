# Personal POH detection and hosted-house precedence

Routing reads the persisted detection keys through PohHouseConfiguration rather
than depending on an open Swing panel. Their fingerprint participates in the
transport memo key; edits also invalidate transport snapshots through the normal
ConfigChanged handler. Unknown destinations and malformed anchors fail closed.

W330 takes precedence only with useWorld330MaxHouse enabled on world 330.
Otherwise usePoh enables the player's detected personal house. Keep those modes
separate: merging both synthetic house anchors can mix facilities between houses.

Imported generic house facility edges are not evidence of player ownership.
Replace those outgoing edges and incoming fairy-ring/tree connections with the
detected configuration. Preserve ordinary home exits, house-entry spells/items,
and non-POH world transport networks. Resource/converter formats are unchanged.

PohTransport records whether an edge came from the personal configuration or the
hosted-house builder. Personal-house execution must not invoke hosted-house
switching/blacklisting recovery for an unavailable facility.

The profile inspected during this work has an ornate jewellery box, mounted
digsite, fairy ring, spirit tree, Kharyrll portal and 24 configured Nexus entries.
Mounted Xeric's is absent and must not appear in its personal transport graph.
