import local.logger.MovementTracker;
import local.logger.ProtocolDecoder;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;

/** Run with -ea; no network, Minecraft, or test library required. */
public final class DecoderChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        // IDs and fixtures follow the attributed minecraft-data schemas in ProtocolDecoder.
        int[][] versions = {
                {754, 0x13, 0x1c, -1}, {769, 0x1d, 0x28, 0x29}, {770, 0x1d, 0x28, 0x29},
                {771, 0x1e, 0x29, 0x2a}, {772, 0x1e, 0x29, 0x2a}, {773, 0x1e, 0x29, 0x2a},
                {774, 0x1e, 0x29, 0x2a}, {775, 0x1f, 0x2a, 0x2b}
        };
        for (int[] v : versions) schema(v[0], v[1], v[2], v[3]);
        trackerTimingAndUnknowns();
        trackerCorrections();
        trackerInFlightTeleportPackets();
        trackerInputsAndVehicles();
        invalidPackets();
        System.out.println("DecoderChecks: " + checks + " assertions passed across 8 protocol versions.");
    }

    private static void schema(int protocol, int positionLookId, int actionId, int inputId) throws Exception {
        check(ProtocolDecoder.supports(protocol), "supported protocol " + protocol);
        equal(ProtocolDecoder.packetId(protocol, "PLAY", true, "position_look"), positionLookId, "movement ID " + protocol);
        equal(ProtocolDecoder.packetName(protocol, "PLAY", true, positionLookId), "position_look", "movement name " + protocol);
        equal(ProtocolDecoder.packetId(protocol, "LOGIN", false, "disconnect"), 0, "login disconnect ID");

        var packet = new ByteArrayOutputStream(); var out = new DataOutputStream(packet);
        out.writeByte(positionLookId); out.writeDouble(-123.125); out.writeDouble(64.375); out.writeDouble(20.5);
        out.writeFloat(179.5f); out.writeFloat(-45.25f); out.writeByte(protocol == 754 ? 1 : 3);
        var fields = ProtocolDecoder.fields(protocol, "PLAY", true, positionLookId, packet.toByteArray());
        number(fields.get("x"), -123.125, "negative coordinate"); number(fields.get("y"), 64.375, "fractional coordinate");
        number(fields.get("yaw"), 179.5, "rotation"); equal(fields.get("onGround"), true, "ground flag");
        equal(fields.get("decodedComplete"), true, "complete movement");
        if (protocol != 754) equal(fields.get("horizontalCollision"), true, "collision bit");
        else check(!fields.containsKey("horizontalCollision"), "old version has no invented collision bit");

        equal(ProtocolDecoder.packetId(protocol, "PLAY", true, "entity_action"), actionId, "action ID");
        var actionPacket = new byte[]{(byte) actionId, 42, (byte) (protocol >= 771 ? 1 : 3), 0};
        var action = ProtocolDecoder.fields(protocol, "PLAY", true, actionId, actionPacket);
        equal(action.get("action"), "start_sprinting", "version-specific sprint action");
        actionPacket[2]++;
        equal(ProtocolDecoder.fields(protocol, "PLAY", true, actionId, actionPacket).get("action"), "stop_sprinting", "sprint stop");
        if (inputId >= 0) {
            equal(ProtocolDecoder.packetId(protocol, "PLAY", true, "player_input"), inputId, "input ID");
            var input = object(ProtocolDecoder.fields(protocol, "PLAY", true, inputId, new byte[]{(byte) inputId, 0x51}).get("inputs"));
            equal(input.get("forward"), true, "forward input"); equal(input.get("jump"), true, "jump input");
            equal(input.get("sprint"), true, "sprint input"); equal(input.get("shift"), false, "sneak input");
        }
    }

    private static void trackerTimingAndUnknowns() {
        var tracker = new MovementTracker();
        var look = tracker.movement("look", Map.of("yaw", 179., "pitch", 0., "onGround", true), 900_000_000L);
        check(look.get("x") == null, "look before first position keeps x unknown");
        check(look.get("speedBlocksPerSecond") == null, "look has no invented speed");
        var first = tracker.movement("position", Map.of("x", 1., "y", 64., "z", 2., "onGround", true), 1_000_000_000L);
        check(first.get("distance") == null && first.get("dtMs") == null, "initial position has no delta or time");
        var wrap = tracker.movement("look", Map.of("yaw", -179., "pitch", 1., "onGround", true), 1_025_000_000L);
        number(object(wrap.get("delta")).get("yaw"), 2., "yaw wrap avoids 358-degree turn");
        var second = tracker.movement("position", Map.of("x", 2., "y", 64., "z", 2., "onGround", true), 1_050_000_000L);
        number(second.get("dtMs"), 50., "position timing ignores intervening look");
        number(second.get("speedBlocksPerSecond"), 20., "packet-derived speed");
        number(second.get("distance"), 1., "position distance");
        number(second.get("horizontalDistance"), 1., "horizontal distance");
        var duplicateTime = tracker.movement("position", Map.of("x", 3., "y", 64., "z", 2.), 1_050_000_000L);
        check(duplicateTime.get("speedBlocksPerSecond") == null, "zero dt has no infinity");
        equal(second.get("measurement"), "client_packet", "measurement provenance");
        check(tracker.movement("arm_animation", Map.of("hand", 0), 2_000_000_000L) == null, "action is not movement");
        check(tracker.movement("position", Map.of(), 2_000_000_000L) == null, "partial semantic packet has no fabricated position");
    }

    private static void trackerCorrections() {
        var tracker = new MovementTracker();
        tracker.movement("position_look", Map.of("x", 10., "y", 64., "z", 20., "yaw", 10., "pitch", 5.), 1_000_000_000L);
        tracker.serverPacket("position", Map.of("x", 5., "y", 70., "z", -2., "yaw", 20., "pitch", -5.,
                "flags", Map.of("x", true, "y", false, "z", true, "yaw", true, "pitch", false), "teleportId", 17));
        var after = tracker.movement("position_look", Map.of("x", 15.5, "y", 70., "z", 18., "yaw", 31., "pitch", -5.), 1_050_000_000L);
        number(object(after.get("delta")).get("x"), .5, "relative correction rebases position");
        number(object(after.get("delta")).get("yaw"), 1., "relative correction rebases yaw");
        check(after.get("speedBlocksPerSecond") == null && after.get("dtMs") == null, "server teleport cannot create player speed");
        equal(object(after.get("state")).get("pendingTeleportId"), 17, "pending teleport");
        tracker.action("teleport_confirm", Map.of("teleportId", 17));
        var confirmed = tracker.movement("flying", Map.of("onGround", true), 1_060_000_000L);
        check(object(confirmed.get("state")).get("pendingTeleportId") == null, "matching confirmation clears pending ID");
        tracker.serverPacket("respawn", Map.of());
        var reset = tracker.movement("look", Map.of("yaw", 0., "pitch", 0.), 1_100_000_000L);
        check(reset.get("x") == null && reset.get("y") == null && reset.get("z") == null, "respawn clears old world coordinates");
        tracker.serverPacket("position", Map.of("x", 2., "y", 64., "z", 4., "flags", 1));
        var unknownRelative = tracker.movement("look", Map.of("yaw", 0., "pitch", 0.), 1_150_000_000L);
        check(unknownRelative.get("x") == null, "relative correction without baseline remains unknown");
        number(unknownRelative.get("z"), 4., "absolute correction establishes known coordinate");
    }

    private static void trackerInputsAndVehicles() {
        var tracker = new MovementTracker();
        tracker.action("player_input", Map.of("inputs", Map.of("forward", true, "jump", true, "shift", false, "sprint", true)));
        tracker.action("entity_action", Map.of("action", "start_sprinting"));
        tracker.action("held_item_slot", Map.of("slotId", 4));
        var first = tracker.movement("position_look", Map.of("x", 1., "y", 64., "z", 1., "yaw", 0., "pitch", 0.), 1_000_000_000L);
        var state = object(first.get("state"));
        equal(state.get("sprinting"), true, "sprint transition tracked"); equal(state.get("heldSlot"), 4, "hotbar slot tracked");
        equal(object(state.get("inputs")).get("jump"), true, "input state retained");
        var vector = object(first.get("lookVector")); number(vector.get("x"), 0., "yaw zero x direction"); number(vector.get("z"), 1., "yaw zero looks south");
        tracker.action("entity_action", Map.of("action", "stop_sprinting"));
        equal(state.get("sprinting"), true, "returned snapshots do not mutate after later actions");
        tracker.movement("vehicle_move", Map.of("x", 100., "y", 64., "z", 100., "yaw", 90., "pitch", 0.), 1_025_000_000L);
        var walking = tracker.movement("position", Map.of("x", 1.5, "y", 64., "z", 1.), 1_050_000_000L);
        check(walking.get("speedBlocksPerSecond") == null, "vehicle transition cannot create walking speed");
        number(object(walking.get("delta")).get("x"), .5, "vehicle location does not replace player's feet");
    }

    private static void trackerInFlightTeleportPackets() {
        var tracker = new MovementTracker();
        tracker.movement("position_look", Map.of("x", 1., "y", 64., "z", 1., "yaw", 5., "pitch", 0.), 1_000_000_000L);
        tracker.serverPacket("position", Map.of("x", 1000., "y", 70., "z", 2000., "yaw", 90., "pitch", 0., "flags", 0, "teleportId", 23));
        // These packets were sent by the client before it received the server correction.
        tracker.movement("position", Map.of("x", 1.1, "y", 64., "z", 1.), 1_020_000_000L);
        var secondStale = tracker.movement("position", Map.of("x", 1.2, "y", 64., "z", 1.), 1_030_000_000L);
        check(secondStale.get("speedBlocksPerSecond") == null, "movement during pending teleport has no comparable speed");
        equal(secondStale.get("teleportPending"), true, "pending teleport explicitly marked");
        tracker.action("teleport_confirm", Map.of("teleportId", 22));
        var unmatched = tracker.movement("flying", Map.of("onGround", true), 1_035_000_000L);
        equal(object(unmatched.get("state")).get("pendingTeleportId"), 23, "unmatched confirmation cannot clear newer correction");
        tracker.action("teleport_confirm", Map.of("teleportId", 23));
        var resumed = tracker.movement("position_look", Map.of("x", 1000.5, "y", 70., "z", 2000., "yaw", 91., "pitch", 0.), 1_040_000_000L);
        check(resumed.get("speedBlocksPerSecond") == null && resumed.get("dtMs") == null, "confirmation resets timing despite in-flight stale position");
        number(object(resumed.get("delta")).get("x"), .5, "confirmed baseline excludes stale client position");
        number(object(resumed.get("delta")).get("yaw"), 1., "confirmed rotation baseline");
        equal(object(resumed.get("state")).get("positionSource"), "server_correction_confirmed", "restored baseline source explicit");
        equal(resumed.get("teleportPending"), false, "matching confirmation clears pending marker");
        var next = tracker.movement("position", Map.of("x", 1001., "y", 70., "z", 2000.), 1_090_000_000L);
        number(next.get("speedBlocksPerSecond"), 10., "speed resumes between comparable post-confirmation samples");
    }

    private static void invalidPackets() throws Exception {
        check(!ProtocolDecoder.supports(9999), "unsupported protocol explicit");
        check(ProtocolDecoder.packetName(9999, "PLAY", true, 29).startsWith("unknown_"), "unknown packet not guessed");
        check(ProtocolDecoder.packetId(9999, "PLAY", false, "kick_disconnect") == null, "unknown version has no invented ID");
        try { ProtocolDecoder.fields(769, "PLAY", true, 0x1d, new byte[]{0x1d, 0}); throw new AssertionError("truncation accepted"); }
        catch (IOException expected) { checks++; }
        try { ProtocolDecoder.fields(769, "PLAY", true, 0x1d, new byte[]{0x1c}); throw new AssertionError("wrong ID accepted"); }
        catch (IOException expected) { checks++; }
    }

    private static Map<?, ?> object(Object value) { check(value instanceof Map<?, ?>, "expected object"); return (Map<?, ?>) value; }
    private static void number(Object actual, double expected, String message) { check(actual instanceof Number && Math.abs(((Number) actual).doubleValue() - expected) < 1e-9, message + ": " + actual); }
    private static void equal(Object actual, Object expected, String message) { check(java.util.Objects.equals(actual, expected), message + ": " + actual); }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
