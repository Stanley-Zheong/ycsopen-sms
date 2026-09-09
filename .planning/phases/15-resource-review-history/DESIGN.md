# Design

## Data Design

The API returns a normalized row with:

- decision id: `<RESOURCE_TYPE>:<history_id>`;
- resource type/id/code/version;
- tenant id;
- decision state;
- actor, reason, risk, evidence reference, submitted snapshot, lifecycle link, created time.

## UI Design

The page has one filter region, one table region, and one detail drawer. The table row opens an immutable detail view fetched by decision id.
