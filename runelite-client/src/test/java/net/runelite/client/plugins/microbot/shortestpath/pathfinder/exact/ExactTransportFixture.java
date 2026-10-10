package net.runelite.client.plugins.microbot.shortestpath.pathfinder.exact;

import static org.mockito.Mockito.*;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;

/** Preserve upstream packed-coordinate fixtures against Microbot's WorldPoint transport model. */
final class ExactTransportFixture {
    static final class Builder {
        private Integer origin;
        private int destination, duration, maxWildernessLevel;
        private TransportType type;
        private String objectInfo;
        Builder origin(int value) { origin = value; return this; }
        Builder destination(int value) { destination = value; return this; }
        Builder duration(int value) { duration = value; return this; }
        Builder type(TransportType value) { type = value; return this; }
        Builder maxWildernessLevel(int value) { maxWildernessLevel = value; return this; }
        Builder objectInfo(String value) { objectInfo = value; return this; }
        Transport build() {
            Transport t = mock(Transport.class);
            when(t.getOrigin()).thenReturn(origin == null || origin == WorldPointUtil.UNDEFINED ? null : WorldPointUtil.unpackWorldPoint(origin));
            when(t.getDestination()).thenReturn(WorldPointUtil.unpackWorldPoint(destination));
            when(t.getDuration()).thenReturn(duration);
            when(t.getType()).thenReturn(type);
            when(t.getMaxWildernessLevel()).thenReturn(maxWildernessLevel);
            when(t.getName()).thenReturn(objectInfo);
            return t;
        }
    }
}
