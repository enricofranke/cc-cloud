package ai.ezfox.cccloud.access;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Registry of {@link TeamProvider}s. Integrations register during mod construction; the server config
 * ({@code teams.providers}) can disable providers. Thread-safe.
 */
public final class TeamProviders {
    private static final Map<String, TeamProvider> PROVIDERS = new ConcurrentHashMap<>();
    /** Order in which providers are listed: the well-known ones first. */
    private static final List<String> ORDER = List.of("ftbteams", "opac", "scoreboard");
    private static volatile Supplier<Set<String>> disabled = Set::of;

    private TeamProviders() {
    }

    /** Registers (or replaces) a provider. */
    public static void register(TeamProvider provider) {
        PROVIDERS.put(provider.id(), provider);
    }

    public static void unregister(String id) {
        PROVIDERS.remove(id);
    }

    /** Config hook: ids of providers that must not be used. */
    public static void setDisabled(Supplier<Set<String>> supplier) {
        disabled = supplier;
    }

    /** An enabled and available provider, or null. */
    public static @Nullable TeamProvider get(@Nullable String id) {
        if (id == null) return null;
        var provider = PROVIDERS.get(id);
        if (provider == null || disabled.get().contains(id) || !provider.available()) return null;
        return provider;
    }

    /**
     * Whether {@code player} is in a team that {@code teamOf} is a member of, with any rank (owner, officer, member or
     * ally, by provider): FTB Teams parties and their allies, scoreboard teams, any registered provider. A team where
     * {@code teamOf} is only an ally is not theirs (allies are one way). Relays carry the networks of such players
     * (docs/SPEC.md "Relays belong to a team"). A failing provider counts as "not in the team". Pure.
     */
    public static boolean sameTeamOrAlly(Iterable<TeamProvider> providers, UUID teamOf, UUID player) {
        for (var provider : providers) {
            try {
                for (var team : provider.teamsOf(teamOf)) {
                    if ("ally".equals(provider.rankOf(team.id(), teamOf))) continue;
                    if (provider.rankOf(team.id(), player) != null) return true;
                }
            } catch (RuntimeException | LinkageError ignored) {
            }
        }
        return false;
    }

    /** Enabled and available providers in display order. */
    public static List<TeamProvider> enabled() {
        var list = new ArrayList<TeamProvider>();
        for (String id : ORDER) {
            var p = get(id);
            if (p != null) list.add(p);
        }
        for (var e : PROVIDERS.entrySet()) {
            if (ORDER.contains(e.getKey())) continue;
            var p = get(e.getKey());
            if (p != null) list.add(p);
        }
        return list;
    }
}
