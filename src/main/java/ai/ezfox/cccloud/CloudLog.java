package ai.ezfox.cccloud;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The mod's logger, in a class without game dependencies so plain unit tests can load code that logs. */
public final class CloudLog {
    public static final Logger LOGGER = LoggerFactory.getLogger("cccloud");

    private CloudLog() {
    }
}
