# ADR-010: Persist Delegation Relationships Using Graph Data Model

## Status
Accepted

## Context
Delegation relationships form complex hierarchical and temporal relationships.

Examples:

- Assistant → Executive
- Manager → Team members
- AI Agent → User

## Decision
Delegation relationships will be stored using a graph data model.

Graph enables:

- Relationship traversal
- Policy evaluation
- Temporal delegation tracking

## Consequences

### Positive
- Efficient relationship resolution
- Supports Zanzibar-style authorization models

### Negative
- Introduces additional datastore
- Requires synchronization with IAM events

## Alternatives Considered
- Relational delegation tables
- Token-only delegation
