package at.vl.jJK.cursedtechniques.melee;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import at.vl.jJK.JJK;

/**
 * Owns {@code abilities/melee.yml} and every melee move's actual effect — structured the same way
 * {@link TenShadows} owns Ten Shadows' abilities. {@code managers.MeleeManager} stays the thin
 * per-player unlock/cooldown layer (mirroring {@code AbilityManager}) and delegates activation here.
 */
public class Melee {
	private final JJK jjk;

	private File file;
	private FileConfiguration config;

	// Barrage-specific config — a channeled ability, not something every MeleeType needs, so these
	// live here rather than as generic fields on MeleeType itself.
	private double barrageDamage;
	private long barrageChannelMaxDurationMillis;
	private long barrageChannelTickIntervalTicks;
	private long barrageSwingIntervalTicks;
	private double barrageForwardDistance;
	private double barrageRadius;
	private Particle barrageParticle;
	private int barrageParticleCount;
	private double barrageParticleSpeed;
	private double barrageParticleSpread;
	private double barrageParticleTargetOffset;
	private long barrageStunDurationMillis;

	private final Map<UUID, ChannelTasks> activeBarrageChannels = new HashMap<>();

	private record ChannelTasks(BukkitTask tickTask, BukkitTask swingTask, AtomicBoolean targetInRange) {
	}

	public Melee(JJK jjk) {
		this.jjk = jjk;

		file = new File(jjk.getDataFolder(), "abilities/melee.yml");
		if (!file.exists()) {
			jjk.saveResource("abilities/melee.yml", false);
		}

		load();
	}

	public void load() {
		config = YamlConfiguration.loadConfiguration(file);

		ConfigurationSection section = config.getConfigurationSection("moves");
		if (section == null) {
			jjk.getLogger().info("No 'moves' section found in melee.yml!");
			return;
		}

		for (MeleeType type : MeleeType.values()) {
			String path = "moves." + type.name();

			String displayName = config.getString(path + ".display-name", type.name());
			String materialName = config.getString(path + ".material", "IRON_SWORD");

			Material material;
			try {
				material = Material.valueOf(materialName.toUpperCase());
			} catch (IllegalArgumentException e) {
				jjk.getLogger().warning("Invalid material '" + materialName + "' for melee move " + type.name()
						+ ", defaulting to IRON_SWORD");
				material = Material.IRON_SWORD;
			}

			long cooldownMillis = config.getLong(path + ".cooldown", 0);
			double cursedEnergyCost = config.getDouble(path + ".cursed-energy-cost", 0);

			type.load(displayName, material, cooldownMillis, cursedEnergyCost);
		}

		loadBarrageConfig();
	}

	private void loadBarrageConfig() {
		String path = "moves.BARRAGE";

		barrageDamage = config.getDouble(path + ".damage", 1.0);
		barrageChannelMaxDurationMillis = config.getLong(path + ".channel-max-duration", 5000);
		barrageChannelTickIntervalTicks = config.getLong(path + ".channel-tick-interval", 2);
		barrageSwingIntervalTicks = config.getLong(path + ".channel-swing-interval", 1);
		barrageForwardDistance = config.getDouble(path + ".channel-forward-distance", 1.5);
		barrageRadius = config.getDouble(path + ".channel-radius", 1.5);

		String barrageParticleName = config.getString(path + ".channel-particle-type", "CRIT");
		try {
			barrageParticle = Particle.valueOf(barrageParticleName.toUpperCase());
		} catch (IllegalArgumentException e) {
			jjk.getLogger().warning("Invalid particle '" + barrageParticleName + "' for Barrage, defaulting to CRIT");
			barrageParticle = Particle.CRIT;
		}

		barrageParticleCount = config.getInt(path + ".channel-particle-count", 4);
		barrageParticleSpeed = config.getDouble(path + ".channel-particle-speed", 0.02);
		barrageParticleSpread = config.getDouble(path + ".channel-particle-spread", 0.15);
		barrageParticleTargetOffset = config.getDouble(path + ".channel-particle-target-offset", 0.3);
		barrageStunDurationMillis = config.getLong(path + ".stun-duration", 1500);
	}

	public void reload() {
		load();
	}

	public void activateMove(Player player, MeleeType type) {
		switch (type) {
			// Barrage is never triggered by a left-click — it's a sneak-and-slot channel, started/
			// stopped by BarrageListener via startBarrageChannel()/stopBarrageChannel() instead.
			case BARRAGE -> {
			}
			case UPPERCUT -> activateUppercut(player);
		}
	}

	private void activateUppercut(Player player) {
		if (!checkAndStartCooldown(player, MeleeType.UPPERCUT)) {
			return;
		}

		// TODO: real effect.
	}

