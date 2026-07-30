# Auth and identity

Why the server cannot believe what the mod tells it, and the design that fixes it.

This is the area where a plausible-looking shortcut is most likely to be insecure, so the reasoning
matters more than the conclusion.

## The threat

The mod runs on the player's machine and can be modified or replaced entirely. Anything it asserts
is a claim, not a fact.

**Minecraft UUIDs are public.** They are trivially looked up from a username, and who is currently
online is observable. So "the client says it is UUID X" carries no weight — an attacker picks any
online player's UUID and submits data as them.

## Current state

`HandshakeService` compares the mod's version against the server's build version and nothing else.
Identity is entirely unverified, and version equality is exact — so every server release rejects
every deployed mod. Both are being replaced: identity by #31 and #33, the version rule by #39.

## The design

Three checks, in this order, each cheap-to-expensive:

1. **Version** — is this mod version supported? Rejecting here costs no upstream calls.
2. **Account ownership** — does the caller actually control this Minecraft account?
3. **SkyBlock presence** — is that account online and in SkyBlock right now?

### Proving account ownership

The mechanism is Mojang's `joinServer` / `hasJoined` pair — the same exchange a real Minecraft server
uses to authenticate a joining player:

1. The server generates a single-use random `serverId` and returns it as a challenge.
2. The mod calls Mojang's `joinServer` with that `serverId`, using the player's session.
3. The mod sends the `serverId` to our server.
4. Our server asks Mojang's `hasJoined` whether that account completed a join for that `serverId`.

The server chooses the nonce, so the proof cannot be replayed. The profile Mojang returns is
authoritative — the token's subject comes from that, never from the client-supplied UUID.

### Why not just send the session id

`Minecraft.getInstance().getUser().getSessionId()` returns the access token embedded in a
`token:<accessToken>:<uuid>` string. **Do not send it to the server.** It is the account credential
itself, not a proof of ownership:

- Forwarding it puts full control of every player's Microsoft account into our server, our logs, and
  anything on the wire.
- Mods that ask for your session token are the best-known account-theft pattern in the ecosystem.
- It does not even work. Mojang exposes no endpoint letting a third party ask whose token a token is,
  so the server could only "verify" it by impersonating the player.

`joinServer`/`hasJoined` reaches the same goal with none of that exposure: the credential never
leaves the client, and the server learns only a yes/no plus the profile.

### Why SkyBlock presence, separately

Ownership proves *who*. It does not prove the player is actually playing, and data claiming to come
from a SkyBlock session should come from one. Hypixel's status endpoint answers `online` and
`gameType`; requiring `SKYBLOCK` also makes collection start and stop naturally as the player enters
and leaves.

Note the ordering carefully: **the Hypixel check alone proves nothing about identity.** It confirms
that *someone* is online — without the ownership step it is exactly the spoofable check described
above.

## Principles

- **Fail closed.** If Mojang or Hypixel cannot be reached, reject. A verification step that silently
  passes when the verifier is down can be bypassed by attacking the verifier.
- **Distinguish "rejected" from "could not verify"** in the response, so the client knows whether to
  tell the user something or to quietly retry.
- **Never log credentials** — session access tokens, the Hypixel API key, or issued tokens.
- **Short-lived tokens over revocation.** The planned JWT is signed with a keypair generated in
  memory at startup, so there is no long-lived secret to leak; a restart simply invalidates
  outstanding tokens and clients re-login.

## Where the detail lives

The full contract — status codes, machine-readable rejection codes, and the mod's behaviour for each
— is in #31 and #33, tracked together under #32.
