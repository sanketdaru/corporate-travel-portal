## Resolving Podman Installation Conflicts on macOS## Problem: Multiple Podman Instances Detected
When Podman is installed via both the Official PKG/DMG installer (bundled with Podman Desktop) and Homebrew, it creates a conflict in the system $PATH.

* Official Path: /opt/podman/bin/podman
* Homebrew Path: /opt/homebrew/bin/podman

This results in Podman Desktop reporting "Multiple Podman installations detected," which leads to version mismatches and unpredictable behavior when running container commands.
## Solution: Unified Official Installation
To resolve the conflict and establish a clean environment, the following steps were taken:

   1. Complete Removal of Homebrew Instances:
   We uninstalled both the Podman engine and the podman-compose Python script from Homebrew to ensure no lingering dependencies or path shadowing.
   
   brew uninstall podman-compose podman
   
   2. Verification of the Official Binary:
   Confirmed that the system is now exclusively using the official Podman binary:
   
   which podman# Output should be: /opt/podman/bin/podman
   
   3. Modernizing the Compose Workflow:
   Instead of using the external podman-compose (hyphenated) tool, we transitioned to the native podman compose (space) command.
   * How it works: This is a built-in feature of the official Podman binary. It acts as a wrapper that automatically finds and utilizes an external compose provider (like docker-compose) to execute YAML files.
      * Benefit: This setup is more stable, supports modern Compose V2 features (like BuildKit), and is the officially supported path for Podman on macOS.
   
## Key Takeaway
For the most reliable experience on macOS, stick to the official installer and use the native podman compose command. This avoids the "double installation" trap common when mixing Homebrew with standalone macOS installers.

---

## Additional Fixes: Making `podman compose up -d` Work End-to-End

After the installation was unified, several additional issues were discovered when running `podman compose up -d` from scratch. All fixes below are already applied to `docker-compose.yml`.

### Fix 1: Activate the Podman Docker Context

`podman compose` uses docker-compose as its external provider. Docker-compose needs to know which daemon socket to use. A Docker context named `podman` exists and points to the correct podman machine socket, but it must be set as the active context — otherwise docker-compose falls back to the non-existent `/var/run/docker.sock`.

**One-time setup:**

```bash
# Check current context
cat ~/.docker/config.json | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('currentContext','not set'))"

# Set the podman context as active
python3 -c "
import json
with open('/Users/sanketdaru/.docker/config.json', 'r') as f:
    config = json.load(f)
config['currentContext'] = 'podman'
with open('/Users/sanketdaru/.docker/config.json', 'w') as f:
    json.dump(config, f, indent=4)
"
```

### Fix 2: Built Images Must Have `image:` with `localhost/` Prefix

When a service has a `build:` section in docker-compose but no `image:` field, docker-compose generates an internal image name (e.g., `corporate-travel-portal-travel-service`). However, after building, docker-compose treats this as a registry image and **tries to pull it from Docker Hub** when creating containers, rather than using the locally-built copy.

**Solution:** Add an explicit `image:` field with the `localhost/` prefix to every service that has a `build:` section. The `localhost/` prefix signals to docker-compose that this image is local-only and should never be pulled.

```yaml
# ✅ Correct
travel-service:
  build:
    context: .
    dockerfile: services/travel-service/Dockerfile
  image: localhost/corporate-travel-portal-travel-service:latest

# ❌ Will fail — docker-compose tries to pull from Docker Hub
travel-service:
  build:
    context: .
    dockerfile: services/travel-service/Dockerfile
```

### Fix 3: Remove `platform: linux/amd64` from Built Services

On Apple Silicon (arm64), locally-built images are always built as `linux/arm64`. If a service in docker-compose specifies `platform: linux/amd64`, docker-compose passes a `platform=linux/amd64` constraint to the container create API. Podman then searches for an `amd64` variant of the image and returns **"image not known"** because only an `arm64` variant exists.

**Solution:** Remove `platform: linux/amd64` from all services that have a `build:` section. External pulled images (like OPA) that are amd64-only can keep the platform spec — Podman will run them under emulation.

```yaml
# ❌ Breaks on Apple Silicon with locally-built images
travel-service:
  build: ...
  platform: linux/amd64   # Remove this line

# ✅ Correct — omit platform for built services
travel-service:
  build: ...
```

---

## Complete Fresh-Start Checklist

If you ever need to tear down everything and start from scratch:

```bash
# 1. Stop and remove all containers
podman rm -f --all

# 2. Remove project network
podman network ls --format "{{.Name}}" | grep -v "^podman$" | xargs -I{} podman network rm {}

# 3. Verify the Docker context is set to podman (one-time setup)
cat ~/.docker/config.json | python3 -c "import sys,json; print(json.load(sys.stdin).get('currentContext'))"
# Should print: podman

# 4. Start the full stack
podman compose up -d
```