	/**
	 * Starts a Barrage channel for {@code player}, if they're not already channeling and aren't on
	 * cooldown. Runs the cost/cooldown-independent tick loop until it's stopped by
	 * {@link #stopBarrageChannel}, called from {@code BarrageListener} once sneak or the held slot
	 * condition stops holding, or once {@code channel-max-duration} elapses.
	 */
	public void startBarrageChannel(Player player) {
		UUID playerId = player.getUniqueId();
		if (activeBarrageChannels.containsKey(playerId)) {
			return;
		}

		if (jjk.getCursedEnergyManager().getCursedEnergy(player) < MeleeType.BARRAGE.getCursedEnergyCost()) {
			return;
		}

		if (jjk.getMeleeManager().isOnCooldown(player, MeleeType.BARRAGE)) {
			return;
		}

		jjk.getCursedEnergyManager().reduceCursedEnergy(player, MeleeType.BARRAGE.getCursedEnergyCost());

		Set<UUID> stunnedTargets = new HashSet<>();
		AtomicBoolean targetInRange = new AtomicBoolean(false);
		long startMillis = System.currentTimeMillis();

		BukkitTask tickTask = Bukkit.getScheduler().runTaskTimer(jjk, () -> {
			int heldSlot = player.getInventory().getHeldItemSlot();
			boolean stillChanneling = player.isOnline()
					&& player.isSneaking()
					&& jjk.getBindManager().getBoundAbility(player, heldSlot) == MeleeType.BARRAGE
					&& System.currentTimeMillis() - startMillis < barrageChannelMaxDurationMillis;

			if (!stillChanneling) {
				stopBarrageChannel(player);
				return;
			}

			tickBarrage(player, stunnedTargets, targetInRange);
		}, 0L, barrageChannelTickIntervalTicks);

		// Purely visual, and runs on its own configurable interval independent of the
		// damage/particle tick loop above — broadcasts the arm-swing animation to everyone nearby
		// without going through the real click-input path, so it can't loop back and re-trigger
		// the ability. Gated on targetInRange so the player only visibly throws punches while an
		// entity is actually within the hit area, instead of swinging at thin air the whole channel.
		BukkitTask swingTask = Bukkit.getScheduler().runTaskTimer(jjk, () -> {
			if (targetInRange.get()) {
				player.swingMainHand();
			}
		}, 0L, barrageSwingIntervalTicks);

		activeBarrageChannels.put(playerId, new ChannelTasks(tickTask, swingTask, targetInRange));
	}

	private void tickBarrage(Player player, Set<UUID> stunnedTargets, AtomicBoolean targetInRange) {
		Location eye = player.getEyeLocation();
		Location front = eye.clone().add(eye.getDirection().normalize().multiply(barrageForwardDistance));

		boolean hitAnyTarget = false;

		for (Entity entity : player.getWorld().getNearbyEntities(front, barrageRadius, barrageRadius, barrageRadius)) {
			if (!(entity instanceof LivingEntity target) || target.equals(player)) {
				continue;
			}

			if (target.getLocation().distance(front) > barrageRadius) {
				continue;
			}

			hitAnyTarget = true;

			// Particles spawn on the side of the target facing the attacker, not centered inside
			// their hitbox — makes it read as punches actually landing on the near side of them.
			Vector towardAttacker = player.getEyeLocation().toVector().subtract(target.getEyeLocation().toVector());
			if (towardAttacker.lengthSquared() > 0) {
				towardAttacker.normalize();
			}
			Location hitLocation = target.getEyeLocation().add(towardAttacker.multiply(barrageParticleTargetOffset));
			target.getWorld().spawnParticle(barrageParticle, hitLocation, barrageParticleCount,
					barrageParticleSpread, barrageParticleSpread, barrageParticleSpread, barrageParticleSpeed);

			// Damage applies every tick a target is caught in range — only the stun is one-time,
			// on the first tick a given target is hit during this channel.
			Vector velocityBeforeHit = target.getVelocity();
			target.damage(barrageDamage, player);
			target.setVelocity(velocityBeforeHit);

			if (stunnedTargets.add(target.getUniqueId())) {
				jjk.getStunManager().stun(target, barrageStunDurationMillis);
			}
		}

		targetInRange.set(hitAnyTarget);
	}

	/**
	 * Stops {@code player}'s Barrage channel if one is running, and starts the normal move cooldown
	 * — cooldown covers recovery time after a channel ends (whether it timed out or was cut short),
	 * not a fixed window from when it started.
	 */
	public void stopBarrageChannel(Player player) {
		ChannelTasks tasks = activeBarrageChannels.remove(player.getUniqueId());
		if (tasks == null) {
			return;
		}

		tasks.tickTask().cancel();
		tasks.swingTask().cancel();
		jjk.getMeleeManager().startCooldown(player, MeleeType.BARRAGE, MeleeType.BARRAGE.getCooldownMillis());
	}

	/**
	 * Checks cursed energy and cooldown, then starts the cooldown and deducts the cost — same order
	 * as {@link TenShadows#activateDivineDogs}. Returns false (doing nothing else) if either check
	 * fails.
	 */
	private boolean checkAndStartCooldown(Player player, MeleeType type) {
		if (jjk.getCursedEnergyManager().getCursedEnergy(player) < type.getCursedEnergyCost()) {
			return false;
		}

		if (jjk.getMeleeManager().isOnCooldown(player, type)) {
			return false;
		}

		jjk.getMeleeManager().startCooldown(player, type, type.getCooldownMillis());
		jjk.getCursedEnergyManager().reduceCursedEnergy(player, type.getCursedEnergyCost());

		return true;
	}
}
