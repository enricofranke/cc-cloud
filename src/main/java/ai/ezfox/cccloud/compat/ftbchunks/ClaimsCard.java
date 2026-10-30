package ai.ezfox.cccloud.compat.ftbchunks;

import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.compat.IntegrationConfig;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.server.MainframeCard;
import ai.ezfox.cccloud.compat.server.MainframeCards;
import ai.ezfox.cccloud.uplink.Widget;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.property.TeamProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * FTB Chunks: a "Team claims" mainframe card (canControl devices only, since it carries coordinates) with the
 * claimed and force-loaded chunks of the mainframe owner's team. The chunk list itself is the overlay data for the
 * network map: the section snapshot carries {@code "claims":{"team","color","chunks":[{"dim","x","z","forced"}],
 * "truncated"}} (docs/SPEC.md v0.5 B). Re-read every 10 s; the snapshot is re-sent only when the claims change.
 */
public final class ClaimsCard extends MainframeCard {
    public static final String KIND = "claims";
    /** Chunks in the overlay at most (≈ 40 bytes each). */
    public static final int MAX_CHUNKS = 2048;

    record Chunk(String dim, int x, int z, boolean forced) {
    }

    private final CloudMainframeBlockEntity mainframe;
    private List<Chunk> chunks = List.of();
    private boolean truncated;
    private String teamName = "";
    private int color = 0xFFFFFF;
    private int maxClaims;
    private int maxForced;
    private int revision;
    private int refreshes;

    private ClaimsCard(CloudMainframeBlockEntity mainframe) {
        this.mainframe = mainframe;
    }

    public static void init() {
        FtbClaims.init();
        MainframeCards.register(KIND, () -> IntegrationConfig.get(IntegrationConfig.FTB_CHUNKS_CLAIMS), ClaimsCard::new);
        ai.ezfox.cccloud.compat.Integrations.test("ftbchunks_claims", () -> FtbGameTests::claims);
        ai.ezfox.cccloud.compat.Integrations.test("ftbchunks_claim_guard", () -> FtbGameTests::claimGuard);
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public String title() {
        return "Team claims";
    }

    @Override
    public List<Widget> widgets() {
        return new CardWidgets()
            .label("team", "Team", "icon", "shield")
            .label("claimed", "Claimed chunks")
            .label("forced", "Force-loaded chunks")
            .label("dimensions", "Claims per dimension", "span", 2)
            .build();
    }

    @Override
    public int revision() {
        return revision;
    }

    /** Reads the owner team's claims (server thread). */
    void reload() {
        var owner = mainframe.getOwner();
        var chunksApi = FTBChunksAPI.api();
        var teamsApi = FTBTeamsAPI.api();
        List<Chunk> list = new ArrayList<>();
        String name = "";
        int teamColor = 0xFFFFFF;
        int claims = 0, forced = 0;
        if (owner != null && chunksApi.isManagerLoaded() && teamsApi.isManagerLoaded()) {
            var team = teamsApi.getManager().getTeamForPlayerID(owner).orElse(null);
            if (team != null) {
                name = team.getShortName();
                try {
                    teamColor = team.getProperty(TeamProperties.COLOR).rgb() & 0xFFFFFF;
                } catch (RuntimeException ignored) {
                }
                var data = chunksApi.getManager().getOrCreateData(team);
                claims = data.getMaxClaimChunks();
                forced = data.getMaxForceLoadChunks();
                for (ClaimedChunk chunk : data.getClaimedChunks()) {
                    var pos = chunk.getPos();
                    list.add(new Chunk(pos.dimension().identifier().toString(), pos.x(), pos.z(), chunk.isForceLoaded()));
                }
            }
        }
        list.sort(Comparator.comparing(Chunk::dim).thenComparingInt(Chunk::x).thenComparingInt(Chunk::z));
        boolean cut = list.size() > MAX_CHUNKS;
        if (cut) list = new ArrayList<>(list.subList(0, MAX_CHUNKS));
        int hash = (name.hashCode() * 31 + teamColor) * 31 + list.hashCode();
        chunks = list;
        truncated = cut;
        teamName = name;
        color = teamColor;
        maxClaims = claims;
        maxForced = forced;
        revision = hash & 0x7fffffff;
    }

    @Override
    public void refresh(CardOutput out) {
        if (refreshes++ % 10 == 0) reload();
        long forced = chunks.stream().filter(Chunk::forced).count();
        out.text("team", teamName.isEmpty() ? "No team (owner unknown)" : teamName);
        out.text("claimed", chunks.size() + (truncated ? "+" : "") + (maxClaims > 0 ? " / " + maxClaims : ""));
        out.text("forced", forced + (maxForced > 0 ? " / " + maxForced : ""));
        var perDim = new TreeMap<String, Integer>();
        for (var chunk : chunks) perDim.merge(chunk.dim().substring(chunk.dim().indexOf(':') + 1), 1, Integer::sum);
        var parts = new ArrayList<String>();
        perDim.forEach((dim, n) -> parts.add(dim + " " + n));
        out.text("dimensions", parts.isEmpty() ? "–" : String.join(" · ", parts));
    }

    @Override
    public void snapshotExtras(JsonObject snapshot) {
        var claims = new JsonObject();
        claims.addProperty("team", teamName);
        claims.addProperty("color", String.format(Locale.ROOT, "#%06x", color));
        var array = new JsonArray(chunks.size());
        for (var chunk : chunks) {
            var c = new JsonObject();
            c.addProperty("dim", chunk.dim());
            c.addProperty("x", chunk.x());
            c.addProperty("z", chunk.z());
            if (chunk.forced()) c.addProperty("forced", true);
            array.add(c);
        }
        claims.add("chunks", array);
        claims.addProperty("truncated", truncated);
        snapshot.add("claims", claims);
    }

    /** Test hook. */
    @Nullable List<Chunk> chunks() {
        return chunks;
    }
}
