package at.vl.jJK.cursedtechniques.technique;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import at.vl.jJK.JJK;
import at.vl.jJK.cursedtechniques.shikigami.ShikigamiType;

public class TenShadows implements CursedTechnique {
	private JJK jjk;

	private File file;
	private FileConfiguration config;

	/* How the ability works:
	* When a Shikigami is activated, an item is added to the player's
	* inventory with the Shikigami's name, each Shikigami may have different
	* controls but the despawn stays the same:  Shift + Right Click to despawn
	* Available keys: Drop, Right Click Block, Right Click, Shift Right Click Block, Shift */

	private Map<AbilityType, Ability> abilities;

	public TenShadows(JJK jjk) {
		this.jjk = jjk;

		abilities = new HashMap<>();

		// Configs
		file = new File(jjk.getDataFolder(), "abilities/cursedtechniques.yml");

		if (!file.exists()) {
			jjk.saveResource("abilities/cursedtechniques.yml", false);
		}

		load();

		// Divine Dogs' cooldown starts once both dogs have despawned (whether the player only ever
		// had one out or both), not at the moment they're summoned — see activateDivineDogs.
		jjk.getShikigamiManager().addDespawnListener((ownerId, type) -> {
			if (type != ShikigamiType.WHITE_DIVINE_DOG && type != ShikigamiType.BLACK_DIVINE_DOG) {
				return;
			}

			boolean stillHasWhite = jjk.getShikigamiManager().hasActiveShikigami(ownerId, ShikigamiType.WHITE_DIVINE_DOG);
			boolean stillHasBlack = jjk.getShikigamiManager().hasActiveShikigami(ownerId, ShikigamiType.BLACK_DIVINE_DOG);
			if (stillHasWhite || stillHasBlack) {
				return;
			}

			Player owner = Bukkit.getPlayer(ownerId);
			if (owner != null) {
				jjk.getAbilityManager().startCooldown(owner, AbilityType.DIVINE_DOGS, abilities.get(AbilityType.DIVINE_DOGS).getCooldown());
			}
		});
	}

	// Configs
	@Override
	public void load() {
		config = YamlConfiguration.loadConfiguration(file);

		abilities.clear();

		ConfigurationSection section = config.getConfigurationSection("techniques.TEN_SHADOWS.abilities");

		if (section == null) {
			jjk.getLogger().info("No 'abilities' section found in cursedtechniques.yml!");
			return;
		}

		for (String key : section.getKeys(false)) {
			AbilityType abilityType = AbilityType.valueOf(key);
			String path = "techniques.TEN_SHADOWS.abilities." + abilityType.name();

			String displayName = config.getString(path + ".display-name", abilityType.name());
			Material material = Material.valueOf(config.getString(path + ".material", "DIAMOND").toUpperCase());
			int damage = config.getInt(path + ".damage", 0);
			long cooldown = config.getInt(path + ".cooldown", 0);
			double cursedEnergyCost = config.getDouble(path + ".cursed-energy-cost", 0);

			abilityType.load(CursedTechniqueType.TEN_SHADOWS, displayName, material);

			abilities.put(abilityType,
					new Ability(abilityType, displayName, damage,
							cooldown, cursedEnergyCost, false));

		}

	}

	@Override
	public void reload() {
		load();
	}

	// Passive buffs
	@Override
	public void activateTechnique(Player player) {
		player.registerAttribute(Attribute.MAX_HEALTH);
		player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(40);

	}

	@Override
	public void activateAbility(Player player, AbilityType type) {
		switch (type) {
			case DIVINE_DOGS -> activateDivineDogs(player);
			case NUE -> activateNue(player);
		}
	}

	// Divine Dog
	public void activateDivineDogs(Player player) {
		boolean hasWhite = jjk.getShikigamiManager().hasActiveShikigami(player.getUniqueId(), ShikigamiType.WHITE_DIVINE_DOG);
		boolean hasBlack = jjk.getShikigamiManager().hasActiveShikigami(player.getUniqueId(), ShikigamiType.BLACK_DIVINE_DOG);

		// Both already out — nothing to summon, and no cost/cooldown for a no-op.
		if (hasWhite && hasBlack) {
			return;
		}

		// Check if user has enough cursed energy
		if (!(jjk.getCursedEnergyManager().getCursedEnergy(player) >=
				abilities.get(AbilityType.DIVINE_DOGS).getCursedEnergyCost()))
			return;

		// If player is on cooldown return
		if (jjk.getAbilityManager().isOnCooldown(player, AbilityType.DIVINE_DOGS)) {
			return;
		}

		// Cooldown starts once both dogs have despawned again — see the despawn listener
		// registered in the constructor — not here at summon time.

		// Use Cursed Energy
		jjk.getCursedEnergyManager().reduceCursedEnergy(player, abilities.get(AbilityType.DIVINE_DOGS).getCursedEnergyCost());

		// Summon only whichever Divine Dog(s) aren't already out. summon() returns null (and gives
		// no item) if the type isn't currently summonable; the control item is only ever given for
		// a tamed type, since an untamed one has no owner-driven control interface to hand over.
		if (!hasWhite) {
			Mob whiteDog = jjk.getShikigamiManager().summon(player, player.getLocation(), ShikigamiType.WHITE_DIVINE_DOG);
			if (whiteDog != null && jjk.getShikigamiManager().hasTamed(player, ShikigamiType.WHITE_DIVINE_DOG)) {
				jjk.getShikigamiManager().giveShikigamiItem(player, "<#BCAED7>White Divine Dog",
						ShikigamiType.WHITE_DIVINE_DOG, 1, whiteDog.getUniqueId());
			}
		}

		if (!hasBlack) {
			Mob blackDog = jjk.getShikigamiManager().summon(player, player.getLocation(), ShikigamiType.BLACK_DIVINE_DOG);
			if (blackDog != null && jjk.getShikigamiManager().hasTamed(player, ShikigamiType.BLACK_DIVINE_DOG)) {
				jjk.getShikigamiManager().giveShikigamiItem(player, "<#221F28>Black Divine Dog",
						ShikigamiType.BLACK_DIVINE_DOG, 1, blackDog.getUniqueId());
			}
		}
	}

	// Nue
	public void activateNue(Player player) {
		// Already out — nothing to summon, and no cost/cooldown for a no-op.
		if (jjk.getShikigamiManager().hasActiveShikigami(player.getUniqueId(), ShikigamiType.NUE)) {
			return;
		}

		if (!(jjk.getCursedEnergyManager().getCursedEnergy(player) >=
				abilities.get(AbilityType.NUE).getCursedEnergyCost()))
			return;

		if (jjk.getAbilityManager().isOnCooldown(player, AbilityType.NUE)) {
			return;
		}

		jjk.getCursedEnergyManager().reduceCursedEnergy(player, abilities.get(AbilityType.NUE).getCursedEnergyCost());

		// Unlike Divine Dogs (a pair, cooldown deferred until both despawn), Nue is a single
		// shikigami — the cooldown just starts here, at summon time.
		jjk.getAbilityManager().startCooldown(player, AbilityType.NUE, abilities.get(AbilityType.NUE).getCooldown());

		Mob nue = jjk.getShikigamiManager().summon(player, player.getLocation(), ShikigamiType.NUE);
		if (nue != null && jjk.getShikigamiManager().hasTamed(player, ShikigamiType.NUE)) {
			jjk.getShikigamiManager().giveShikigamiItem(player, "<#7A7A8C>Nue", ShikigamiType.NUE, 1, nue.getUniqueId());
		}
	}


}
