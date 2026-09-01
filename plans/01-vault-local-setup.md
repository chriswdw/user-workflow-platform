# Plan: Set Up HashiCorp Vault for Local Development (Ubuntu)

## Context

The `platform-api` review found that `application.properties:1` hardcodes a base64-encoded JWT
HMAC secret checked into source control, and there is no `application-prod.*` file anywhere in
the module to override it. Combined with the dev-token endpoint being enabled by default (see
`02-fix-dev-token-privilege-escalation.md`), this is the root enabler of a critical
privilege-escalation path. The long-term fix is to stop committing secrets at all and pull them
from Vault instead — this plan sets up Vault locally so `02` has something to point at.

This is infrastructure setup, not a code change — no BDD scenarios needed. It doesn't touch the
`./gradlew build cucumber` gate.

---

## Step 1 — Install Vault on Ubuntu

```bash
wget -O- https://apt.releases.hashicorp.com/gpg | sudo gpg --dearmor -o /usr/share/keyrings/hashicorp-archive-keyring.gpg
echo "deb [signed-by=/usr/share/keyrings/hashicorp-archive-keyring.gpg] https://apt.releases.hashicorp.com $(lsb_release -cs) main" | sudo tee /etc/apt/sources.list.d/hashicorp.list
sudo apt update && sudo apt install vault
vault --version
```

## Step 2 — Run Vault

Two options. Dev mode is quickest to try but resets on every reboot — since this machine reboots
daily, use Option B for anything beyond a one-off check.

### Option A — Dev mode (quick, non-persistent)

Dev mode runs a single in-memory, auto-unsealed server — fine for a quick check, **never for
prod**, and **wiped on every restart** (new root token, all secrets/mounts gone).

```bash
# foreground, keep running in its own terminal / tmux pane
vault server -dev -dev-listen-address="127.0.0.1:8200"
```

This prints a `Root Token` on startup — copy it. In a second terminal:

```bash
export VAULT_ADDR='http://127.0.0.1:8200'
export VAULT_TOKEN='<root token from above>'
vault status
```

If you use this option, you must redo Step 3 (mount + secrets) after every reboot, and re-export
`VAULT_TOKEN` with the new root token every time the server restarts.

### Option B — Persistent local Vault (recommended — survives daily reboots)

Uses the `file` storage backend so data survives restarts, run as a systemd service so it starts
on boot automatically, with a small unseal script so you don't manually unseal every morning.

**Config — `/etc/vault.d/vault.hcl`:**

```hcl
storage "file" {
  path = "/opt/vault/data"
}

listener "tcp" {
  address     = "127.0.0.1:8200"
  tls_disable = true  # localhost-only dev machine — never do this beyond local dev
}

ui = true
disable_mlock = true
```

```bash
sudo mkdir -p /opt/vault/data
sudo chown -R vault:vault /opt/vault/data /etc/vault.d
```

**Enable and start the service** (the `hashicorp` apt package ships a `vault.service` unit):

```bash
sudo systemctl enable vault
sudo systemctl start vault
export VAULT_ADDR='http://127.0.0.1:8200'
vault status   # will show "Initialized: false" the first time
```

**One-time initialization** (only ever done once — re-running `operator init` against
already-initialized storage will fail, which is expected):

```bash
vault operator init -key-shares=1 -key-threshold=1 > ~/.vault-init.txt
chmod 600 ~/.vault-init.txt
```

This writes one **Unseal Key** and the **Root Token** to `~/.vault-init.txt`. `-key-shares=1
-key-threshold=1` skips Shamir key-splitting — appropriate for a single-developer local machine,
not for prod (see security note below).

**Auto-unseal on boot** — file storage persists data but Vault still starts *sealed* after every
restart; a systemd drop-in unseals it automatically using the saved key:

```bash
sudo tee /etc/systemd/system/vault.service.d/auto-unseal.conf <<'EOF'
[Service]
ExecStartPost=/bin/bash -c 'sleep 2 && /usr/bin/vault operator unseal $(grep "Unseal Key 1" /home/chris/.vault-init.txt | awk "{print \$NF}")'
EOF
sudo systemctl daemon-reload
sudo systemctl restart vault
```

**Persist your session env vars** so you don't re-export after every reboot — add to `~/.bashrc`:

```bash
export VAULT_ADDR='http://127.0.0.1:8200'
export VAULT_TOKEN="$(grep 'Initial Root Token' ~/.vault-init.txt | awk '{print $NF}')"
```

