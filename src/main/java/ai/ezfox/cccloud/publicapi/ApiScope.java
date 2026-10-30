package ai.ezfox.cccloud.publicapi;

import ai.ezfox.cccloud.access.Perm;
import java.util.EnumSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * What an API key may be used for (docs/SPEC.md v0.10 "Public API v1"). A request needs the scope <b>and</b> the
 * key owner's live permission: the scope only narrows the owner's rights, it never adds any.
 */
public enum ApiScope {
    /** Sections, widget values and the event stream ({@code view}; restricted sections also {@code view.positions}). */
    READ_SECTIONS("read:sections"),
    /** Item search and counts of storage cards ({@code storage.search}). */
    READ_STORAGE("read:storage"),
    /** Production statistics ({@code view}; storages also {@code storage.search}). */
    READ_STATS("read:stats"),
    /** Set a widget value / press a button (the widget's own action permission). */
    ACTION_WIDGETS("action:widgets"),
    /** Run scenes ({@code act.buttons} and every step's permission at run time). */
    RUN_SCENES("run:scenes");

    public final String id;

    ApiScope(String id) {
        this.id = id;
    }

    public static @Nullable ApiScope byId(@Nullable String id) {
        for (var s : values()) if (s.id.equals(id)) return s;
        return null;
    }

    /**
     * The owner's permissions a key with these scopes may use: reading needs view (+ positions for restricted sections),
     * storage search, the action permissions for widgets; scenes check every step against the owner's full rights.
     */
    public static Set<Perm> cap(Set<ApiScope> scopes) {
        var set = EnumSet.noneOf(Perm.class);
        if (!scopes.isEmpty()) set.add(Perm.VIEW);
        if (scopes.contains(READ_SECTIONS)) set.add(Perm.VIEW_POSITIONS);
        if (scopes.contains(READ_STORAGE) || scopes.contains(READ_STATS)) set.add(Perm.STORAGE_SEARCH);
        if (scopes.contains(ACTION_WIDGETS)) {
            set.add(Perm.ACT_BUTTONS);
            set.add(Perm.ACT_REDSTONE);
        }
        if (scopes.contains(RUN_SCENES)) set.add(Perm.ACT_BUTTONS);
        return set;
    }
}
