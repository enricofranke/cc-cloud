package ai.ezfox.cccloud.web;

import static org.junit.jupiter.api.Assertions.*;

import ai.ezfox.cccloud.code.runtime.Limits;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** A .wasm upload onto a computer's drive: the size limit first, then the module check (no WASI, bounded structure). */
class WasmUploadTest {
    private static byte[] module(String name) {
        try (var in = WasmUploadTest.class.getResourceAsStream("/wasm/" + name + ".wasm")) {
            assertNotNull(in, name);
            return in.readAllBytes();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void aGoodProgramIsTaken() {
        assertNull(ScriptApi.wasmRefusal(module("computer-args"), Limits.DEFAULT));
    }

    @Test
    void tooLargeNotAModuleOrRefusedByTheCheck() {
        var big = new byte[Limits.DEFAULT.maxWasmBytes() + 1];
        big[1] = 'a';
        assertEquals(413, ScriptApi.wasmRefusal(big, Limits.DEFAULT).status());
        var text = ScriptApi.wasmRefusal("print('lua')".getBytes(java.nio.charset.StandardCharsets.UTF_8), Limits.DEFAULT);
        assertEquals(400, text.status());
        assertEquals("invalid_wasm", text.body().get("error").getAsString());
        var wasi = ScriptApi.wasmRefusal(module("wasi"), Limits.DEFAULT);
        assertEquals(400, wasi.status());
        assertTrue(wasi.body().get("detail").getAsString().contains("WASI"), wasi.body().toString());
        // a data segment that claims 200 MB in a few bytes
        byte[] hostile = {0, 'a', 's', 'm', 1, 0, 0, 0, 5, 3, 1, 0, 1, 11, 10, 1, 0, 0x41, 0, 0x0b, (byte) 0x80, (byte) 0x84, (byte) 0xaf, 0x5f, 0};
        assertEquals("invalid_wasm", ScriptApi.wasmRefusal(hostile, Limits.DEFAULT).body().get("error").getAsString());
    }
}
