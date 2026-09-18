# Nexus stub

A Nexus, as much of one as the end-to-end suite needs: enough to find an artifact's assets, read one,
and serve its bytes.

It exists so the suite can assert a real download rather than reaching the live `eldonexus.de` on
every run. The client the backend uses fixes its scheme at `https` and sends no port, so this answers
TLS on 443 under a name its certificate covers, and the backend is handed the matching truststore
through `JAVA_OPTS`.

The certificate, its key and that truststore are made by the `tls_init_e2e` service when the stack
comes up, into a volume both containers share. Nothing is committed and nothing outlives
`docker compose --profile e2e down -v`, so there is no key in the repository to rotate or to leak.
The init runs to completion before either the stub or the backend starts, which is what keeps the two
from racing each other for a file neither has made yet.

`UserData.class` is the same fixture `JarUtilTest` uses. The proxy rewrites string constants in the
constant pool of `.class` entries and nothing else, so a jar of plain text would take the fallback
path and prove nothing about the rewrite.
