# Setup

## Version matrix (tested combination)

| Component      | Version |
|----------------|---------|
| Gradle         | 8.9     |
| Android Gradle Plugin | 8.6.1 |
| Kotlin         | 2.0.21  |
| JDK            | 17      |
| compileSdk     | 35      |

These four move together. If you change one, check the others.

## If the Gradle distribution won't download

The wrapper needs one file from services.gradle.org the first time you build.
If that download fails, work through these in order.

**1. Retry on a different network.** A phone hotspot is the fastest test. Many
college and office networks block or throttle services.gradle.org, and this is
by far the most common cause.

**2. Try the alternate host.** In `gradle/wrapper/gradle-wrapper.properties`,
swap the distribution URL to:

    distributionUrl=https\://downloads.gradle.org/distributions/gradle-8.9-bin.zip

Same file, different front door. Sometimes one is reachable when the other isn't.

**3. Set your proxy.** If you're behind one, uncomment the proxy lines in
`gradle.properties` and fill in the host and port.

**4. Install the distribution by hand.** Download `gradle-8.9-bin.zip` in a
browser — any machine, any network, then copy it across if needed. Then in
Android Studio: Settings → Build, Execution, Deployment → Build Tools → Gradle
→ set *Use Gradle from* to **Specified location**, and point it at the unzipped
folder. This bypasses the wrapper download entirely and is the reliable escape
hatch when the network is the problem.

## Why 9.3.0 appeared in the error

The project shipped without a wrapper, so Android Studio fell back to its own
bundled default. That version is also too new for AGP 8.6.1 — Gradle 9 dropped
APIs the plugin still uses — so even a successful download would have failed at
the next step. The wrapper is now pinned, which fixes both halves.

If Studio offers to upgrade AGP and Gradle together, accepting is fine; just let
it move both, never one alone.
