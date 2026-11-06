package local.logger;

import java.util.LinkedHashMap;
import java.util.Map;

/** Latest observed client state. Packet timing is receive timing, never a server tick or velocity. */
public final class MovementTracker {
    private Double x, y, z, yaw, pitch;
    private Boolean onGround, horizontalCollision;
    private Long lastPositionNano, lastLookNano, lastMovementNano;
    private final Map<String, Object> state = new LinkedHashMap<>();
    private Double vehicleX, vehicleY, vehicleZ, vehicleYaw, vehiclePitch;
    private Long lastVehicleNano;
    private Integer playerEntityId;
    private Correction pendingCorrection;

    public MovementTracker() {
        state.put("sprinting", null);
        state.put("sneaking", null);
        state.put("flying", null);
        state.put("fallFlying", null);
        state.put("heldSlot", null);
        state.put("inputs", null);
    }

    public synchronized Map<String, Object> movement(String name, Map<String, Object> fields, long nano) {
        boolean vehicle = "vehicle_move".equals(name);
        boolean hasPosition = vehicle || "position".equals(name) || "position_look".equals(name);
        boolean hasLook = vehicle || "look".equals(name) || "position_look".equals(name);
        if (!hasPosition && !hasLook && !"flying".equals(name)) return null;
        // A decoder failure must not manufacture a movement row from missing coordinates.
        if (hasPosition && (!isNumber(fields, "x") || !isNumber(fields, "y") || !isNumber(fields, "z"))) return null;
        if (hasLook && (!isNumber(fields, "yaw") || !isNumber(fields, "pitch"))) return null;

        Double oldX = vehicle ? vehicleX : x, oldY = vehicle ? vehicleY : y, oldZ = vehicle ? vehicleZ : z;
        Double oldYaw = vehicle ? vehicleYaw : yaw, oldPitch = vehicle ? vehiclePitch : pitch;
        Double newX = hasPosition ? number(fields.get("x")) : x;
        Double newY = hasPosition ? number(fields.get("y")) : y;
        Double newZ = hasPosition ? number(fields.get("z")) : z;
        Double newYaw = hasLook ? number(fields.get("yaw")) : yaw;
        Double newPitch = hasLook ? number(fields.get("pitch")) : pitch;
        Long previousTime = vehicle ? lastVehicleNano : hasPosition ? lastPositionNano : lastLookNano;
        Double dtMs = elapsed(previousTime, nano);
        Double dx = hasPosition ? difference(newX, oldX) : null;
        Double dy = hasPosition ? difference(newY, oldY) : null;
        Double dz = hasPosition ? difference(newZ, oldZ) : null;
        Double deltaYaw = hasLook ? angleDifference(newYaw, oldYaw) : null;
        Double deltaPitch = hasLook ? difference(newPitch, oldPitch) : null;
        Double distance = dx == null || dy == null || dz == null ? null : Math.sqrt(dx * dx + dy * dy + dz * dz);
        Double horizontalDistance = dx == null || dz == null ? null : Math.hypot(dx, dz);
        boolean teleportPending = state.get("pendingTeleportId") != null;
        Double speed = teleportPending || distance == null || dtMs == null || dtMs <= 0 ? null : distance * 1000.0 / dtMs;
        Boolean packetGround = fields.get("onGround") instanceof Boolean ? (Boolean) fields.get("onGround") : null;
        Boolean packetCollision = fields.get("horizontalCollision") instanceof Boolean ? (Boolean) fields.get("horizontalCollision") : null;
        if (vehicle) {
            vehicleX = newX; vehicleY = newY; vehicleZ = newZ;
            vehicleYaw = newYaw; vehiclePitch = newPitch; lastVehicleNano = nano;
            // Vehicle coordinates do not describe the player's feet. Rebase the next walking sample.
            lastPositionNano = null;
        } else {
            x = newX; y = newY; z = newZ; yaw = newYaw; pitch = newPitch;
            if (packetGround != null) onGround = packetGround;
            if (packetCollision != null) horizontalCollision = packetCollision;
            if (hasPosition) lastPositionNano = nano;
            if (hasLook) lastLookNano = nano;
        }
        Map<String, Object> result = map(
                "variant", name, "measurement", "client_packet", "movementKind", vehicle ? "vehicle" : "player",
                "hasPosition", hasPosition, "hasLook", hasLook,
                "x", newX, "y", newY, "z", newZ, "yaw", newYaw, "pitch", newPitch,
                "onGround", vehicle ? packetGround : onGround,
                "horizontalCollision", vehicle ? packetCollision : horizontalCollision,
                "delta", map("x", dx, "y", dy, "z", dz, "yaw", deltaYaw, "pitch", deltaPitch),
                "dtMs", dtMs, "dtBasis", vehicle ? "previous_vehicle_packet" : hasPosition ? "previous_position_packet" : "previous_look_packet",
                "packetDtMs", elapsed(lastMovementNano, nano),
                "distance", distance, "horizontalDistance", horizontalDistance,
                "teleportPending", teleportPending,
                "speedBlocksPerSecond", speed, "lookVector", lookVector(newYaw, newPitch),
                "state", copy(state));
        lastMovementNano = nano;
        return result;
    }

