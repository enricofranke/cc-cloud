package ai.ezfox.cccloud.computer;

import ai.ezfox.cccloud.code.api.ApiError;
import ai.ezfox.cccloud.code.api.HostModule;
import java.util.ArrayList;
import java.util.List;

/**
 * The Java side of a computer program's APIs, as {@link HostModule}s: {@code term}, {@code os} (added to the sandbox's
 * {@code os}), {@code fs}, {@code redstone} and the hidden {@code __native} table the boot code takes and removes
 * (event wait, file handles, game time, power). Names follow the usual computer-mod style; the code is our own.
 * Everything else ({@code print}, {@code read}, {@code sleep}, {@code textutils}, {@code colors}, {@code keys},
 * {@code shell}) is Lua in the rom.
 */
final class ComputerApis {
    private ComputerApis() {
    }

    static List<HostModule> modules(Computer computer, ComputerRuntime runtime, FileHandles files) {
        return List.of(term(computer.terminal()), os(computer, runtime), fs(computer.fs()), redstone(computer), peripheral(computer.peripherals()),
            natives(computer, runtime, files));
    }

    // ---- term ----------------------------------------------------------------------------------------------------------

    static HostModule term(ComputerTerminal t) {
        var b = HostModule.builder("term");
        b.fn("write", a -> {
            t.write(a.string(0));
            return null;
        });
        b.fn("blit", a -> {
            String text = a.string(0), fg = a.string(1), bg = a.string(2);
            try {
                t.blit(text, fg, bg);
            } catch (IllegalArgumentException e) {
                throw new ApiError(e.getMessage());
            }
            return null;
        });
        b.fn("clear", a -> {
            t.clear();
            return null;
        });
        b.fn("clearLine", a -> {
            t.clearLine();
            return null;
        });
        b.fn("getCursorPos", a -> new Object[]{(long) t.cursorX() + 1, (long) t.cursorY() + 1});
        b.fn("setCursorPos", a -> {
            int x = (int) a.integer(0, Integer.MIN_VALUE / 2, Integer.MAX_VALUE / 2), y = (int) a.integer(1, Integer.MIN_VALUE / 2, Integer.MAX_VALUE / 2);
            t.setCursor(x - 1, y - 1);
            return null;
        });
        b.fn("getCursorBlink", a -> t.blink());
        b.fn("setCursorBlink", a -> {
            t.setBlink(a.bool(0));
            return null;
        });
        b.fn("getSize", a -> new Object[]{(long) t.width(), (long) t.height()});
        b.fn("scroll", a -> {
            t.scroll((int) a.integer(0, -10_000, 10_000));
            return null;
        });
        HostModule.Function setText = a -> {
            t.setTextColour(colour(a, 0));
            return null;
        };
        HostModule.Function getText = a -> 1L << t.textColour();
        HostModule.Function setBack = a -> {
            t.setBackgroundColour(colour(a, 0));
            return null;
        };
        HostModule.Function getBack = a -> 1L << t.backgroundColour();
        HostModule.Function isColour = a -> t.colour();
        HostModule.Function setPalette = a -> {
            int c = colour(a, 0);
            int rgb;
            if (a.count() <= 2) {
                rgb = (int) a.integer(1, 0, 0xFFFFFF);
            } else {
                rgb = channel(a, 1) << 16 | channel(a, 2) << 8 | channel(a, 3);
            }
            t.setPalette(c, rgb);
            return null;
        };
        HostModule.Function getPalette = a -> rgb(t.palette(colour(a, 0)));
        HostModule.Function nativePalette = a -> rgb(ComputerTerminal.defaultPalette(colour(a, 0)));
        for (String suffix : new String[]{"Colour", "Color"}) {
            b.fn("setText" + suffix, setText).fn("getText" + suffix, getText).fn("setBackground" + suffix, setBack).fn("getBackground" + suffix, getBack)
                .fn("is" + suffix, isColour).fn("setPalette" + suffix, setPalette).fn("getPalette" + suffix, getPalette)
                .fn("nativePalette" + suffix, nativePalette);
        }
        b.fn("getLine", a -> {
            int y = (int) a.integer(0, 1, t.height()) - 1;
            return new Object[]{t.line(y), t.foreground(y), t.background(y)};
        });
        return b.build();
    }

    /** A colour value of the {@code colors} API (a power of two) → palette index; the highest bit counts. */
    static int colour(HostModule.Arguments a, int i) {
        long c = a.integer(i, 1, 0xFFFF);
        return 63 - Long.numberOfLeadingZeros(c);
    }

