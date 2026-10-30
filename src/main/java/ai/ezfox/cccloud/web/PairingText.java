package ai.ezfox.cccloud.web;

import java.net.URI;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * Chat components for pairing codes: the code copies itself on click, and "[Pair in browser]" opens
 * {@code <dashboard>/pair#code=XXXX-XXXX}. The code is in the URL fragment, so browsers never send it to the server
 * (no logs, no Referer); the dashboard reads it from {@code location.hash} and clears it.
 */
public final class PairingText {
    private PairingText() {
    }

    /** The URL that opens the pairing form with the code filled in, or null without a dashboard URL. */
    public static @Nullable String pairUrl(@Nullable String dashboardUrl, String formattedCode) {
        if (dashboardUrl == null || dashboardUrl.isBlank()) return null;
        String base = dashboardUrl.endsWith("/") ? dashboardUrl.substring(0, dashboardUrl.length() - 1) : dashboardUrl;
        if (!formattedCode.matches("[A-Z0-9]{4}-[A-Z0-9]{4}")) return null;
        return base + "/pair#code=" + formattedCode;
    }

    /**
     * The dashboard URL that opens a mainframe and scrolls to one card ({@code #/m/<mainframe>?card=<section>}), or null
     * without a dashboard URL or with ids that are not UUIDs.
     */
    public static @Nullable String cardUrl(@Nullable String dashboardUrl, String mainframeId, String sectionId) {
        if (dashboardUrl == null || dashboardUrl.isBlank()) return null;
        var uuid = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
        if (!mainframeId.matches(uuid) || !sectionId.matches(uuid)) return null;
        String base = dashboardUrl.endsWith("/") ? dashboardUrl.substring(0, dashboardUrl.length() - 1) : dashboardUrl;
        return base + "/#/m/" + mainframeId + "?card=" + sectionId;
    }

    /** " [label]" in aqua, underlined, opening {@code url} on click (empty if the URL is invalid). */
    public static MutableComponent openLink(String url, Component label) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return Component.empty();
        }
        return Component.literal(" ").append(label.copy().withStyle(style -> style.withColor(ChatFormatting.AQUA)
            .withUnderlined(true).withClickEvent(new ClickEvent.OpenUrl(uri))
            .withHoverEvent(new HoverEvent.ShowText(Component.literal(url)))));
    }

    /** The code in gold/bold; click copies it. */
    public static MutableComponent code(String formattedCode) {
        return Component.literal(formattedCode).withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true)
            .withClickEvent(new ClickEvent.CopyToClipboard(formattedCode))
            .withHoverEvent(new HoverEvent.ShowText(Component.translatable("cccloud.pairing.copy_hover"))));
    }

    /** "[Pair in browser]" for the dashboard URL, or an empty component if there is none. */
    public static MutableComponent pairLink(@Nullable String dashboardUrl, String formattedCode) {
        String url = pairUrl(dashboardUrl, formattedCode);
        if (url == null) return Component.empty();
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return Component.empty();
        }
        return Component.literal(" ").append(Component.translatable("cccloud.pairing.open").withStyle(style -> style.withColor(ChatFormatting.AQUA)
            .withUnderlined(true).withClickEvent(new ClickEvent.OpenUrl(uri))
            .withHoverEvent(new HoverEvent.ShowText(Component.literal(url)))));
    }
}
