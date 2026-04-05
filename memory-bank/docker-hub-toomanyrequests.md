## Fixing Docker Hub "Too Many Requests" in Podman Compose## Problem: Pull Rate Limit Errors
When running podman compose or building images, you may encounter the following error even if you have already run podman login:

toomanyrequests: You have reached your unauthenticated pull rate limit.

This happens because Docker Hub limits anonymous pulls based on your IP address. Even if you are logged in via Podman, the underlying Compose engine (often docker-compose) may not be seeing those credentials, causing it to attempt an "unauthenticated" pull.
## The Conflict: Credential Locations
Podman and Docker-based tools store authentication data in different folders on macOS:

* Podman: ~/.config/containers/auth.json
* Docker/Compose: ~/.docker/config.json

Because these paths differ, being "logged in" in Podman doesn't automatically log you in for the Compose engine.
## Solution: Synchronize Credentials
To resolve this, you must link the two credential files so that both tools share the same login session.

   1. Generate a Docker Hub Token (Recommended):
   Go to Docker Hub Security Settings and create a Personal Access Token (PAT). This is more secure than using your account password.
   2. Login via Podman:
   Use your username and the Token as your password:
   
   podman login docker.io
   
   3. Create a Symbolic Link:
   Map the Podman auth file to the location where Compose expects it:
   
   mkdir -p ~/.docker
   ln -sf ~/.config/containers/auth.json ~/.docker/config.json
   
   4. Verify the Fix:
   Run your podman compose command again. The "unauthenticated" error should disappear as the request is now tied to your Docker Hub account (increasing your limit to 200+ pulls).

## Key Takeaway
On macOS, podman login only authenticates the Podman engine. Creating a symlink to ~/.docker/config.json ensures that podman compose and other Docker-compatible utilities can find your credentials and avoid IP-based rate limits.

