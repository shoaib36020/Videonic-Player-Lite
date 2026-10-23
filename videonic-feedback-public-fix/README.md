# Videonic Lite feedback screen — production endpoint update

This patch replaces the development-only feedback endpoint input with the fixed HTTPS API URL:
`https://wheat-stork-207923.hostingersite.com/api/feedback`.

It does **not** embed SMTP secrets, change the Hostinger backend, change the Android package name, or affect other screens.

## Apply in Codespaces
Upload `videonic-feedback-public-fix.zip` to `/workspaces/Videonic-Player-Lite`, then:

```bash
cd /workspaces/Videonic-Player-Lite
unzip -o videonic-feedback-public-fix.zip -d .
bash videonic-feedback-public-fix/APPLY.sh
```

## Check compilation (no phone installation needed)

```bash
gradle :app:compileDebugKotlin --no-daemon --max-workers=1 --no-parallel --no-configuration-cache -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC" -Pkotlin.compiler.execution.strategy=in-process
```

If compilation succeeds, build APK with `:app:assembleDebug` using the same arguments. Test a form submission on a device, then check the recipient inbox. `/health` only proves server availability, not SMTP delivery.

## Security and operational notes
* Endpoint URL inside an APK is public information. Keep SMTP credentials on Hostinger only.
* The feedback server must remain protected against spam and abuse; current lightweight in-memory per-IP limits are not reliable for large public usage.
* Privacy Policy must disclose the processing of messages, optional email, device metadata and attached screenshots.
* `PORT=3306` should not be configured for the Node.js app. Use hosting-provided `PORT` or the server default.
