package ai.ezfox.cccloud.compat.cc;

import ai.ezfox.cccloud.CCCloud;
import ai.ezfox.cccloud.compat.card.PeripheralValues;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.LuaTask;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IDynamicPeripheral;
import dan200.computercraft.api.peripheral.IPeripheral;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * The methods of a CC: Tweaked peripheral the Peripheral card may use (docs/COMPAT.md "Peripheral card"), with their
 * Java signatures, so that only methods that take no arguments are ever polled.
 * <p>
 * CC builds a peripheral's Lua methods from {@link LuaFunction} methods: on the peripheral's own class, and from generic
 * sources (CC's energy/item/fluid methods and those mods register) whose target the block offers. Both are looked up
 * here with plain reflection, the generic ones through CC's method registry (internal classes; if CC changes them, the
 * card simply offers fewer methods). Dynamic peripherals of other mods only report names, not arguments: their methods
 * are never offered (counted as {@link Discovery#unknown()}).
 * <p>
 * A method is a <b>getter</b> when its name is a getter name ({@link PeripheralValues#isGetterName}) and its only
 * parameters are the generic target and {@link ILuaContext}; a <b>setter</b> when its name starts with {@code set} and
 * it takes exactly one {@code boolean}. Methods that ask for {@link IComputerAccess} or {@link IArguments} are never
 * offered. Server thread only.
 */
public final class PeripheralMethods {
    private static final String GENERIC_PERIPHERAL = "dan200.computercraft.shared.peripheral.generic.GenericPeripheral";
    private static boolean internalsFailed;

    private PeripheralMethods() {
    }

    /** A usable method: its Lua name, how to call it, and what it returns. */
    public record Callable(String name, Object receiver, @Nullable Object target, Method method, boolean setter) {
        /** Whether the Java return type is numeric (offered for charts) or boolean (shown as an indicator). */
        public boolean numeric() {
            var t = method.getReturnType();
            return t == int.class || t == long.class || t == double.class || t == float.class || t == short.class || t == byte.class
                || Number.class.isAssignableFrom(t);
        }

        public boolean bool() {
            var t = method.getReturnType();
            return t == boolean.class || t == Boolean.class;
        }
    }

    /** What a peripheral offers: getters and setters by name, and how many methods could not be checked. */
    public record Discovery(String type, List<String> types, Map<String, Callable> getters, Map<String, Callable> setters, int unknown, int total) {
    }

    /** Lists the getters and setters of a peripheral. Never calls any of them. */
    public static Discovery discover(MinecraftServer server, IPeripheral peripheral) {
        var types = new ArrayList<String>();
        types.add(peripheral.getType());
        for (String t : peripheral.getAdditionalTypes()) if (!types.contains(t)) types.add(t);
        var all = new LinkedHashMap<String, Callable>();
        int unknown = 0, total = 0;
        if (GENERIC_PERIPHERAL.equals(peripheral.getClass().getName())) {
            var saturated = saturated(peripheral);
            var generics = genericMethods(server);
            if (saturated == null || generics == null) {
                String[] names = ((IDynamicPeripheral) peripheral).getMethodNames();
                return new Discovery(peripheral.getType(), types, Map.of(), Map.of(), names.length, names.length);
            }
            for (var s : saturated) {
                total++;
                Callable found = null;
                for (var g : generics) {
                    if (!g.target().isInstance(s.target()) || !names(g.method(), g.annotation()).contains(s.name())) continue;
                    found = callable(s.name(), g.source(), s.target(), g.method());
                    break;
                }
                if (found == null) unknown++;
                else all.putIfAbsent(found.name(), found);
            }
        } else if (peripheral instanceof IDynamicPeripheral dynamic) {
            int n = dynamic.getMethodNames().length;
            return new Discovery(peripheral.getType(), types, Map.of(), Map.of(), n, n);
        } else {
            for (var m : peripheral.getClass().getMethods()) {
                var annotation = m.getAnnotation(LuaFunction.class);
                if (annotation == null || Modifier.isStatic(m.getModifiers()) || m.isBridge() || m.isSynthetic()) continue;
                for (String name : names(m, annotation)) {
                    total++;
                    var c = callable(name, peripheral, null, m);
                    if (c != null) all.putIfAbsent(name, c);
                }
            }
            // generic sources whose target is the peripheral object itself (as CC's own method lookup does)
            var generics = genericMethods(server);
            if (generics != null) {
                for (var g : generics) {
                    if (!g.target().isInstance(peripheral)) continue;
                    for (String name : names(g.method(), g.annotation())) {
                        total++;
                        var c = callable(name, g.source(), peripheral, g.method());
                        if (c != null) all.putIfAbsent(name, c);
                    }
                }
            }
        }
        var getters = new LinkedHashMap<String, Callable>();
        var setters = new LinkedHashMap<String, Callable>();
        all.keySet().stream().sorted().forEach(name -> {
            var c = all.get(name);
            (c.setter() ? setters : getters).put(name, c);
        });
        return new Discovery(peripheral.getType(), types, getters, setters, unknown, total);
    }

    /** The Lua names of a method: the annotation's names, else the Java name (CC's rule). */
    private static List<String> names(Method m, LuaFunction annotation) {
        var names = annotation.value();
        return names.length == 0 ? List.of(m.getName()) : List.of(names);
    }

    /**
     * A getter or setter, or null: the generic target (if any) and {@link ILuaContext} are filled in by the card; any
     * other parameter is a Lua argument. Getters take none, setters exactly one boolean.
     */
    private static @Nullable Callable callable(String name, Object receiver, @Nullable Object target, Method m) {
        var params = m.getParameterTypes();
        int from = target != null ? 1 : 0;
        int luaArgs = 0;
        boolean booleanArg = false;
        for (int i = from; i < params.length; i++) {
            var p = params[i];
            if (p == ILuaContext.class) continue;
            if (p == IComputerAccess.class || p == IArguments.class) return null;
            luaArgs++;
            booleanArg = p == boolean.class || p == Boolean.class;
        }
        if (m.getReturnType() == void.class && luaArgs == 0) return null;
        if (luaArgs == 0 && PeripheralValues.isGetterName(name)) return new Callable(name, receiver, target, m, false);
        if (luaArgs == 1 && booleanArg && PeripheralValues.isSetterName(name)) return new Callable(name, receiver, target, m, true);
        return null;
    }

    // ---- calling ---------------------------------------------------------------------------------------------------

    /** The outcome of a call: the result (CC's values) or an error message. */
    public record Outcome(@Nullable Object value, @Nullable String error) {
    }

    /** Calls a getter (no arguments) or a setter (one boolean) on the server thread. */
    public static Outcome call(Callable c, @Nullable Boolean argument) {
        var context = new ImmediateContext();
        var params = c.method().getParameterTypes();
        var args = new Object[params.length];
        int from = c.target() != null ? 1 : 0;
        if (c.target() != null) args[0] = c.target();
        for (int i = from; i < params.length; i++) {
            args[i] = params[i] == ILuaContext.class ? context : argument;
        }
        if (c.setter() && argument == null) return new Outcome(null, "no value");
        try {
            c.method().setAccessible(true);
        } catch (RuntimeException ignored) {
            // public methods of exported classes work without it
        }
        try {
            Object result = c.method().invoke(c.receiver(), args);
            if (result instanceof MethodResult mr) return context.resolve(mr);
            return new Outcome(result, null);
        } catch (InvocationTargetException e) {
            var cause = e.getCause();
            if (cause instanceof LuaException lua) return new Outcome(null, lua.getMessage() == null ? "error" : lua.getMessage());
            return new Outcome(null, cause == null ? "error" : cause.getClass().getSimpleName());
        } catch (IllegalAccessException | IllegalArgumentException e) {
            return new Outcome(null, "not callable");
        }
    }

    /**
     * An {@link ILuaContext} for calls on the server thread: a main-thread task runs at once, and the
     * {@code task_complete} event CC's {@code executeMainThreadTask} waits for is answered right away.
     */
    private static final class ImmediateContext implements ILuaContext {
        private long nextId;
        private long lastId = -1;
        private boolean lastOk;
        private @Nullable String lastError;

        @Override
        public long issueMainThreadTask(LuaTask task) {
            long id = ++nextId;
            lastId = id;
            try {
                task.execute();
                lastOk = true;
            } catch (LuaException e) {
                lastOk = false;
                lastError = e.getMessage();
            }
            return id;
        }

        Outcome resolve(MethodResult result) {
            var mr = result;
            for (int i = 0; i < 4 && mr.getCallback() != null; i++) {
                if (lastId < 0) return new Outcome(null, "waits for an event");
                try {
                    mr = mr.getCallback().resume(new Object[]{"task_complete", lastId, lastOk, lastError});
                } catch (LuaException e) {
                    return new Outcome(null, e.getMessage() == null ? "error" : e.getMessage());
                }
                lastId = -1;
            }
            if (mr.getCallback() != null) return new Outcome(null, "waits for an event");
            return new Outcome(mr.getResult(), null);
        }
    }

    // ---- CC internals (reflection, fail-soft) --------------------------------------------------------------------------

    private record Saturated(Object target, String name) {
    }

    private record Generic(Object source, Method method, LuaFunction annotation, Class<?> target) {
    }

    /** The (target, name) pairs of CC's generic peripheral, or null if CC's internals changed. */
    private static @Nullable List<Saturated> saturated(IPeripheral peripheral) {
        if (internalsFailed) return null;
        try {
            var methodsField = field(peripheral.getClass(), "methods");
            var list = (List<?>) methodsField.get(peripheral);
            var out = new ArrayList<Saturated>();
            for (Object s : list) {
                var target = field(s.getClass(), "target").get(s);
                var name = (String) field(s.getClass(), "name").get(s);
                if (target != null && name != null) out.add(new Saturated(target, name));
            }
            return out;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            failed(e);
            return null;
        }
    }

    /** CC's registered generic methods (energy/item/fluid and those of other mods), or null if CC's internals changed. */
    private static @Nullable List<Generic> genericMethods(MinecraftServer server) {
        if (internalsFailed) return null;
        try {
            var supplier = dan200.computercraft.shared.computer.core.ServerContext.get(server).peripheralMethods();
            var list = (List<?>) field(supplier.getClass(), "genericMethods").get(supplier);
            var out = new ArrayList<Generic>();
            for (Object g : list) {
                var c = g.getClass();
                var method = (Method) field(c, "method").get(g);
                // CC lists the erased bridge methods of generic base classes too, but never generates them
                if (method.isBridge() || method.isSynthetic()) continue;
                out.add(new Generic(field(c, "source").get(g), method, (LuaFunction) field(c, "annotation").get(g),
                    (Class<?>) field(c, "target").get(g)));
            }
            return out;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            failed(e);
            return null;
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        var f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static void failed(Throwable e) {
        if (!internalsFailed) {
            internalsFailed = true;
            CCCloud.LOGGER.warn("[cccloud] This CC: Tweaked version hides its generic peripheral methods; the Peripheral card only offers a block's own methods ({})", e.toString());
        }
    }
}
