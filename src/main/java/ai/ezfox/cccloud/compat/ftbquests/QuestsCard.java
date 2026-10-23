package ai.ezfox.cccloud.compat.ftbquests;

import ai.ezfox.cccloud.block.entity.CloudMainframeBlockEntity;
import ai.ezfox.cccloud.compat.IntegrationConfig;
import ai.ezfox.cccloud.compat.card.CardWidgets;
import ai.ezfox.cccloud.compat.card.Fmt;
import ai.ezfox.cccloud.compat.server.MainframeCard;
import ai.ezfox.cccloud.compat.server.MainframeCards;
import ai.ezfox.cccloud.uplink.Widget;
import dev.ftb.mods.ftbquests.api.FTBQuestsAPI;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * FTB Quests (docs/SPEC.md v0.8 item 8): a "Quests" mainframe card with the progress of the mainframe owner's team –
 * overall %, chapters done / total, quests done / total and the most recent completions; an info alert for each new
 * completion. Restricted section ({@code view.positions}): quest progress is private to the team. Read every 10 s.
 */
public final class QuestsCard extends MainframeCard {
    public static final String KIND = "quests";
    public static final String TEAM = "team";
    public static final String PROGRESS = "progress";
    public static final String CHAPTERS = "chapters";
    public static final String QUESTS = "quests";
    public static final String RECENT = "recent";
    /** Shown instead of the completion rows while nothing is completed. */
    public static final String EMPTY = "empty";
    static final int RECENT_ROWS = 5;

    private record Done(String title, String chapter, long time) {
    }

    private final CloudMainframeBlockEntity mainframe;
    private int refreshes;
    private long lastCompletion = -1;
    /** Completion rows currently in the widget set (0 = the "No quests completed yet" line). */
    private int rows;

    QuestsCard(CloudMainframeBlockEntity mainframe) {
        this.mainframe = mainframe;
    }

    public static void init() {
        MainframeCards.register(KIND, () -> IntegrationConfig.get(IntegrationConfig.QUESTS_CARD), QuestsCard::new);
        ai.ezfox.cccloud.compat.Integrations.test("ftbquests_card", () -> QuestsGameTests::card);
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public String title() {
        return "Quests";
    }

    @Override
    public List<Widget> widgets() {
        var w = new CardWidgets()
            .label(TEAM, "Team", "icon", "star")
            .progress(PROGRESS, "Progress", 0, 100, "%")
            .label(CHAPTERS, "Chapters done")
            .label(QUESTS, "Quests done");
        if (rows == 0) w.label(EMPTY, "Completed", "span", 2);
        for (int i = 1; i <= rows; i++) w.label(RECENT + i, i == 1 ? "Last completed" : "Completed before", "span", 2);
        return w.build();
    }

    /** The widget set follows the number of completions shown (only rows with data). */
    @Override
    public int revision() {
        return rows;
    }

    static String title(QuestObjectBase object) {
        try {
            String title = object.getTitle().getString();
            if (!title.isBlank()) return title;
        } catch (RuntimeException ignored) {
        }
        String raw = object.getRawTitle();
        return raw == null || raw.isBlank() ? object.getCodeString() : raw;
    }

    @Override
    public void refresh(CardOutput out) {
        if (refreshes++ % 10 != 0) return;
        var owner = mainframe.getOwner();
        var teams = FTBTeamsAPI.api();
        var team = owner == null || !teams.isManagerLoaded() ? null : teams.getManager().getTeamForPlayerID(owner).orElse(null);
        var file = FTBQuestsAPI.api().getQuestFile(false);
        TeamData data = team == null || file == null ? null : file.getNullableTeamData(team.getId());
        out.text(TEAM, team == null ? "No team (owner unknown)" : team.getShortName());
        if (data == null) {
            out.number(PROGRESS, 0);
            out.text(CHAPTERS, "–");
            out.text(QUESTS, "–");
            rows = 0;
            out.text(EMPTY, "No quests completed yet");
            return;
        }
        int[] chapters = {0, 0};
        file.forAllChapters(chapter -> {
            if (chapter.isAlwaysInvisible() || chapter.getQuests().isEmpty()) return;
            chapters[1]++;
            if (data.isCompleted(chapter)) chapters[0]++;
        });
        int[] quests = {0, 0};
        var done = new ArrayList<Done>();
        file.forAllQuests(quest -> {
            quests[1]++;
            if (!data.isCompleted(quest)) return;
            quests[0]++;
            long time = data.getCompletedTime(quest.id).map(Date::getTime).orElse(0L);
            done.add(new Done(title(quest), chapterTitle(quest), time));
        });
        done.sort(Comparator.comparingLong(Done::time).reversed());
        out.number(PROGRESS, Math.round(Fmt.percent(quests[0], quests[1])));
        out.text(CHAPTERS, chapters[0] + " / " + chapters[1]);
        out.text(QUESTS, quests[0] + " / " + quests[1]);
        rows = Math.min(RECENT_ROWS, done.size());
        if (rows == 0) out.text(EMPTY, "No quests completed yet");
        for (int i = 1; i <= rows; i++) {
            var d = done.get(i - 1);
            long ago = d.time() > 0 ? Math.max(0, (System.currentTimeMillis() - d.time()) / 1000) : -1;
            out.text(RECENT + i, d.title() + " (" + d.chapter() + ")" + (ago >= 0 ? " – " + Fmt.duration(ago) + " ago" : ""));
        }
        long newest = done.isEmpty() ? 0 : done.getFirst().time();
        if (lastCompletion >= 0 && newest > lastCompletion) out.alert("Quest completed: " + done.getFirst().title(), "info");
        lastCompletion = newest;
    }

    private static String chapterTitle(Quest quest) {
        try {
            return title(quest.getChapter());
        } catch (RuntimeException e) {
            return "?";
        }
    }
}