    public synchronized void serverPacket(String name, Map<String, Object> fields) {
        if ("respawn".equals(name) || "login".equals(name)) {
            resetCoordinates();
            if ("login".equals(name) && fields.get("entityId") instanceof Number) playerEntityId = ((Number) fields.get("entityId")).intValue();
            state.put("pendingTeleportId", null);
            state.put("positionSource", name);
        } else if ("position".equals(name)) {
            Object flags = fields.get("flags");
            x = corrected(fields, "x", x, relative(flags, "x", 0));
            y = corrected(fields, "y", y, relative(flags, "y", 1));
            z = corrected(fields, "z", z, relative(flags, "z", 2));
            yaw = corrected(fields, "yaw", yaw, relative(flags, "yaw", 3));
            pitch = corrected(fields, "pitch", pitch, relative(flags, "pitch", 4));
            lastPositionNano = null;
            lastLookNano = null;
            onGround = null;
            horizontalCollision = null;
            state.put("pendingTeleportId", fields.get("teleportId"));
            pendingCorrection = fields.get("teleportId") == null ? null : new Correction(x, y, z, yaw, pitch);
            state.put("positionSource", "server_correction");
        } else if ("player_rotation".equals(name)) {
            yaw = corrected(fields, "yaw", pendingCorrection == null ? yaw : pendingCorrection.yaw,
                    Boolean.TRUE.equals(fields.get("relativeYaw")));
            pitch = corrected(fields, "pitch", pendingCorrection == null ? pitch : pendingCorrection.pitch,
                    Boolean.TRUE.equals(fields.get("relativePitch")));
            lastLookNano = null;
            if (pendingCorrection != null) pendingCorrection = new Correction(pendingCorrection.x, pendingCorrection.y, pendingCorrection.z, yaw, pitch);
        } else if ("vehicle_move".equals(name)) {
            vehicleX = number(fields.get("x")); vehicleY = number(fields.get("y")); vehicleZ = number(fields.get("z"));
            vehicleYaw = number(fields.get("yaw")); vehiclePitch = number(fields.get("pitch"));
            lastVehicleNano = null;
            lastPositionNano = null;
        } else if ("held_item_slot".equals(name)) {
            if (fields.get("slot") instanceof Number) state.put("heldSlot", fields.get("slot"));
        } else if ("abilities".equals(name) && fields.get("flags") instanceof Number) {
            int flags = ((Number) fields.get("flags")).intValue();
            state.put("flying", (flags & 2) != 0);
            state.put("allowFlying", (flags & 4) != 0);
            state.put("creativeMode", (flags & 8) != 0);
            if (fields.containsKey("flyingSpeed")) state.put("flyingSpeed", fields.get("flyingSpeed"));
            if (fields.containsKey("walkingSpeed")) state.put("walkingSpeed", fields.get("walkingSpeed"));
        } else if ("entity_velocity".equals(name) && playerEntityId != null
                && fields.get("entityId") instanceof Number
                && ((Number) fields.get("entityId")).intValue() == playerEntityId
                && fields.get("velocity") instanceof Map<?, ?> velocity) {
            // This is a server impulse, not measured client velocity.
            state.put("serverImpulse", map("x", scaled(velocity.get("x")), "y", scaled(velocity.get("y")), "z", scaled(velocity.get("z")), "units", "blocks_per_tick"));
        }
    }

