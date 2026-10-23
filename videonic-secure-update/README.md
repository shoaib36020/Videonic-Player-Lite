# Videonic secure folder and feedback update

Apply to **the already-merged major update**, not the earlier 989-line MainActivity. The script checks expected anchors and makes a timestamped backup. If it cannot find them, it stops without replacing MainActivity.

1. Upload ZIP to Codespaces root, unzip, then `bash videonic-secure-update/APPLY.sh`.
2. Compile Kotlin before making APK. Follow existing Gradle low-memory command.
3. Deploy `videonic-secure-update/backend/` to an HTTPS Node.js host with `npm install`, `npm start`. Configure environment variables from `.env.example` in host secret manager; never include passwords in your APK or repository.
4. The Support & Feedback page takes your HTTPS address, e.g. `https://support.example.com/api/feedback`.

## Security limits (important)
* Private Folder makes AES-256-GCM encrypted **copies** using a key derived from a user PIN. It does not automatically delete/hide original device videos: users must choose the system Trash action separately. Never claim otherwise.
* Video playback temporarily decrypts to an app-cache file. The cache is removed when private playback is closed normally and at next Private Folder opening, but crashes may leave it until then. Thus this is a practical privacy feature, not hardened DRM or protection against rooted devices. There is no PIN recovery; deleting app data destroys the vault.
* Do not apply this patch if you require biometric-grade vault isolation, instant seek without plaintext cache, or tamper-resistant security without further engineering.
* Feedback screenshots are optional (max 5 at 2 MB each); backend does not authenticate senders. IP-based rate limits are in-memory and reset on restart. Add persistent abuse defenses and spam filtering before a broad Play release.
* Review privacy disclosures and publish a real publicly accessible privacy policy covering submission data/attachments; generic legal text is insufficient.