    private static int channel(HostModule.Arguments a, int i) {
        double v = a.number(i);
        if (Double.isNaN(v)) throw a.bad(i, "number expected, got nan");
        return (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
    }

    private static Object[] rgb(int rgb) {
        return new Object[]{((rgb >> 16) & 0xff) / 255.0, ((rgb >> 8) & 0xff) / 255.0, (rgb & 0xff) / 255.0};
    }

    // ---- os ------------------------------------------------------------------------------------------------------------

    static HostModule os(Computer computer, ComputerRuntime runtime) {
        var b = HostModule.builder("os");
        b.fn("queueEvent", a -> {
            String name = a.string(0);
            if (name.isEmpty() || name.length() > 64) throw a.bad(0, "event name must be 1-64 characters");
            var args = new Object[Math.max(0, Math.min(16, a.count() - 1))];
            for (int i = 0; i < args.length; i++) args[i] = a.get(i + 1);
            runtime.queue(name, args);
            return null;
        });
        b.fn("startTimer", a -> (long) runtime.startTimer(a.number(0)));
        b.fn("cancelTimer", a -> {
            runtime.cancelTimer((int) a.integer(0, Integer.MIN_VALUE, Integer.MAX_VALUE));
            return null;
        });
        HostModule.Function id = a -> (long) computer.id();
        HostModule.Function label = a -> computer.label();
        b.fn("getComputerID", id).fn("computerID", id).fn("getComputerLabel", label).fn("computerLabel", label);
        b.fn("setComputerLabel", a -> {
            String l = a.optString(0);
            if (l != null && l.length() > ComputerRegistry.MAX_LABEL) throw a.bad(0, "label too long (at most " + ComputerRegistry.MAX_LABEL + ")");
            computer.setLabelFromProgram(l);
            return null;
        });
        b.fn("epoch", a -> {
            String kind = a.has(0) ? a.string(0) : "ingame";
            return switch (kind) {
                case "utc", "local" -> System.currentTimeMillis();
                case "ingame" -> computer.gameDay() * 86_400_000L + (long) (computer.gameTimeOfDay() / 24000.0 * 86_400_000L);
                default -> throw a.bad(0, "unsupported operation");
            };
        });
        b.fn("day", a -> {
            String kind = a.has(0) ? a.string(0) : "ingame";
            return switch (kind) {
                case "utc", "local" -> System.currentTimeMillis() / 86_400_000L;
                case "ingame" -> computer.gameDay();
                default -> throw a.bad(0, "unsupported operation");
            };
        });
        b.fn("shutdown", a -> {
            computer.requestPower(Computer.Power.SHUTDOWN);
            return null; // the rom's os.shutdown then waits until the next tick stops the program
        });
        b.fn("reboot", a -> {
            computer.requestPower(Computer.Power.REBOOT);
            return null;
        });
        return b.build();
    }

    // ---- fs ------------------------------------------------------------------------------------------------------------

    static HostModule fs(ComputerFs fs) {
        var b = HostModule.builder("fs");
        b.fn("list", a -> fs.list(a.string(0)));
        b.fn("exists", a -> fs.exists(a.string(0)));
        b.fn("isDir", a -> fs.isDir(a.string(0)));
        b.fn("isReadOnly", a -> fs.isReadOnly(a.string(0)));
        b.fn("getSize", a -> {
            String p = a.string(0);
            if (!fs.exists(p)) throw new ApiError("/" + ComputerFs.normalize(p) + ": No such file");
            return fs.size(p);
        });
        b.fn("getFreeSpace", a -> {
            a.string(0);
            return fs.isReadOnly(a.string(0)) ? 0L : fs.freeSpace();
        });
        b.fn("getCapacity", a -> fs.isReadOnly(a.string(0)) ? null : fs.capacity());
        b.fn("getDrive", a -> {
            String p = a.string(0);
            if (!fs.exists(p)) return null;
            return fs.isReadOnly(p) ? "rom" : "hdd";
        });
        b.fn("makeDir", a -> {
            fs.makeDir(a.string(0));
            return null;
        });
        b.fn("delete", a -> {
            fs.delete(a.string(0));
            return null;
        });
        b.fn("move", a -> {
            fs.move(a.string(0), a.string(1));
            return null;
        });
        b.fn("copy", a -> {
            fs.copy(a.string(0), a.string(1));
            return null;
        });
        b.fn("combine", a -> {
            var sb = new StringBuilder(a.string(0));
            for (int i = 1; i < a.count(); i++) sb.append('/').append(a.string(i));
            return ComputerFs.normalize(sb.toString());
        });
        b.fn("getName", a -> ComputerFs.name(a.string(0)));
        b.fn("getDir", a -> {
            String p = ComputerFs.normalize(a.string(0));
            return p.isEmpty() ? ".." : ComputerFs.dir(p);
        });
        return b.build();
    }

    // ---- redstone ------------------------------------------------------------------------------------------------------

    static HostModule redstone(Computer computer) {
        var io = computer.redstone();
        var b = HostModule.builder("redstone");
        b.fn("getSides", a -> {
            var out = new ArrayList<Object>();
            for (var s : ComputerSide.all()) out.add(s.id());
            return out;
        });
        b.fn("getInput", a -> io.input(side(a)) > 0);
        b.fn("getOutput", a -> io.output(side(a)) > 0);
        b.fn("setOutput", a -> {
            var side = side(a);
            computer.setOutput(side, a.bool(1) ? 15 : 0);
            return null;
        });
        HostModule.Function getAnalogIn = a -> (long) io.input(side(a));
        HostModule.Function getAnalogOut = a -> (long) io.output(side(a));
        HostModule.Function setAnalogOut = a -> {
            var side = side(a);
            computer.setOutput(side, (int) a.integer(1, 0, 15));
            return null;
        };
        for (String w : new String[]{"Analog", "Analogue"}) {
            b.fn("get" + w + "Input", getAnalogIn).fn("get" + w + "Output", getAnalogOut).fn("set" + w + "Output", setAnalogOut);
        }
        return b.build();
    }

    private static ComputerSide side(HostModule.Arguments a) {
        var side = ComputerSide.byId(a.string(0));
        if (side == null) throw a.bad(0, "unknown side '" + a.string(0) + "'");
        return side;
    }

    // ---- peripheral: the machines next to the computer, through their dashboard cards -----------------------------------

    static HostModule peripheral(ComputerPeripherals p) {
        var b = HostModule.builder("peripheral");
        b.fn("getNames", a -> {
            var out = new ArrayList<Object>();
            for (var side : ComputerSide.all()) if (p.views().containsKey(side)) out.add(side.id());
            return out;
        });
        b.fn("isPresent", a -> p.views().containsKey(side(a)));
        b.fn("getType", a -> {
            var v = p.views().get(side(a));
            return v == null ? null : v.kind();
        });
        b.fn("getTitle", a -> {
            var v = p.views().get(side(a));
            return v == null ? null : v.title();
        });
        b.fn("isBlocked", a -> {
            var v = p.views().get(side(a));
            return v != null && v.blocked();
        });
        b.fn("getWidgets", a -> {
            var v = p.views().get(side(a));
            return v == null ? null : v.widgets();
        });
        b.fn("getValues", a -> {
            var v = p.views().get(side(a));
            return v == null ? null : v.values();
        });
        b.fn("getValue", a -> {
            var v = p.views().get(side(a));
            return v == null ? null : v.values().get(a.string(1));
        });
        // asynchronous: the rom's peripheral.call waits for the peripheral_result event with this id
        b.fn("callAsync", a -> {
            var value = a.get(2);
            if (value != null && !(value instanceof Boolean || value instanceof Number || value instanceof String)) {
                throw a.bad(2, "boolean, number or string expected");
            }
            return (long) p.call(side(a), a.string(1), value);
        });
        return b.build();
    }

    // ---- natives (taken by the boot code, then removed from the globals) -----------------------------------------------

    static HostModule natives(Computer computer, ComputerRuntime runtime, FileHandles files) {
        var b = HostModule.builder("__native");
        b.fn("pull", a -> runtime.pull());
        b.fn("args", a -> java.util.List.of()); // a boot program has no shell arguments (ChildProgram gives its own)
        // a WebAssembly program from the shell: runWasm(path, name, args...) -> id; "wasm_done", id, ok, error when it ended
        b.fn("runWasm", a -> {
            var wasm = computer.wasmEngine();
            if (wasm == null) throw new ApiError("WebAssembly programs cannot run on this server");
            String path = a.string(0);
            byte[] module = computer.fs().readFile(path);
            var args = new java.util.ArrayList<Object>();
            args.add(a.has(1) ? a.string(1) : ComputerFs.name(path)); // what it was called as, then its arguments
            for (int i = 2; i < a.count(); i++) args.add(a.get(i));
            String source = new ai.ezfox.cccloud.code.wasm.WasmSource(module, null, null).encode();
            return runtime.startWasm(wasm, ComputerFs.name(path), source, args);
        });
        b.fn("open", a -> {
            try {
                return (long) files.open(a.string(0), a.string(1));
            } catch (ApiError e) {
                return new Object[]{null, e.getMessage()};
            }
        });
        b.fn("read", a -> files.read(handle(a), a.has(1) ? a.integer(1, 0, Integer.MAX_VALUE) : null));
        b.fn("readLine", a -> files.readLine(handle(a), a.optBool(1, false)));
        b.fn("readAll", a -> files.readAll(handle(a)));
        b.fn("write", a -> {
            files.write(handle(a), a.string(1));
            return null;
        });
        b.fn("flush", a -> {
            files.flush(handle(a));
            return null;
        });
        b.fn("seek", a -> files.seek(handle(a), a.has(1) ? a.string(1) : "cur", a.has(2) ? a.integer(2, Integer.MIN_VALUE, Integer.MAX_VALUE) : 0));
        b.fn("close", a -> {
            files.close(handle(a));
            return null;
        });
        // in-game hours 0-24 (tick 0 is 6 o'clock)
        b.fn("gameTime", a -> (computer.gameTimeOfDay() / 1000.0 + 6) % 24);
        b.fn("readFile", a -> {
            // for loading programs: whole file as one string, or nil and the error
            try {
                var p = a.string(0);
                return new String(computer.fs().readFile(p), java.nio.charset.StandardCharsets.ISO_8859_1);
            } catch (ApiError e) {
                return new Object[]{null, e.getMessage()};
            }
        });
        return b.build();
    }

    private static int handle(HostModule.Arguments a) {
        return (int) a.integer(0, 1, Integer.MAX_VALUE);
    }
}