With this setup, Step 3's mount and secrets are a **one-time operation** — they persist in
`/opt/vault/data` across reboots. You'll only need to redo them if you wipe that directory.

**Security trade-off**: storing the unseal key in a plaintext file (`~/.vault-init.txt`) that a
systemd unit reads on boot defeats part of Vault's security model — normal Vault deployments
split the unseal key across multiple people (Shamir shares) or use cloud KMS auto-unseal
precisely so no single file/person can unseal alone. That trade-off is acceptable for a
single-developer local machine but must **not** carry over to staging/prod (see "Notes for
later" at the bottom of this plan).

## Step 3 — Enable the KV secrets engine and load project secrets

Note: dev mode (Option A) auto-mounts a KV v2 engine at `secret/` on every startup, so
`vault secrets enable` will fail with `path is already in use at secret/` — that's expected,
skip that line and go straight to `vault kv put`. With Option B (persistent), the mount doesn't
exist yet the first time, so run it once as shown below; it then persists across reboots.

```bash
vault secrets enable -path=secret -version=2 kv   # Option B only, one-time; skip for Option A

# JWT signing secret — generate a real one, don't reuse the committed test value
NEW_JWT_SECRET=$(openssl rand -base64 32)
vault kv put secret/platform-api/jwt secret="$NEW_JWT_SECRET"

# local Postgres credentials (adjust to your local setup)
vault kv put secret/platform-api/datasource \
  username="platform_local" \
  password="$(openssl rand -base64 24)"

vault kv get secret/platform-api/jwt
```

## Step 4 — Add Spring Cloud Vault to `platform-api`

**`platform-api/build.gradle.kts`** — add:

```kotlin
implementation("org.springframework.cloud:spring-cloud-starter-vault-config:<version-from-libs.versions.toml-or-BOM>")
```

Add the version to `gradle/libs.versions.toml` under the existing Spring Cloud/Spring Boot
version block rather than hardcoding it inline, per this repo's convention of keeping all
versions in `libs.versions.toml`.

**New file: `platform-api/src/main/resources/bootstrap.yml`** (Spring Cloud Vault reads config
before the main `application.properties` is processed):

```yaml
spring:
  application:
    name: platform-api
  cloud:
    vault:
      uri: http://127.0.0.1:8200
      authentication: TOKEN
      token: ${VAULT_TOKEN}
      kv:
        enabled: true
        backend: secret
        default-context: platform-api
      fail-fast: true
```

With `default-context: platform-api` and `spring.application.name: platform-api`, Spring Cloud
Vault will look up `secret/platform-api/*` paths and expose their keys as Spring properties —
e.g. `secret/platform-api/jwt`'s `secret` key becomes available for binding.

## Step 5 — Replace the committed secret

**`platform-api/src/main/resources/application.properties:1`** — remove the hardcoded value:

```properties
# api.jwt.secret is now supplied by Vault (see bootstrap.yml) — do not set a default here.
# Local dev without Vault: export API_JWT_SECRET before starting the app.
api.jwt.secret=${API_JWT_SECRET:${vault.platform-api.jwt.secret:}}
```

Confirm `SecurityConfig.java:17` (`@Value("${api.jwt.secret}")`) still resolves correctly — if
Vault is unreachable and no env var is set, startup should fail loudly (`fail-fast: true` above
ensures this) rather than silently falling back to an empty/test secret.

## Step 6 — Verify

```bash
export VAULT_ADDR='http://127.0.0.1:8200'
export VAULT_TOKEN='<root token>'
./gradlew :platform-api:bootRun --args='--spring.profiles.active=local'
```

Confirm in the startup logs that Spring Cloud Vault connected (`Located property source:
VaultPropertySource`) and that `api.jwt.secret` resolved to the Vault-stored value, not the old
committed one — e.g. temporarily log its length (never its value) or issue a token via
`DevTokenController` (still gated by `02`'s fix) and verify it validates against the new secret.

## Notes for later (staging/prod)

Dev mode Vault is **not** durable and **not** suitable beyond a local machine — no persistence
across restarts, single unsealed node, root token in plaintext env var. When this pattern moves
to staging/prod, switch to a real Vault deployment (file/Consul/Raft storage backend, proper
unseal keys, AppRole or Kubernetes auth instead of a static root token) — that's a separate,
larger piece of work and out of scope here.
