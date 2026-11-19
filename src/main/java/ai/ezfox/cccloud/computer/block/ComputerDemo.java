package ai.ezfox.cccloud.computer.block;

import ai.ezfox.cccloud.computer.CloudComputer;
import ai.ezfox.cccloud.computer.InputEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * A small real program for screenshots and the screen GameTest: {@code startup.lua} draws a title bar and the 16
 * colours, then the shell starts; {@link #type} types a command like a player would.
 */
public final class ComputerDemo {
    /** The startup program: a title bar, the 16 colours as blocks and as text, some ASCII. */
    public static final String COLOUR_TEST = """
        local names = {"white", "orange", "magenta", "lightBlue", "yellow", "lime", "pink", "gray",
          "lightGray", "cyan", "purple", "blue", "brown", "green", "red", "black"}
        term.setBackgroundColour(colors.blue)
        term.setTextColour(colors.white)
        print(" CC: Cloud - colour test" .. string.rep(" ", 27))
        term.setBackgroundColour(colors.black)
        for _, n in ipairs(names) do
          term.setBackgroundColour(colors[n])
          term.write("   ")
        end
        term.setBackgroundColour(colors.black)
        print()
        for _, n in ipairs(names) do
          term.setTextColour(colors[n] == colors.black and colors.gray or colors[n])
          term.write(n:sub(1, 3))
        end
        term.setTextColour(colors.white)
        print()
        print("{x=[1,2]} ~^|\\\\ @#$%&*() <ok>")
        """;

    private ComputerDemo() {
    }

    /** Puts {@code startup.lua} (the colour test) and a second file on the computer's drive. */
    public static void install(CloudComputer computer) throws IOException {
        var files = computer.drive();
        if (!files.exists("startup.lua")) files.writeNew("startup.lua", COLOUR_TEST.getBytes(StandardCharsets.UTF_8));
        if (!files.exists("reactor.lua")) files.writeNew("reactor.lua", "print(\"reactor\")\n".getBytes(StandardCharsets.UTF_8));
    }

    /** Types a line and Enter, as the screen would send it. */
    public static void type(CloudComputer computer, String line, String source) {
        for (char c : line.toCharArray()) computer.input(InputEvent.character(c), source);
        computer.input(InputEvent.key(257, false), source);
        computer.input(InputEvent.keyUp(257), source);
    }
}
