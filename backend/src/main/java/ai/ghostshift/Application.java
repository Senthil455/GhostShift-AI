package ai.ghostshift;

import io.micronaut.runtime.Micronaut;

/**
 * GhostShift AI backend.
 *
 * <p>AI suggests. Application code validates. The user authorizes. The workflow
 * engine executes. History records what happened.</p>
 */
public final class Application {
    private Application() {
    }

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}
