# Backup compatibility policy

Kilometrina must preserve existing user data across app upgrades and manual backup restores.

## Current formats

- Room database: version 3
- Manual backup format: `kilometrina-backup`, schema version 3
- Supported backup imports: schema versions 1, 2 and 3

## Required compatibility

- Never use a destructive Room migration for a release build.
- Every Room schema change must include an explicit migration from the previous version.
- Existing trip IDs and GPS point relationships must remain stable.
- New non-null columns must have safe defaults for older rows.
- Old backups must remain importable after adding new fields.
- Missing v3 fields in v1/v2 backups default to:
  - `tripType = BUSINESS`
  - `gpsQuality = ""`
  - no attachments
  - calendar suggestions disabled
- Re-exporting restored legacy data uses the latest backup schema.

## Regression gates

CI tests must cover:

1. schema v1 backup -> current model;
2. schema v2 backup -> current model;
3. current schema encode -> decode without data loss, including attachments;
4. Room migration chain 1 -> 2 -> 3 must remain registered.

Any feature that breaks one of these guarantees must not be merged.
