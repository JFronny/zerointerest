Each client maintains a set of trusted summary event IDs.
When a client receives a new summary event, it must ensure the following before marking it as trusted:
1. If a rejection for the summary exists, the summary is always rejected.
2. Otherwise, if the summary event is the first summary event ever encountered in a room, it is trusted.
3. Otherwise, if the summary event has no trusted parents, it is rejected.
4. Otherwise, if the sum of transactions from a parent does not result in the balances, the event is rejected.
5. Otherwise, if a common ancestor exists between two parents, and the following transactions differ between them, the event is rejected.
6. Otherwise, if transactions that are not between the summaries temporally are referenced, the event is rejected.
7. Otherwise, the summary event is trusted.

If the summary event was rejected and no rejection event exists, the client must create a rejection event for the summary.
Clients must store the trusted summary event IDs persistently to ensure consistency across sessions.
Since rooms are append-only, once a summary event is marked as trusted or rejected, its status cannot change unless a rejection event is created.

A rejection event is a `m.reaction` event with a `👎` key ("\uD83D\uDC4E")