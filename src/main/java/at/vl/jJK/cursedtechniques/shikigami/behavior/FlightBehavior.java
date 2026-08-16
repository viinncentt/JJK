package at.vl.jJK.cursedtechniques.shikigami.behavior;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sittable;
import org.bukkit.util.Vector;

import com.destroystokyo.paper.entity.ai.GoalType;

import at.vl.jJK.cursedtechniques.shikigami.ShikigamiManager;
import at.vl.jJK.cursedtechniques.shikigami.ShikigamiType;

/**
 * Custom flight — drives 100% of the shikigami's own movement via direct velocity control every
 * tick, since vanilla's ground-based Pathfinder can't route through open air at all. Idle (no
 * commanded target): hovers/orbits the owner. Commanded (target set via
 * {@code ShikigamiManager#commandAttack}): flies to the target and, once within
 * {@code FlightConfig#attackRange()}, lands hits on its own cooldown — there's no vanilla
 * melee-attack goal left to do this, since MOVE goals are stripped in {@link #onSummon}. What Nue
 * uses; recall is left at the default no-op since clearing the commanded target (done generically
 * by {@code ShikigamiManager#recall} before this behavior is consulted) is enough — the next tick
 * naturally flies it back toward the owner via idle hover.
 */
public class FlightBehavior implements ShikigamiBehavior {

	/**
	 * How far from the owner (and how high above them) it idles/orbits, how fast it flies, how fast
	 * it circles while idle, how close counts as attack range, and the cooldown between hits once in
	 * range of a commanded target.
	 */
	public record FlightConfig(double orbitRadius, double heightOffset, double speed,
			double angularSpeedDegreesPerSecond, double attackRange, long attackIntervalMillis) {
	}

	private final ShikigamiManager manager;
	private final FlightConfig flightConfig;

	// Shikigami UUID -> last time it landed a hit — flying types have no vanilla melee-attack goal
	// left to enforce their own attack-speed cooldown.
	private final Map<UUID, Long> lastAttackMillis = new HashMap<>();

	public FlightBehavior(ShikigamiManager manager, FlightConfig flightConfig) {
		this.manager = manager;
		this.flightConfig = flightConfig;
	}

	@Override
	public void onSummon(Mob mob, ShikigamiType type, boolean tamed) {
		// Movement is driven entirely by tick() below — stop vanilla's own ground-based navigation
		// from fighting that velocity control.
		Bukkit.getMobGoals().removeAllGoals(mob, GoalType.MOVE);
	}

	@Override
	public void tick(Mob shikigami, ShikigamiType type) {
		if (shikigami.isDead()) {
			return;
		}

		Player owner = manager.getOwner(shikigami);
		if (owner == null) {
			return;
		}

		// A sitting Wolf refuses to path anywhere (see GroundMeleeBehavior#onRecall) — a flying
		// shikigami should never be allowed to get stuck sitting mid-air, so this clears it every
		// tick rather than relying on the player never right-clicking it bare-handed.
		if (shikigami instanceof Sittable sittable && sittable.isSitting()) {
			sittable.setSitting(false);
		}

		LivingEntity target = shikigami.getTarget();
		if (target != null && (!target.isValid() || target.isDead())) {
			// Commanded target died/despawned since the last tick — fall back to idle hover instead
			// of flying toward a corpse forever.
			shikigami.setTarget(null);
			target = null;
		}

		Location destination;
		if (target != null) {
			destination = target.getEyeLocation();
			if (shikigami.getLocation().distance(destination) <= flightConfig.attackRange()) {
				tickAttack(shikigami, type, target);
			}
		} else {
			destination = idleHoverPoint(shikigami, owner);
		}

		flyToward(shikigami, destination);
	}

	/**
	 * Lands a hit on {@code target} if {@code shikigami} isn't still on its own attack cooldown —
	 * the melee equivalent of vanilla's {@code MeleeAttackGoal}, hand-rolled since that goal (a MOVE
	 * goal) is stripped in {@link #onSummon}.
	 */
	private void tickAttack(Mob shikigami, ShikigamiType type, LivingEntity target) {
		long now = System.currentTimeMillis();
		Long lastAttack = lastAttackMillis.get(shikigami.getUniqueId());
		if (lastAttack != null && now - lastAttack < flightConfig.attackIntervalMillis()) {
			return;
		}

		lastAttackMillis.put(shikigami.getUniqueId(), now);
		target.damage(type.getDamage(), shikigami);
	}

	/**
	 * A point on a circle of radius {@code orbitRadius} around {@code owner}, at {@code heightOffset}
	 * above them — continuously rotating over real time at {@code angularSpeedDegreesPerSecond},
	 * phase-offset per entity (derived from its UUID) so multiple flying shikigami orbiting the same
	 * owner spread out instead of stacking.
	 */
	private Location idleHoverPoint(Mob shikigami, Player owner) {
		double phaseOffsetRadians = Math.toRadians(shikigami.getUniqueId().hashCode() % 360);
		double angleRadians = Math.toRadians(
				flightConfig.angularSpeedDegreesPerSecond() * (System.currentTimeMillis() / 1000.0))
				+ phaseOffsetRadians;

		return owner.getLocation().clone().add(
				Math.cos(angleRadians) * flightConfig.orbitRadius(),
				flightConfig.heightOffset(),
				Math.sin(angleRadians) * flightConfig.orbitRadius());
	}

	/**
	 * Moves {@code mob} straight toward {@code destination} via {@code Entity#setVelocity}, bypassing
	 * vanilla Pathfinder entirely (ground-based navigation can't route through open air). Overwrites
	 * the full velocity vector — rather than adding to whatever it already was — every tick this is
	 * called, which as a side effect cancels out vanilla gravity's own pull each tick too, without
	 * needing {@code Entity#setGravity(false)}.
	 */
	private void flyToward(Mob mob, Location destination) {
		Vector toDestination = destination.toVector().subtract(mob.getLocation().toVector());
		double distance = toDestination.length();

		if (distance < 0.1) {
			mob.setVelocity(new Vector(0, 0, 0));
			return;
		}

		mob.setVelocity(toDestination.normalize().multiply(Math.min(flightConfig.speed(), distance)));

		Location facing = mob.getLocation();
		facing.setDirection(toDestination);
		mob.setRotation(facing.getYaw(), facing.getPitch());
	}

	@Override
	public boolean supportsTracking() {
		return false;
	}

	@Override
	public void onDespawn(Mob mob, ShikigamiType type) {
		lastAttackMillis.remove(mob.getUniqueId());
	}
}
