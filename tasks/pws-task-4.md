Task PWA-4: Data Synchronization Logic
1. Objective
To implement the core two-way data synchronization mechanism, allowing the PWA to pull data from the desktop server and push review logs back.

2. Scope
Desktop Server:

Create a primary API endpoint POST /api/sync.

This endpoint will handle incoming review logs from the PWA and return updated data to the PWA.

PWA:

Create a sync.service.js module to manage all synchronization logic.

Implement the client-side logic to periodically call the /api/sync endpoint.

Process the server's response and update the local IndexedDB.

Out of Scope: Conflict resolution (Last Write Wins is assumed), real-time sync.

3. Technical Specifications
API Endpoint: POST /api/sync

Request Body (PWA to Server):

{
  "lastSyncTimestamp": "2025-10-11T10:30:00Z", // ISO 8601 format, or 0 for first sync
  "reviewLogs": [
    { "cardId": 123, "rating": 3, "reviewedAt": "...", "latencyMs": 1500 }
  ]
}

Response Body (Server to PWA):

{
  "syncTimestamp": "2025-10-11T10:35:00Z",
  "data": {
    "decks": [ ... ],   // Full objects
    "notes": [ ... ],   // Full objects
    "cards": [ ... ]    // Full objects
  }
}

Sync Logic: The server returns all decks/notes/cards that have been created or updated since the lastSyncTimestamp provided by the client. Review logs from the client are saved to the server's database.

4. Detailed Implementation Steps
Desktop: Create DTOs: Create Plain Old Java Objects (POJOs) for SyncRequest, SyncResponse, and the data entities (DeckDTO, NoteDTO, etc.) to be serialized to/from JSON. Use a library like Jackson or GSON.

Desktop: Implement /api/sync Endpoint:

In WebServerManager.java, define the post("/api/sync", ctx -> ...) handler.

Deserialize the request body into a SyncRequest object.

Write: Iterate through request.reviewLogs and save each one to the server's database. This should be done in a transaction.

Read: Query the server's database for all decks, notes, and cards where updated_at > request.lastSyncTimestamp.

Respond: Construct a SyncResponse object containing the new data and the current server timestamp. Serialize it to JSON and send it as the response.

PWA: Create services/syncService.js:

Create a function syncWithServer().

This function will:

Read the server_url and last_sync_timestamp from localStorage.

Query IndexedDB for all review_logs that have not yet been synced.

Construct the request body JSON.

Use fetch() to make the POST request to the server.

Handle fetch errors, especially certificate errors which may indicate a pairing issue.

On a successful response:

Parse the response JSON.

Use db.transaction() to perform a bulk update (bulkPut) of the received decks, notes, and cards into IndexedDB.

Mark the sent review logs as synced in IndexedDB.

Save the new syncTimestamp from the response to localStorage.

PWA: Integrate Sync:

Add a "Sync" button to the PWA's main UI.

When clicked, it calls syncWithServer() and displays feedback (e.g., "Syncing...", "Sync complete", "Sync failed").

5. Definition of Done (DoD)
[ ] The desktop server has a functional /api/sync endpoint that correctly processes requests and returns data.

[ ] The PWA has a syncService that can successfully communicate with the desktop API.

[ ] After the first sync, all data from the desktop is present in the PWA's IndexedDB.

[ ] Reviews performed on the PWA are successfully sent and saved to the desktop's database on the next sync.

[ ] Changes made on the desktop (e.g., adding a new card) are correctly reflected on the PWA after a sync.