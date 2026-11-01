package ai.ezfox.cccloud.reports;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * A stack trace reduced to what a report may hold: exception class names, sanitised messages and frames. Frames of this
 * mod and of the mods it integrates keep method and line; every other frame is only its class name. Comes from a live
 * {@link Throwable} or from the text of a crash report.
 */
public final class ReportStack {
    public static final String OWN_PACKAGE = "ai.ezfox.cccloud.";
    /** Causes in a report and frames per cause. */
    static final int MAX_CAUSES = 4, MAX_FRAMES = 48;
    /** The frames of this mod that make up the fingerprint: line numbers stay out, so a report survives small edits. */
    private static final int FINGERPRINT_FRAMES = 12;

    /** Mods with an integration in {@code compat/}, whose frames say something about this mod's use of them. */
    static final List<String> KNOWN_PACKAGES = List.of("dan200.computercraft.", "cc.tweaked.", "appeng.", "com.refinedmods.", "dev.ftb.mods.", "snownee.jade.",
        "org.cyclops.", "net.blay09.mods.", "com.direwolf20.", "mods.railcraft.", "owmii.powah.", "de.maxhenkel.", "dev.latvian.mods.", "net.silentchaos512.",
        "com.yogpc.qp.", "com.breakinblocks.neovitae.", "com.blakebr0.", "cy.jdkdigital.", "dev.su5ed.mffs.", "com.almostreliable.", "me.jddev0.ep.",
        "me.desht.modularrouters.", "dev.shadowsoffire.", "com.enderio.", "rearth.oritech.", "net.permutated.pylons.", "net.pedroksl.advanced_ae.",
        "com.leclowndu93150.", "guideme.", "top.theillusivec4.curios.");

    /** Messages of these exceptions quote Lua code or a player's program, so they are never kept. */
    private static final List<String> NO_MESSAGE = List.of("dan200.computercraft.api.lua.LuaException", "dan200.computercraft.core.", "cc.tweaked.internal.",
        "org.squiddev.cobalt.");

    private static final Pattern FRAME_LINE = Pattern.compile("^\\s*at\\s+(?:[^\\s(/]+/)*([\\p{L}\\p{N}_$.]+)\\.([\\p{L}\\p{N}_$<>]+)\\(([^)]*)\\).*$");
    private static final Pattern THROWABLE_LINE = Pattern.compile("^(?:Caused by:\\s*)?([\\p{L}_$][\\p{L}\\p{N}_$]*(?:\\.[\\p{L}_$][\\p{L}\\p{N}_$]*)+)(?::\\s*(.*))?$");
    private static final Pattern LINE_NUMBER = Pattern.compile(":(\\d{1,7})$");
    private static final Pattern HIDDEN_SUFFIX = Pattern.compile("/0x[0-9a-fA-F]+");

    /** One frame; {@code method} is null when only the class is kept. */
    public record Frame(String cls, @Nullable String method, int line) {
        boolean own() {
            return cls.startsWith(OWN_PACKAGE);
        }

        /** {@code class.method:line}, or the class name alone. */
        String text() {
            if (method == null) return cls;
            return cls + "." + method + (line > 0 ? ":" + line : "");
        }
    }

    /** One exception of the chain, outermost first; {@code message} is raw until {@link ReportPayload} scrubs it. */
    public record Cause(String cls, @Nullable String message, List<Frame> frames) {
    }

    private final List<Cause> causes;

    private ReportStack(List<Cause> causes) {
        this.causes = causes;
    }

    public List<Cause> causes() {
        return causes;
    }

    /** From a live exception (the chain of causes, cycles stop it). */
    public static ReportStack of(Throwable throwable) {
        var causes = new ArrayList<Cause>();
        Set<Throwable> seen = new HashSet<>();
        for (Throwable t = throwable; t != null && causes.size() < MAX_CAUSES && seen.add(t); t = t.getCause()) {
            var frames = new ArrayList<Frame>();
            for (var element : t.getStackTrace()) add(frames, element.getClassName(), element.getMethodName(), element.getLineNumber());
            causes.add(new Cause(t.getClass().getName(), keptMessage(t.getClass().getName(), t.getMessage()), frames));
        }
        return new ReportStack(causes);
    }

    /**
     * From the text of a Minecraft crash report. Only the stack trace at its top is read: it ends at the "detailed
     * walkthrough", so system details, mod lists, paths and player data below it are never touched. Null if there is none.
     */
    public static @Nullable ReportStack parse(String report) {
        var causes = new ArrayList<Cause>();
        @Nullable String cls = null, message = null;
        List<Frame> frames = new ArrayList<>();
        boolean started = false;
        for (String raw : report.split("\\R", -1)) {
            if (raw.startsWith("A detailed walkthrough") || raw.startsWith("-- Head --")) break;
            if (!started) {
                // the header ends with "Description: ..." and an empty line
                if (raw.startsWith("Description:")) started = true;
                continue;
            }
            var frame = FRAME_LINE.matcher(raw);
            if (frame.matches()) {
                if (cls != null) {
                    var line = LINE_NUMBER.matcher(frame.group(3));
                    add(frames, frame.group(1), frame.group(2), line.find() ? Integer.parseInt(line.group(1)) : -1);
                }
                continue;
            }
            var head = THROWABLE_LINE.matcher(raw.strip());
            if (head.matches()) {
                if (cls != null) causes.add(new Cause(cls, keptMessage(cls, message), frames));
                cls = head.group(1);
                message = head.group(2);
                frames = new ArrayList<>();
            }
        }
        if (cls != null) causes.add(new Cause(cls, keptMessage(cls, message), frames));
        if (causes.isEmpty()) return null;
        return new ReportStack(causes.size() > MAX_CAUSES ? List.copyOf(causes.subList(0, MAX_CAUSES)) : causes);
    }

    private static void add(List<Frame> frames, String cls, String method, int line) {
        if (frames.size() >= MAX_FRAMES) return;
        String name = HIDDEN_SUFFIX.matcher(cls).replaceAll("");
        var frame = detailed(name) ? new Frame(name, method, line) : new Frame(name, null, -1);
        // long runs of the same class are recursion or reflection, one is enough
        if (!frames.isEmpty() && frame.method() == null && frames.getLast().equals(frame)) return;
        frames.add(frame);
    }

    private static boolean detailed(String cls) {
        if (cls.startsWith(OWN_PACKAGE)) return true;
        for (String prefix : KNOWN_PACKAGES) if (cls.startsWith(prefix)) return true;
        return false;
    }

    private static @Nullable String keptMessage(String cls, @Nullable String message) {
        for (String prefix : NO_MESSAGE) if (cls.startsWith(prefix)) return null;
        return message;
    }

    /** Whether this mod's code is on the stack; every other failure belongs to someone else and is not reported. */
    public boolean ours() {
        for (var cause : causes) {
            for (var frame : cause.frames()) if (frame.own()) return true;
        }
        return false;
    }

    /**
     * 16 hex digits of a SHA-256 over the innermost exception's class and the top frames of this mod: the same bug gets
     * the same fingerprint on every server and in every build, and it is computed from code names only.
     */
    public String fingerprint() {
        var root = causes.getLast();
        var text = new StringBuilder(root.cls());
        int count = 0;
        // the innermost cause first: that is where it broke; wrappers change more often than the bug
        for (int i = causes.size() - 1; i >= 0 && count < FINGERPRINT_FRAMES; i--) {
            for (var frame : causes.get(i).frames()) {
                if (!frame.own() || count == FINGERPRINT_FRAMES) continue;
                text.append('\n').append(frame.cls()).append('.').append(frame.method());
                count++;
            }
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
