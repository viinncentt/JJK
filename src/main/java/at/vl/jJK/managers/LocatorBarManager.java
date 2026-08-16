package at.vl.jJK.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.scheduler.BukkitTask;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.color.Color;
import com.github.retrooper.packetevents.protocol.world.waypoint.EmptyWaypointInfo;
import com.github.retrooper.packetevents.protocol.world.waypoint.TrackedWaypoint;
import com.github.retrooper.packetevents.protocol.world.waypoint.Vec3iWaypointInfo;
import com.github.retrooper.packetevents.protocol.world.waypoint.WaypointIcon;
import com.github.retrooper.packetevents.util.Either;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWaypoint;

import at.vl.jJK.JJK;

/**
 * Shows arbitrary entities on a specific player's locator bar, via raw waypoint packets that
 * bypass vanilla's player-only waypoint transmission — so an entity can be shown to exactly one
 * viewer without server-wide visibility. Not tied to any one ability; anything that wants to
 * point a player toward an entity (mob or player) can call {@link #track}/{@link #untrack}.
 */
public class LocatorBarManager implements JJKManager, Listener {
	private final JJK jjk;

	// Viewer UUID -> (tracked entity UUID -> tracked waypoint state)
	private final Map<UUID, Map<UUID, Tracked>> tracked = new HashMap<>();

	private long updatePeriodTicks;
	private BukkitTask updateTask;

	private record Tracked(LivingEntity entity, Color color) {
	}

	public LocatorBarManager(JJK jjk) {
		this.jjk = jjk;
		loadConfig();
	}

	public void reload() {
		loadConfig();
		hideVanillaPlayerWaypoints();
	}

	private void loadConfig() {
		updatePeriodTicks = jjk.getConfig().getLong("LocatorBar.UpdatePeriodTicks", 5);
	}

	@Override
	public void start() {
		// Hides vanilla's automatic player waypoints so only entities explicitly tracked
		// through this manager show up on the locator bar.
		hideVanillaPlayerWaypoints();
		Bukkit.getPluginManager().registerEvents(this, jjk);

		updateTask = Bukkit.getScheduler().runTaskTimer(jjk, this::tick, updatePeriodTicks, updatePeriodTicks);
	}

	/**
	 * Applies to every currently-loaded world; {@link #onWorldLoad} covers any world loaded later
	 * (a new dimension, a plugin creating one lazily, etc.) so this can't silently stop applying.
	 */
	private void hideVanillaPlayerWaypoints() {
		for (World world : Bukkit.getWorlds()) {
			world.setGameRule(GameRules.LOCATOR_BAR, false);
		}
	}

	@EventHandler
	public void onWorldLoad(WorldLoadEvent event) {
		event.getWorld().setGameRule(GameRules.LOCATOR_BAR, false);
	}

	public void stop() {
		if (updateTask != null) {
			updateTask.cancel();
		}
	}

	/**
	 * Shows {@code entity} on {@code viewer}'s locator bar with the given color, keeping its
	 * position updated automatically until {@link #untrack(Player, LivingEntity)} is called.
	 */
	public void track(Player viewer, LivingEntity entity, Color color) {
		Map<UUID, Tracked> viewerTracked = tracked.computeIfAbsent(viewer.getUniqueId(), id -> new HashMap<>());
		boolean alreadyTracked = viewerTracked.put(entity.getUniqueId(), new Tracked(entity, color)) != null;

		WrapperPlayServerWaypoint.Operation operation =
				alreadyTracked ? WrapperPlayServerWaypoint.Operation.UPDATE : WrapperPlayServerWaypoint.Operation.TRACK;
		sendWaypoint(viewer, entity, color, operation);
	}

	/**
	 * Stops showing {@code entity} on {@code viewer}'s locator bar.
	 */
	public void untrack(Player viewer, LivingEntity entity) {
		Map<UUID, Tracked> viewerTracked = tracked.get(viewer.getUniqueId());
		if (viewerTracked == null || viewerTracked.remove(entity.getUniqueId()) == null) {
			return;
		}
		if (viewerTracked.isEmpty()) {
			tracked.remove(viewer.getUniqueId());
		}

		sendUntrack(viewer, entity);
	}

	/**
	 * Stops showing everything currently tracked for {@code viewer} (e.g. on quit).
	 */
	public void untrackAll(Player viewer) {
		Map<UUID, Tracked> viewerTracked = tracked.remove(viewer.getUniqueId());
		if (viewerTracked == null) {
			return;
		}

		for (Tracked entry : viewerTracked.values()) {
			sendUntrack(viewer, entry.entity());
		}
	}

	private void tick() {
		for (Map.Entry<UUID, Map<UUID, Tracked>> viewerEntry : tracked.entrySet()) {
			Player viewer = Bukkit.getPlayer(viewerEntry.getKey());
			if (viewer == null) {
				continue;
			}

			for (Tracked entry : viewerEntry.getValue().values()) {
				if (!entry.entity().isValid()) {
					continue;
				}
				sendWaypoint(viewer, entry.entity(), entry.color(), WrapperPlayServerWaypoint.Operation.UPDATE);
			}
		}
	}

	private void sendWaypoint(Player viewer, LivingEntity entity, Color color, WrapperPlayServerWaypoint.Operation operation) {
		Vector3i position = new Vector3i(
				entity.getLocation().getBlockX(), entity.getLocation().getBlockY(), entity.getLocation().getBlockZ());

		WaypointIcon icon = new WaypointIcon(WaypointIcon.ICON_STYLE_DEFAULT, color);
		TrackedWaypoint waypoint =
				new TrackedWaypoint(Either.createLeft(entity.getUniqueId()), icon, new Vec3iWaypointInfo(position));

		WrapperPlayServerWaypoint packet = new WrapperPlayServerWaypoint(operation, waypoint);
		PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
	}

	private void sendUntrack(Player viewer, LivingEntity entity) {
		WaypointIcon icon = new WaypointIcon(WaypointIcon.ICON_STYLE_DEFAULT, null);
		TrackedWaypoint waypoint =
				new TrackedWaypoint(Either.createLeft(entity.getUniqueId()), icon, EmptyWaypointInfo.EMPTY);

		WrapperPlayServerWaypoint packet = new WrapperPlayServerWaypoint(WrapperPlayServerWaypoint.Operation.UNTRACK, waypoint);
		PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
	}
}
