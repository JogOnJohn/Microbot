package net.runelite.client.plugins.microbot.statemachine;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;

import java.util.concurrent.TimeUnit;

@Slf4j
public class StateMachineScript extends Script {

    private State state = State.INIT;

    private enum State {
        INIT,
        IDLE,
        PROCESS,
        EXIT
    }

    public boolean run(StateMachineConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) {
                    return;
                }

                switch (state) {
                    case INIT:
                        state = State.IDLE;
                        break;
                    case IDLE:
                        // transition logic here
                        break;
                    case PROCESS:
                        // main task logic here
                        state = State.EXIT;
                        break;
                    case EXIT:
                        shutdown();
                        break;
                }
            } catch (Exception e) {
                log.error("Error in state machine", e);
                shutdown();
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }
}
