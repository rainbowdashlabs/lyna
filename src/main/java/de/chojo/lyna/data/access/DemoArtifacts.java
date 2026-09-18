package de.chojo.lyna.data.access;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * What the demo seed made, so that a reset can take back exactly that and nothing else.
 *
 * <p>A demo instance is not supposed to hold anything real, but "not supposed to" is not a reason to
 * write a reset that truncates tables. Recording each row as it is created means the reset is a list
 * of things to remove rather than a guess about what was there first.
 */
public class DemoArtifacts {
    /** The kinds a reset knows how to take back, in the order it has to walk them. */
    public static final String ACCOUNT = "account";
    public static final String PRODUCT = "product";
    public static final String DOWNLOAD_TYPE = "download_type";

    public void record(String kind, String artifact) {
        query("""
                INSERT INTO demo_artifact (kind, artifact) VALUES (?, ?)
                ON CONFLICT (kind, artifact) DO NOTHING
                """)
                .single(call().bind(kind).bind(artifact))
                .insert();
    }

    /**
     * @param kind which kind to list
     * @return what the seed made of that kind, oldest first
     */
    public List<String> of(String kind) {
        return query("SELECT artifact FROM demo_artifact WHERE kind = ? ORDER BY created_at")
                .single(call().bind(kind))
                .map(row -> row.getString("artifact"))
                .all();
    }

    /**
     * Forgets everything recorded. Called once the rows themselves are gone.
     */
    public void clear() {
        query("DELETE FROM demo_artifact").single(call()).delete();
    }

    /**
     * @return whether a seed has ever run here
     */
    public boolean seeded() {
        return query("SELECT 1 FROM demo_artifact LIMIT 1")
                .single(call())
                .map(row -> Boolean.TRUE)
                .first()
                .orElse(Boolean.FALSE);
    }
}
