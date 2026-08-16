package at.vl.jJK.cursedtechniques.shikigami.behavior;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sittable;
import org.bukkit.util.Vector;

import com.destroystokyo.paper.entity.ai.GoalType;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;

import at.vl.jJK.JJK;
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

	/**
	 * Horizontal and vertical thrust speed while carrying a rider (see {@link #tickCarry}), how far
	 * below the shikigami's own location the rider's seat sits (see {@link #seatLocation}), and how
	 * close it needs to fly to the owner while approaching a pickup (see {@link #tickApproach}) before
	 * actually mounting them.
	 */
	public record CarryConfig(double speed, double verticalSpeed, double seatOffset, double pickupRange) {
	}

	/**
	 * How close it needs to fly to the owner before the shield engages, how far in front of the
	 * owner's current facing it holds position once engaged, the total damage-absorption pool and how
	 * long it lasts, the cooldown after it ends, an optional cursed-energy cost charged once it
	 * engages, and the title-bar message (both lines MiniMessage, times in ticks) shown to the owner
	 * for as long as the shield stays engaged.
	 */
	public record ShieldConfig(double approachRange, double hoverDistance, double maxDamageBlocked,
			long maxDurationMillis, long cooldownMillis, double cursedEnergyCost, String titleText,
			String subtitleText, int titleFadeInTicks, int titleStayTicks, int titleFadeOutTicks) {
	}

	/**
	 * Electric Wing Clap: an instant AoE burst centered on the shikigami's own current location —
	 * damage/radius, cooldown, an optional cursed-energy cost, whether to layer a vanilla lightning
	 * bolt's flash/thunder on top (its color can't be changed — see the particle color below for the
	 * actual "purple" look), and the particle burst itself.
	 */
	public record ClapConfig(double radius, double damage, long cooldownMillis, double cursedEnergyCost,
			boolean useLightningEffect, Particle particleType, int particleCount, double particleSpread,
			double particleSpeed, Color particleColor, double particleSize) {
	}

	private final JJK jjk;
	private final ShikigamiManager manager;
	private final FlightConfig flightConfig;
	private final CarryConfig carryConfig;
	private final ShieldConfig shieldConfig;
	private final ClapConfig clapConfig;

	// Shikigami UUID -> last time it landed a hit — flying types have no vanilla melee-attack goal
	// left to enforce their own attack-speed cooldown.
	private final Map<UUID, Long> lastAttackMillis = new HashMap<>();

	// Shikigami UUID -> the invisible marker-armorstand its rider actually mounts while carrying. A
	// Wolf has no defined rideable seat position, so the owner's default passenger spot would put them
	// somewhere on/above its back; riding this instead — repositioned every tick in tickCarry — is what
	// lets the rider render underneath the shikigami like it's actually carrying them.
	private final Map<UUID, ArmorStand> carrySeats = new HashMap<>();

	// Shikigami UUID -> the owner it's currently flying toward to pick up, between a carry being
	// requested and it actually getting close enough to mount them — see tickApproach/beginCarry.
	private final Map<UUID, Player> pendingPickup = new HashMap<>();

	// Shikigami UUID -> the owner it's currently flying toward for a shield request, between the
	// request and it actually getting close enough to engage — see tickShieldApproach/engageShield.
	private final Map<UUID, Player> pendingShield = new HashMap<>();

	// Shikigami UUID -> its active shield session. Mutable (remainingAbsorb ticks down per hit), so a
	// small holder class rather than a record.
	private static final class ShieldSession {
		final UUID ownerId;
		double remainingAbsorb;
		final long expiresAtMillis;

		ShieldSession(UUID ownerId, double remainingAbsorb, long expiresAtMillis) {
			this.ownerId = ownerId;
			this.remainingAbsorb = remainingAbsorb;
			this.expiresAtMillis = expiresAtMillis;
		}
	}

	private final Map<UUID, ShieldSession> shieldSessions = new HashMap<>();
	private final Map<UUID, Long> shieldCooldownExpiry = new HashMap<>();
	private final Map<UUID, Long> clapCooldownExpiry = new HashMap<>();

	public FlightBehavior(JJK jjk, ShikigamiManager manager, FlightConfig flightConfig, CarryConfig carryConfig,
			ShieldConfig shieldConfig, ClapConfig clapConfig) {
		this.jjk = jjk;
		this.manager = manager;
		this.flightConfig = flightConfig;
		this.carryConfig = carryConfig;
		this.shieldConfig = shieldConfig;
		this.clapConfig = clapConfig;
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

		if (manager.isShielding(shikigami)) {
			tickShieldEngaged(shikigami);
			return;
		}

		Player shieldOwner = pendingShield.get(shikigami.getUniqueId());
		if (shieldOwner != null) {
			tickShieldApproach(shikigami, shieldOwner);
			return;
		}

		if (manager.isCarrying(shikigami)) {
			tickCarry(shikigami, owner);
			return;
		}

		Player pickupOwner = pendingPickup.get(shikigami.getUniqueId());
		if (pickupOwner != null) {
			tickApproach(shikigami, pickupOwner);
			return;
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

	/**
	 * Flies toward {@code owner} in response to a pending pickup request (see
	 * {@link #onCarryToggle}), rather than snapping the seat straight to wherever the shikigami
	 * currently is — it's the shikigami that comes to the player, not the other way around. Once
	 * within {@code CarryConfig#pickupRange()}, the approach ends and the actual mount happens (see
	 * {@link #beginCarry}).
	 */
	private void tickApproach(Mob shikigami, Player owner) {
		if (!owner.isOnline() || !owner.isValid()) {
			pendingPickup.remove(shikigami.getUniqueId());
			return;
		}

		Location destination = owner.getLocation();
		if (shikigami.getLocation().distance(destination) <= carryConfig.pickupRange()) {
			pendingPickup.remove(shikigami.getUniqueId());
			beginCarry(shikigami, owner);
			return;
		}

		flyToward(shikigami, destination);
	}

	/**
	 * Flies toward {@code owner} in response to a pending shield request (see
	 * {@link #onShieldToggle}). Every tick also rechecks the "still wants this" conditions — owner
	 * online/valid, still sneaking, still holding this shikigami's own control item — the same
	 * per-tick recheck {@code Melee#startBarrageChannel} does for its own sneak-hold channel, since a
	 * cancel could arrive as a plain sneak-release event that a race or another plugin could
	 * swallow — ending the approach for free (no cost, no cooldown) rather than being stuck.
	 */
	private void tickShieldApproach(Mob shikigami, Player owner) {
		if (!stillWantsShield(shikigami, owner)) {
			pendingShield.remove(shikigami.getUniqueId());
			return;
		}

		Location destination = owner.getLocation();
		if (shikigami.getLocation().distance(destination) <= shieldConfig.approachRange()) {
			pendingShield.remove(shikigami.getUniqueId());
			engageShield(shikigami, owner);
			return;
		}

		flyToward(shikigami, destination);
	}

	/**
	 * The approach just closed the distance to {@code owner} — locks both of them in place (see
	 * {@link #tickShieldEngaged}), starts the damage-absorption pool, charges the cursed-energy cost
	 * (if any), and stuns the owner for the shield's full max duration (cleared early in
	 * {@link #endShield} if it ends sooner than that).
	 */
	private void engageShield(Mob shikigami, Player owner) {
		manager.markShielding(shikigami);
		manager.registerShieldOwner(owner.getUniqueId(), shikigami.getUniqueId());
		shieldSessions.put(shikigami.getUniqueId(), new ShieldSession(owner.getUniqueId(),
				shieldConfig.maxDamageBlocked(), System.currentTimeMillis() + shieldConfig.maxDurationMillis()));

		if (shieldConfig.cursedEnergyCost() > 0) {
			jjk.getCursedEnergyManager().reduceCursedEnergy(owner, shieldConfig.cursedEnergyCost());
		}

		jjk.getStunManager().stun(owner, shieldConfig.maxDurationMillis());

		// Stay ticks are set long enough to outlast maxDurationMillis regardless of config, since the
		// shield can end earlier than that (pool depleted, sneak released) — endShield() below always
		// clears it explicitly rather than relying on this timing out on its own.
		owner.showTitle(Title.title(
				MiniMessage.miniMessage().deserialize(shieldConfig.titleText()),
				MiniMessage.miniMessage().deserialize(shieldConfig.subtitleText()),
				Title.Times.times(
						Duration.ofMillis(shieldConfig.titleFadeInTicks() * 50L),
						Duration.ofMillis(shieldConfig.titleStayTicks() * 50L),
						Duration.ofMillis(shieldConfig.titleFadeOutTicks() * 50L))));
	}

	/**
	 * Holds the shikigami motionless at a point {@code Shield.HoverDistance} in front of wherever the
	 * owner is currently facing — recomputed every tick so it tracks if they turn, like a directional
	 * ward rather than a fixed prop. Same per-tick "still wants this" recheck as the approach, plus
	 * the session's own expiry.
	 */
	private void tickShieldEngaged(Mob shikigami) {
		ShieldSession session = shieldSessions.get(shikigami.getUniqueId());
		Player owner = manager.getOwner(shikigami);

		if (session == null || owner == null || System.currentTimeMillis() >= session.expiresAtMillis
				|| !stillWantsShield(shikigami, owner)) {
			endShield(shikigami, owner);
			return;
		}

		Location eye = owner.getEyeLocation();
		Vector facing = eye.getDirection().setY(0);
		if (facing.lengthSquared() > 0) {
			facing.normalize();
		}
		Location holdPoint = eye.clone().add(facing.multiply(shieldConfig.hoverDistance()));

		shikigami.teleport(holdPoint);
		shikigami.setVelocity(new Vector(0, 0, 0));
	}

	/**
	 * Whether {@code owner} is still online, still sneaking, and still holding {@code shikigami}'s own
	 * control item — checked every tick during both the shield approach and once engaged, since
	 * "hold-to-maintain" means any of these failing should end it, not just the
	 * {@code PlayerToggleSneakEvent} release {@code ShikigamiListener} normally reacts to.
	 */
	private boolean stillWantsShield(Mob shikigami, Player owner) {
		return owner.isOnline() && owner.isValid() && owner.isSneaking()
				&& manager.isHoldingControlItemFor(owner, shikigami.getUniqueId());
	}

	/**
	 * Ends whatever shield state {@code shikigami} is in — a still-approaching request (free, no
	 * cost/cooldown paid) or an already-engaged shield (clears the owner's stun, starts the cooldown).
	 * Safe to call with nothing pending or active; safe to call with {@code owner == null} (e.g. from
	 * {@link #onDespawn} if they've since gone offline).
	 */
	private void endShield(Mob shikigami, Player owner) {
		pendingShield.remove(shikigami.getUniqueId());
		ShieldSession session = shieldSessions.remove(shikigami.getUniqueId());

		if (session == null) {
			return; // was only pending (or nothing at all) — free to cancel, no cooldown
		}

		manager.clearShielding(shikigami);
		manager.unregisterShieldOwner(session.ownerId);

		if (owner != null) {
			jjk.getStunManager().clearStun(owner);
			owner.resetTitle();
		}

		shieldCooldownExpiry.put(shikigami.getUniqueId(), System.currentTimeMillis() + shieldConfig.cooldownMillis());
	}

	/**
	 * Drives the shikigami while it's carrying its owner as a passenger (see
	 * {@code ShikigamiManager#toggleCarry}) — a non-steerable mob ignores a rider's own WASD/jump
	 * entirely, so movement instead comes from the owner's raw input, cached from Paper's
	 * {@code PlayerInputEvent} (which still reports it regardless of riding state). Horizontal thrust
	 * is relative to the owner's current facing; jump/sneak add vertical thrust. Overwrites the mob's
	 * full velocity every tick, same as {@link #flyToward} — the owner's own position each tick is
	 * just wherever vanilla passenger sync puts them.
	 */
	private void tickCarry(Mob shikigami, Player owner) {
		Input input = manager.getInput(owner.getUniqueId());

		Vector direction = new Vector(0, 0, 0);
		if (input != null) {
			Location ownerLocation = owner.getLocation();
			Vector forward = ownerLocation.getDirection().setY(0);
			if (forward.lengthSquared() > 0) {
				forward.normalize();
			}
			Vector right = new Vector(-forward.getZ(), 0, forward.getX());

			if (input.isForward()) {
				direction.add(forward);
			}
			if (input.isBackward()) {
				direction.subtract(forward);
			}
			if (input.isRight()) {
				direction.add(right);
			}
			if (input.isLeft()) {
				direction.subtract(right);
			}
			if (direction.lengthSquared() > 0) {
				direction.normalize().multiply(carryConfig.speed());
			}

			if (input.isJump()) {
				direction.setY(direction.getY() + carryConfig.verticalSpeed());
			}
			if (input.isSneak()) {
				direction.setY(direction.getY() - carryConfig.verticalSpeed());
			}
		}

		shikigami.setVelocity(direction);

		Vector horizontal = direction.clone().setY(0);
		if (horizontal.lengthSquared() > 0.01) {
			Location facing = shikigami.getLocation();
			facing.setDirection(horizontal);
			shikigami.setRotation(facing.getYaw(), facing.getPitch());
		}

		updateSeatPosition(shikigami);
	}

	@Override
	public boolean supportsTracking() {
		return false;
	}

	@Override
	public boolean supportsCarry() {
		return true;
	}

	/**
	 * {@code ShikigamiManager#toggleCarry} only knows "actively carrying" vs. not — it can't see the
	 * in-between "flying toward the owner to pick them up" state, since that's purely this behavior's
	 * own bookkeeping (see {@link #pendingPickup}). So on a request to start ({@code start == true}),
	 * this decides for itself whether that means "come pick me up" (nothing pending yet) or "never
	 * mind, stop coming" (already mid-approach) — a request to end ({@code start == false}) always
	 * means an active ride is over.
	 */
	@Override
	public void onCarryToggle(Mob mob, ShikigamiType type, Player owner, boolean start) {
		if (!start) {
			endCarry(mob, owner);
			return;
		}

		if (pendingPickup.remove(mob.getUniqueId()) != null) {
			return;
		}

		if (isBusyWithShield(mob)) {
			return;
		}

		mob.setTarget(null);
		pendingPickup.put(mob.getUniqueId(), owner);
	}

	/**
	 * Whether {@code mob} is currently approaching or engaged in a shield — checked before starting a
	 * carry pickup or a clap, so the three "commitments" (carry, shield, clap) stay mutually exclusive
	 * without needing a generic cross-activity concept on {@link ShikigamiBehavior} itself.
	 */
	private boolean isBusyWithShield(Mob mob) {
		return manager.isShielding(mob) || pendingShield.containsKey(mob.getUniqueId());
	}

	/**
	 * Mirror of {@link #isBusyWithShield} for the other direction — checked before starting a shield
	 * approach or a clap.
	 */
	private boolean isBusyWithCarry(Mob mob) {
		return manager.isCarrying(mob) || pendingPickup.containsKey(mob.getUniqueId());
	}

	@Override
	public boolean supportsShield() {
		return true;
	}

	/**
	 * Mirror of {@link #onCarryToggle} for the shield gesture — {@code start == true} means "come
	 * shield me" (a no-op if already busy with a carry, or if this shikigami's shield is still on
	 * cooldown); {@code start == false} ends whatever shield state is currently active, if any (see
	 * {@link #endShield}).
	 */
	@Override
	public void onShieldToggle(Mob mob, ShikigamiType type, Player owner, boolean start) {
		if (!start) {
			endShield(mob, owner);
			return;
		}

		if (pendingShield.containsKey(mob.getUniqueId()) || manager.isShielding(mob)) {
			return;
		}

		if (isBusyWithCarry(mob)) {
			return;
		}

		if (isShieldOnCooldown(mob.getUniqueId())) {
			return;
		}

		mob.setTarget(null);
		pendingShield.put(mob.getUniqueId(), owner);
	}

	@Override
	public boolean isShieldOnCooldown(UUID shikigamiId) {
		Long expiry = shieldCooldownExpiry.get(shikigamiId);
		return expiry != null && expiry > System.currentTimeMillis();
	}

	@Override
	public double absorbShieldDamage(Mob mob, double incomingDamage) {
		ShieldSession session = shieldSessions.get(mob.getUniqueId());
		if (session == null) {
			return 0;
		}

		double absorbed = Math.min(session.remainingAbsorb, incomingDamage);
		session.remainingAbsorb -= absorbed;

		if (session.remainingAbsorb <= 0) {
			endShield(mob, manager.getOwner(mob));
		}

		return absorbed;
	}

	@Override
	public boolean supportsClap() {
		return true;
	}

	/**
	 * Electric Wing Clap: an instant AoE burst right where {@code mob} currently is — no movement
	 * involved, unlike carry/shield. No-op while busy with either of those, or still on its own
	 * cooldown.
	 */
	@Override
	public void onClap(Mob mob, ShikigamiType type, Player owner) {
		if (isBusyWithCarry(mob) || isBusyWithShield(mob)) {
			return;
		}

		if (isClapOnCooldown(mob.getUniqueId())) {
			return;
		}

		if (clapConfig.cursedEnergyCost() > 0) {
			jjk.getCursedEnergyManager().reduceCursedEnergy(owner, clapConfig.cursedEnergyCost());
		}

		Location location = mob.getLocation();
		for (Entity nearby : mob.getWorld().getNearbyEntities(location, clapConfig.radius(),
				clapConfig.radius(), clapConfig.radius())) {
			if (!(nearby instanceof LivingEntity target) || nearby.equals(owner) || nearby.equals(mob)) {
				continue;
			}
			if (manager.getShikigami(nearby.getUniqueId()) != null) {
				continue; // never friendly-fire another shikigami
			}
			target.damage(clapConfig.damage(), mob);
		}

		if (clapConfig.useLightningEffect()) {
			mob.getWorld().strikeLightningEffect(location);
		}

		mob.getWorld().spawnParticle(clapConfig.particleType(), location, clapConfig.particleCount(),
				clapConfig.particleSpread(), clapConfig.particleSpread(), clapConfig.particleSpread(),
				clapConfig.particleSpeed(),
				clapConfig.particleType() == Particle.DUST
						? new Particle.DustOptions(clapConfig.particleColor(), (float) clapConfig.particleSize())
						: null);

		clapCooldownExpiry.put(mob.getUniqueId(), System.currentTimeMillis() + clapConfig.cooldownMillis());
	}

	@Override
	public boolean isClapOnCooldown(UUID shikigamiId) {
		Long expiry = clapCooldownExpiry.get(shikigamiId);
		return expiry != null && expiry > System.currentTimeMillis();
	}

	/**
	 * The approach (see {@link #tickApproach}) just closed the distance to {@code owner} — spawns the
	 * invisible marker-armorstand seat at the shikigami's current (now close-by) location and mounts
	 * the owner on it instead of the shikigami directly, see {@link #carrySeats}.
	 */
	private void beginCarry(Mob shikigami, Player owner) {
		manager.markCarrying(shikigami);

		ArmorStand seat = shikigami.getWorld().spawn(seatLocation(shikigami), ArmorStand.class, stand -> {
			stand.setMarker(true);
			stand.setInvisible(true);
			stand.setInvulnerable(true);
			stand.setGravity(false);
			stand.setSilent(true);
			stand.setPersistent(false);
		});
		manager.markCarrying(seat);
		seat.addPassenger(owner);
		carrySeats.put(shikigami.getUniqueId(), seat);
	}

	/**
	 * Removes the seat (dismounting the owner from it in the process) and gives the owner one-shot
	 * fall-damage immunity — the ride could be ending mid-air (e.g. force-despawned by running out of
	 * cursed energy), with no ground anywhere nearby to check for.
	 */
	private void endCarry(Mob shikigami, Player owner) {
		manager.clearCarrying(shikigami);

		ArmorStand seat = carrySeats.remove(shikigami.getUniqueId());
		if (seat != null) {
			// Cleared before removePassenger() so the EntityDismountEvent it fires is let through by
			// ShikigamiListener#onDismount instead of being (correctly, for vanilla's own
			// sneak-to-dismount) blocked.
			manager.clearCarrying(seat);
			seat.removePassenger(owner);
			seat.remove();
		}
		manager.markFallDamageImmune(owner.getUniqueId());
	}

	/**
	 * {@code seatOffset} below the shikigami's own location — its own location is roughly its feet, so
	 * this puts the seat (and the rider on it) just beneath its belly instead of on its back.
	 */
	private Location seatLocation(Mob shikigami) {
		return shikigami.getLocation().subtract(0, carryConfig.seatOffset(), 0);
	}

	/**
	 * Keeps the seat glued to the shikigami's current location every tick — called from
	 * {@link #tickCarry} after the shikigami's own velocity for this tick is set, using its
	 * not-yet-moved current location, same as the shikigami's own next actual position will be driven
	 * by that velocity a moment later. Not velocity-matched, to avoid any drift accumulating.
	 */
	private void updateSeatPosition(Mob shikigami) {
		ArmorStand seat = carrySeats.get(shikigami.getUniqueId());
		if (seat != null) {
			seat.teleport(seatLocation(shikigami));
		}
	}

	@Override
	public void onDespawn(Mob mob, ShikigamiType type) {
		lastAttackMillis.remove(mob.getUniqueId());
		pendingPickup.remove(mob.getUniqueId());
		clapCooldownExpiry.remove(mob.getUniqueId());
		shieldCooldownExpiry.remove(mob.getUniqueId());

		if (manager.isCarrying(mob)) {
			Player owner = manager.getOwner(mob);
			if (owner != null) {
				endCarry(mob, owner);
			} else {
				// Owner offline/gone — still clean up the orphaned seat, just without the immunity.
				ArmorStand seat = carrySeats.remove(mob.getUniqueId());
				if (seat != null) {
					seat.remove();
				}
			}
		}

		// Covers both a still-pending approach and an already-engaged shield — endShield() no-ops
		// cleanly if neither, and handles owner == null (offline) fine, same as the carry path above.
		endShield(mob, manager.getOwner(mob));
	}
}
