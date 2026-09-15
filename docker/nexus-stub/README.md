# Nexus stub

A Nexus, as much of one as the end-to-end suite needs: enough to find an artifact's assets, read one,
and serve its bytes.

It exists so the suite can assert a real download rather than reaching the live `eldonexus.de` on
every run. The client the backend uses fixes its scheme at `https` and sends no port, so this answers
TLS on 443 under a name its certificate covers, and the backend is handed the matching truststore
through `JAVA_OPTS`.

`tls/` holds a self-signed certificate, its key and a truststore built from it. They are fixtures for
a container that only ever talks to another container on the same compose network, and they protect
nothing — do not reuse them anywhere else.

`UserData.class` is the same fixture `JarUtilTest` uses. The proxy rewrites string constants in the
constant pool of `.class` entries and nothing else, so a jar of plain text would take the fallback
path and prove nothing about the rewrite.
