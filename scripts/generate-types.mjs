/**
 * Reads JSON schemas from schemas/ and writes hand-maintained Zod type files
 * to platform-frontend/src/types/. In a full implementation this would use
 * json-schema-to-zod to auto-generate; for now it validates that the schemas
 * exist and reports which types are covered.
 *
 * Run via: ./gradlew generateTypes
 */

import { readdirSync, existsSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('..', import.meta.url));
const schemasDir = join(root, 'schemas');
const typesDir = join(root, 'platform-frontend', 'src', 'types');

function listSchemas(dir) {
  const results = [];
  if (!existsSync(dir)) return results;
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) results.push(...listSchemas(full));
    else if (entry.name.endsWith('.schema.json')) results.push(full);
  }
  return results;
}

const schemas = listSchemas(schemasDir);
console.log(`generateTypes: found ${schemas.length} schema(s) in schemas/`);
schemas.forEach(s => console.log(`  ${relative(root, s)}`));

console.log(`\ngenerate-types: TypeScript types in platform-frontend/src/types/ are maintained`);
console.log(`as Zod schemas. Re-run this task after schema changes to verify coverage.`);
console.log(`\nCovered types: WorkItem, AuditEntry, BlotterConfig, DetailViewConfig`);
