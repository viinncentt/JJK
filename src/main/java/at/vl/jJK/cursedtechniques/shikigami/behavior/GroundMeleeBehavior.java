package at.vl.jJK.cursedtechniques.shikigami.behavior;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sittable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import at.vl.jJK.JJK;
import at.vl.jJK.cursedtechniques.shikigami.ShikigamiManager;
import at.vl.jJK.cursedtechniques.shikigami.ShikigamiType;

/**
 * Vanilla ground AI — a tamed Wolf's own follow-owner/sit/melee-attack goals, driven entirely by
 * taming plus {@code commandAttack()}'s {@code setTarget()} — with a charge rush as the secondary
 * (drop) ability. What both Divine Dog types use.
 */
public class GroundMeleeBehavior implements ShikigamiBehavior {

	private static final double RECALL_SPEED = 1.2;

	/**
	 * How fast the shikigami rushes toward the target, how much damage/stun landing the hit deals,
	 * how close counts as a hit, how long it'll keep chasing before giving up, how often it
	 * re-checks during the rush, the trailing particle effect (spawned behind the shikigami as it
	 * charges), and the cooldown before it can be issued again once the current one concludes (hit,
	 * gave up, or was replaced by a new charge command).
	 */
	public record ChargeConfig(double speed, double damage, long stunDurationMillis, long maxDurationMillis,
			double hitRadius, long tickIntervalTicks, Particle particle, int particleCount,
			double particleSpread, double particleSpeed, double particleDistanceBehind, long cooldownMillis) {
	}

	private final JJK jjk;
	private final ShikigamiManager manager;
	private final ChargeConfig chargeConfig;

	// Shikigami UUID -> its in-progress charge task, so a despawn or a re-issued charge can cancel it.
	private final Map<UUID, BukkitTask> chargeTasks = new HashMap<>();

	// Shikigami UUID -> charge cooldown expiry. Only checked when starting a brand new charge (see
	// onSecondaryCommand) — re-issuing one that's already mid-rush freely cancels and restarts it,
	// since the cooldown itself only begins once a charge actually concludes.
	private final Map<UUID, Long> chargeCooldownExpiry = new HashMap<>();

	public GroundMeleeBehavior(JJK jjk, ShikigamiManager manager, ChargeConfig chargeConfig) {
		this.jjk = jjk;
		this.manager = manager;
		this.chargeConfig = chargeConfig;
	}

	/**
	 * Vanilla's own follow-owner goal only activates once the shikigami is already far enough away,
	 * so if it's fairly close (e.g. right after combat) clearing the commanded target alone
	 * (done generically by {@code ShikigamiManager#recall} before this is called) wouldn't visibly
	 * do anything — this issues a one-shot path command instead, using the same Pathfinder vanilla
	 * goals themselves use.
	 */
	@Override
	public void onRecall(Mob mob, ShikigamiType type, Player owner) {
		// A sitting Wolf refuses to path anywhere — vanilla's own AI blocks movement while sitting,
		// so the moveTo() below would silently do nothing unless recall clears it first. Sitting is
		// entirely player-toggled (right-clicking a tamed Wolf without holding its control item is
		// core vanilla behavior), so this can happen without the plugin ever asking for it.
		if (mob instanceof Sittable sittable) {
			sittable.setSitting(false);
		}

		mob.getPathfinder().moveTo(owner.getLocation(), RECALL_SPEED);
	}

	/**
	 * "Charge": rushes {@code target} at an elevated speed. On contact, the target is damaged and
	 * stunned once, then the shikigami switches into normal attack mode ({@code commandAttack}) so
	 * vanilla combat AI keeps fighting it from there. Gives up (no hit, no attack) if it can't reach
	 * the target within the configured max duration. Issuing a new charge replaces any charge
	 * already running for this shikigami.
	 */
	@Override
	public boolean onSecondaryCommand(Mob shikigami, ShikigamiType type, LivingEntity target) {
		UUID shikigamiId = shikigami.getUniqueId();

		BukkitTask existing = chargeTasks.remove(shikigamiId);
		if (existing == null && isSecondaryCommandOnCooldown(shikigamiId)) {
			return false;
		}
		if (existing != null) {
			existing.cancel();
		}

		long startMillis = System.currentTimeMillis();

		BukkitTask task = Bukkit.getScheduler().runTaskTimer(jjk, () -> {
			boolean stillChasing = manager.getShikigami(shikigamiId) != null && target.isValid()
					&& System.currentTimeMillis() - startMillis < chargeConfig.maxDurationMillis();

			if (!stillChasing) {
				BukkitTask self = chargeTasks.remove(shikigamiId);
				if (self != null) {
					self.cancel();
				}
				startChargeCooldown(shikigamiId);
				return;
			}

			if (shikigami.getLocation().distance(target.getLocation()) <= chargeConfig.hitRadius()) {
				BukkitTask self = chargeTasks.remove(shikigamiId);
				if (self != null) {
					self.cancel();
				}
				startChargeCooldown(shikigamiId);

				target.damage(chargeConfig.damage(), shikigami);
				jjk.getStunManager().stun(target, chargeConfig.stunDurationMillis());

				manager.commandAttack(shikigamiId, target);
				return;
			}

			spawnChargeTrailParticle(shikigami);
			shikigami.getPathfinder().moveTo(target.getLocation(), chargeConfig.speed());
		}, 0L, chargeConfig.tickIntervalTicks());

		chargeTasks.put(shikigamiId, task);
		return true;
	}

	private void startChargeCooldown(UUID shikigamiId) {
		chargeCooldownExpiry.put(shikigamiId, System.currentTimeMillis() + chargeConfig.cooldownMillis());
	}

	@Override
	public boolean isSecondaryCommandOnCooldown(UUID shikigamiId) {
		Long expiry = chargeCooldownExpiry.get(shikigamiId);
		return expiry != null && expiry > System.currentTimeMillis();
	}

	/**
	 * Spawns one burst of the charge trail particle behind {@code shikigami}, offset opposite its
	 * current facing direction so the trail reads as being left behind mid-rush rather than out in
	 * front of it.
	 */
	private void spawnChargeTrailParticle(Mob shikigami) {
		Location location = shikigami.getLocation();
		Vector behind = location.getDirection().normalize().multiply(-chargeConfig.particleDistanceBehind());
		Location trailLocation = location.clone().add(behind).add(0, shikigami.getHeight() / 2, 0);

		shikigami.getWorld().spawnParticle(chargeConfig.particle(), trailLocation, chargeConfig.particleCount(),
				chargeConfig.particleSpread(), chargeConfig.particleSpread(), chargeConfig.particleSpread(),
				chargeConfig.particleSpeed());
	}

	@Override
	public void onDespawn(Mob mob, ShikigamiType type) {
		BukkitTask task = chargeTasks.remove(mob.getUniqueId());
		if (task != null) {
			task.cancel();
		}
		chargeCooldownExpiry.remove(mob.getUniqueId());
	}
}