    public synchronized void action(String name, Map<String, Object> fields) {
        if ("entity_action".equals(name)) {
            Object action = fields.get("action");
            if ("start_sprinting".equals(action)) state.put("sprinting", true);
            if ("stop_sprinting".equals(action)) state.put("sprinting", false);
            if ("start_sneaking".equals(action)) state.put("sneaking", true);
            if ("stop_sneaking".equals(action)) state.put("sneaking", false);
            if ("start_fall_flying".equals(action)) state.put("fallFlying", true);
        } else if ("player_input".equals(name) && fields.get("inputs") instanceof Map<?, ?> inputs) {
            state.put("inputs", copy(inputs));
            if (inputs.get("shift") instanceof Boolean) state.put("sneaking", inputs.get("shift"));
            // The input sprint bit is intent; entity_action reports the sprint state transition.
            if (inputs.get("sprint") instanceof Boolean) state.put("sprintInput", inputs.get("sprint"));
        } else if ("held_item_slot".equals(name) && fields.get("slotId") instanceof Number) {
            state.put("heldSlot", fields.get("slotId"));
        } else if ("abilities".equals(name) && fields.get("flags") instanceof Number) {
            state.put("flying", (((Number) fields.get("flags")).intValue() & 2) != 0);
        } else if ("teleport_confirm".equals(name)) {
            state.put("lastConfirmedTeleportId", fields.get("teleportId"));
            if (fields.get("teleportId") != null && fields.get("teleportId").equals(state.get("pendingTeleportId"))) {
                state.put("pendingTeleportId", null);
                // A pre-teleport movement can arrive while the correction is in flight. Restore
                // the server's baseline at its acknowledgement, and do not derive speed across it.
                if (pendingCorrection != null) {
                    x = pendingCorrection.x; y = pendingCorrection.y; z = pendingCorrection.z;
                    yaw = pendingCorrection.yaw; pitch = pendingCorrection.pitch;
                    pendingCorrection = null;
                }
                lastPositionNano = lastLookNano = null;
                state.put("positionSource", "server_correction_confirmed");
            }
        } else if ("steer_vehicle".equals(name)) {
            Map<String, Object> input = copy(fields);
            if (fields.get("jump") instanceof Number) {
                int flags = ((Number) fields.get("jump")).intValue();
                input.put("jumping", (flags & 1) != 0);
                input.put("dismounting", (flags & 2) != 0);
            }
            state.put("vehicleInput", input);
        }
    }

    private void resetCoordinates() {
        x = y = z = yaw = pitch = null;
        vehicleX = vehicleY = vehicleZ = vehicleYaw = vehiclePitch = null;
        lastPositionNano = lastLookNano = lastMovementNano = lastVehicleNano = null;
        onGround = horizontalCollision = null;
        pendingCorrection = null;
        state.put("fallFlying", null);
        state.put("serverImpulse", null);
    }

    private static boolean relative(Object flags, String key, int bit) {
        return flags instanceof Map<?, ?> values ? Boolean.TRUE.equals(values.get(key))
                : flags instanceof Number && ((((Number) flags).longValue() & (1L << bit)) != 0);
    }

    private static Double corrected(Map<String, Object> fields, String key, Double previous, boolean relative) {
        Double value = number(fields.get(key));
        if (value == null) return previous;
        return relative ? previous == null ? null : previous + value : value;
    }

    private static Double elapsed(Long previous, long current) {
        return previous == null || current <= previous ? null : (current - previous) / 1_000_000.0;
    }

    private static Double number(Object value) {
        if (!(value instanceof Number)) return null;
        double result = ((Number) value).doubleValue();
        return Double.isFinite(result) ? result : null;
    }

    private static boolean isNumber(Map<String, Object> values, String key) { return number(values.get(key)) != null; }
    private static Double difference(Double current, Double previous) { return current == null || previous == null ? null : current - previous; }
    private static Double angleDifference(Double current, Double previous) {
        Double delta = difference(current, previous);
        return delta == null ? null : ((delta % 360.0 + 540.0) % 360.0) - 180.0;
    }

    private static Double scaled(Object value) { Double n = number(value); return n == null ? null : n / 8000.0; }

    private static Map<String, Object> lookVector(Double yaw, Double pitch) {
        if (yaw == null || pitch == null) return null;
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch), c = Math.cos(p);
        return map("x", -Math.sin(y) * c, "y", -Math.sin(p), "z", Math.cos(y) * c);
    }

    private static Map<String, Object> copy(Map<?, ?> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            Object value = entry.getValue();
            result.put(String.valueOf(entry.getKey()), value instanceof Map<?, ?> nested ? copy(nested) : value);
        }
        return result;
    }

    private static Map<String, Object> map(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) result.put((String) entries[i], entries[i + 1]);
        return result;
    }

    private record Correction(Double x, Double y, Double z, Double yaw, Double pitch) { }
}
