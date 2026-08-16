package at.vl.jJK.cursedtechniques.shikigami;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import com.destroystokyo.paper.entity.ai.GoalType;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.color.Color;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;

import at.vl.jJK.JJK;
import at.vl.jJK.cursedtechniques.shikigami.behavior.FlightBehavior;
import at.vl.jJK.cursedtechniques.shikigami.behavior.GroundMeleeBehavior;
import at.vl.jJK.cursedtechniques.shikigami.behavior.ShikigamiBehavior;
import at.vl.jJK.managers.JJKManager;

public class ShikigamiManager implements JJKManager, PacketListener {
	public static final String ENTITY_ID_KEY = "shikigami_entity_id";

	private static final int ENTITY_FLAGS_INDEX = 0;
	private static final byte GLOWING_BIT = 0x40;

	// Falls back to this no-op instance for a type with no registered behavior (shouldn't happen for
	// anything loadConfig() produced, but keeps every call site null-check-free regardless).
	private static final ShikigamiBehavior DEFAULT_BEHAVIOR = new ShikigamiBehavior() {
	};

	private final JJK jjk;
	private final NamespacedKey ownerKey;
	private final NamespacedKey typeKey;
	private final NamespacedKey entityIdKey;
	private final NamespacedKey carryingKey;
	private final NamespacedKey shieldingKey;

	private final Map<UUID, Mob> activeShikigami = new HashMap<>();

	// Player UUID -> their latest raw movement input, cached from PlayerInputEvent (which reports
	// WASD/jump/sneak intent even while riding a non-steerable passenger vehicle, unlike vanilla
	// movement itself). Only ever consulted while that player is being carried — see
	// FlightBehavior#tickCarry.
	private final Map<UUID, Input> latestInput = new ConcurrentHashMap<>();

	// Player UUID -> pending one-shot fall-damage immunity, consumed by the next FALL damage event —
	// see markFallDamageImmune/consumeFallDamageImmunity.
	private final Set<UUID> fallDamageImmune = ConcurrentHashMap.newKeySet();

	// Owner UUID -> the shikigami currently shielding them, so a player-keyed EntityDamageEvent (see
	// ShikigamiListener#onDamage) can find its way back to the right FlightBehavior#absorbShieldDamage
	// call without needing to know which shikigami it belongs to.
	private final Map<UUID, UUID> shieldingOwners = new ConcurrentHashMap<>();

	// Shikigami UUID -> the entity that specific shikigami currently has tracked (via the "track"
	// ability), glowing for its owner only. Keyed per-shikigami (not per-owner) so two shikigami
	// owned by the same player can each track a different entity at once. Stores the numeric
	// entity ID alongside the UUID since onPacketSend runs off the main thread and can't safely
	// call Bukkit.getEntity(UUID) there (Bukkit.getEntity(int) doesn't exist).
	private final Map<UUID, TrackedTarget> trackedTargets = new ConcurrentHashMap<>();

	private record TrackedTarget(UUID ownerId, UUID entityUuid, int entityId) {
	}
	// Shikigami UUID -> track-ability cooldown expiry (millis)
	private final Map<UUID, Long> trackCooldownExpiry = new HashMap<>();

	// ShikigamiType -> its AI archetype, built by loadConfig()/loadBehavior() from each type's
	// configured `Behavior` — see ShikigamiBehavior for why this replaces per-type switch statements.
	private final Map<ShikigamiType, ShikigamiBehavior> behaviors = new HashMap<>();

	// Shikigami UUID -> its owner, for the standing "highlight the shikigami itself" glow.
	// Read from onPacketSend (off the main thread), so like TrackedTarget it only holds plain
	// data — no live entity/PDC lookups there.
	private final Map<UUID, ShikigamiOwner> shikigamiOwners = new ConcurrentHashMap<>();

	private record ShikigamiOwner(UUID ownerId, int entityId) {
	}

	// Notified whenever a shikigami is despawned, after it's already removed from
	// activeShikigami — lets ability implementations (e.g. Divine Dogs) react to a shikigami they
	// summoned going away, such as starting their own ability cooldown only once despawned.
	private final List<DespawnListener> despawnListeners = new ArrayList<>();

	public interface DespawnListener {
		void onDespawn(UUID ownerId, ShikigamiType type);
	}

	private boolean highlightEnabled;

	private long cursedEnergyDrainPeriodTicks;
	private long regenPeriodTicks;
	private long itemLoreUpdatePeriodTicks;
	private long aiTickPeriodTicks;

	private BukkitTask drainTask;
	private BukkitTask regenTask;
	private BukkitTask itemLoreUpdateTask;
	private BukkitTask aiTickTask;

	private File file;
	private FileConfiguration config;

	public ShikigamiManager(JJK jjk) {
		this.jjk = jjk;
		this.ownerKey = new NamespacedKey(jjk, "shikigami_owner");
		this.typeKey = new NamespacedKey(jjk, "shikigami_type");
		this.entityIdKey = new NamespacedKey(jjk, ENTITY_ID_KEY);
		this.carryingKey = new NamespacedKey(jjk, "shikigami_carrying");
		this.shieldingKey = new NamespacedKey(jjk, "shikigami_shielding");

		file = new File(jjk.getDataFolder(), "abilities/shikigamis.yml");
		if (!file.exists()) {
			jjk.saveResource("abilities/shikigamis.yml", false);
		}

		loadConfig();
	}

	public void reload() {
		loadConfig();
	}

	public void addDespawnListener(DespawnListener listener) {
		despawnListeners.add(listener);
	}

	public FileConfiguration getConfig() {
		return config;
	}

	private void loadConfig() {
		config = YamlConfiguration.loadConfiguration(file);

		cursedEnergyDrainPeriodTicks = config.getLong("CursedEnergy.DrainPeriodTicks");
		regenPeriodTicks = config.getLong("Regen.PeriodTicks", 200);
		itemLoreUpdatePeriodTicks = config.getLong("ItemLoreUpdatePeriodTicks", 10);
		// Runs every tick by default — FlightBehavior's velocity overwrite is what cancels out
		// vanilla gravity each tick, so a coarser interval would show up as visible falling/bobbing
		// in between updates for flying types. Ground types' tick() is a no-op, so this costs them
		// nothing regardless of interval.
		aiTickPeriodTicks = config.getLong("AiTickPeriodTicks", 1);
		highlightEnabled = config.getBoolean("Highlight.Enabled", true);

		behaviors.clear();

		for (ShikigamiType type : ShikigamiType.values()) {
			String typePath = "Types." + type.name();
			String displayName = config.getString(typePath + ".DisplayName", type.name());
			String raw = config.getString(typePath + ".EntityType", "WOLF");
			double drainAmount = config.getDouble(typePath + ".CursedEnergyDrainAmount");
			long trackCooldownMillis = config.getLong(typePath + ".Track.CooldownMillis");
			double trackCursedEnergyCost = config.getDouble(typePath + ".Track.CursedEnergyCost");

			TextColor trackLocatorColor = parseColor(typePath + ".Track.Color", "YELLOW", type);
			TextColor trackEntityColor = parseColor(typePath + ".Track.EntityColor", "YELLOW", type);
			TextColor highlightColor = parseColor(typePath + ".HighlightColor", "WHITE", type);

			double scale = config.getDouble(typePath + ".Scale", 1.0);
			double speed = config.getDouble(typePath + ".Speed", 0.3);
			double damage = config.getDouble(typePath + ".Damage", 4.0);
			double health = config.getDouble(typePath + ".Health", 20.0);
			double regenAmount = config.getDouble(typePath + ".RegenAmount", 0.05);
			double despawnedRegenPerSecond = config.getDouble(typePath + ".DespawnedRegenPerSecond", 0.1);
			double followRange = config.getDouble(typePath + ".FollowRange", 64.0);
			boolean defaultTamed = config.getBoolean(typePath + ".DefaultTamed", true);
			boolean defaultSummonable = config.getBoolean(typePath + ".DefaultSummonable", true);

			ShikigamiType.ControlItemLore controlItemLore = new ShikigamiType.ControlItemLore(
					loadControlItemLoreLines(typePath));

			try {
				type.load(displayName, EntityType.valueOf(raw.toUpperCase()), drainAmount, trackCooldownMillis,
						trackCursedEnergyCost, trackLocatorColor, trackEntityColor, highlightColor, scale, speed,
						damage, health, regenAmount, despawnedRegenPerSecond, followRange, defaultTamed,
						defaultSummonable, controlItemLore);
			} catch (IllegalArgumentException e) {
				jjk.getLogger().warning("Invalid entity type '" + raw + "' for shikigami type " + type.name()
						+ ", defaulting to WOLF");
				type.load(displayName, EntityType.WOLF, drainAmount, trackCooldownMillis, trackCursedEnergyCost,
						trackLocatorColor, trackEntityColor, highlightColor, scale, speed, damage, health,
						regenAmount, despawnedRegenPerSecond, followRange, defaultTamed, defaultSummonable,
						controlItemLore);
			}

			behaviors.put(type, loadBehavior(type, typePath));
		}
	}

	// The one place a shikigami's AI is actually selected. Reusing an existing archetype for a new
	// type needs no Java changes at all — just this config key and the archetype's own config block.
	private enum BehaviorArchetype {
		GROUND_MELEE,
		FLIGHT
	}

	/**
	 * Builds the {@link ShikigamiBehavior} for {@code type} from its configured {@code Behavior}
	 * archetype ({@code GROUND_MELEE} by default, or {@code FLIGHT}).
	 */
	private ShikigamiBehavior loadBehavior(ShikigamiType type, String typePath) {
		String archetypeRaw = config.getString(typePath + ".Behavior", "GROUND_MELEE");
		BehaviorArchetype archetype;
		try {
			archetype = BehaviorArchetype.valueOf(archetypeRaw.toUpperCase());
		} catch (IllegalArgumentException e) {
			jjk.getLogger().warning("Invalid behavior '" + archetypeRaw + "' for shikigami type " + type.name()
					+ ", defaulting to GROUND_MELEE");
			archetype = BehaviorArchetype.GROUND_MELEE;
		}

		return switch (archetype) {
			case FLIGHT -> new FlightBehavior(jjk, this, loadFlightConfig(typePath), loadCarryConfig(typePath),
					loadShieldConfig(typePath), loadClapConfig(typePath));
			case GROUND_MELEE -> new GroundMeleeBehavior(jjk, this, loadChargeConfig(typePath));
		};
	}

	private FlightBehavior.CarryConfig loadCarryConfig(String typePath) {
		return new FlightBehavior.CarryConfig(
				config.getDouble(typePath + ".Carry.Speed", 1.2),
				config.getDouble(typePath + ".Carry.VerticalSpeed", 1.0),
				config.getDouble(typePath + ".Carry.SeatOffset", 0.6),
				config.getDouble(typePath + ".Carry.PickupRange", 1.5));
	}

	private FlightBehavior.ShieldConfig loadShieldConfig(String typePath) {
		return new FlightBehavior.ShieldConfig(
				config.getDouble(typePath + ".Shield.ApproachRange", 1.5),
				config.getDouble(typePath + ".Shield.HoverDistance", 1.5),
				config.getDouble(typePath + ".Shield.MaxDamageBlocked", 40.0),
				config.getLong(typePath + ".Shield.MaxDurationMillis", 6000),
				config.getLong(typePath + ".Shield.CooldownMillis", 15000),
				config.getDouble(typePath + ".Shield.CursedEnergyCost", 0),
				config.getString(typePath + ".Shield.Title.Text", "<#984DC4>Shielded!"),
				config.getString(typePath + ".Shield.Title.Subtitle", "<#74668F>Nue is protecting you"),
				config.getInt(typePath + ".Shield.Title.FadeInTicks", 10),
				config.getInt(typePath + ".Shield.Title.StayTicks", 200),
				config.getInt(typePath + ".Shield.Title.FadeOutTicks", 10));
	}

	private FlightBehavior.ClapConfig loadClapConfig(String typePath) {
		String particleRaw = config.getString(typePath + ".Clap.Particle.Type", "DUST");
		Particle clapParticle;
		try {
			clapParticle = Particle.valueOf(particleRaw.toUpperCase());
		} catch (IllegalArgumentException e) {
			jjk.getLogger().warning("Invalid particle '" + particleRaw + "' at '" + typePath
					+ ".Clap.Particle.Type', defaulting to DUST");
			clapParticle = Particle.DUST;
		}

		return new FlightBehavior.ClapConfig(
				config.getDouble(typePath + ".Clap.Radius", 4.0),
				config.getDouble(typePath + ".Clap.Damage", 12.0),
				config.getLong(typePath + ".Clap.CooldownMillis", 8000),
				config.getDouble(typePath + ".Clap.CursedEnergyCost", 0),
				config.getBoolean(typePath + ".Clap.UseLightningEffect", true),
				clapParticle,
				config.getInt(typePath + ".Clap.Particle.Count", 40),
				config.getDouble(typePath + ".Clap.Particle.Spread", 1.5),
				config.getDouble(typePath + ".Clap.Particle.Speed", 0.05),
				parseBukkitColor(typePath + ".Clap.Particle.Color", "#8B2FC9"),
				config.getDouble(typePath + ".Clap.Particle.Size", 1.5));
	}

	private GroundMeleeBehavior.ChargeConfig loadChargeConfig(String typePath) {
		String chargeParticleRaw = config.getString(typePath + ".Charge.Particle.Type", "CLOUD");
		Particle chargeParticle;
		try {
			chargeParticle = Particle.valueOf(chargeParticleRaw.toUpperCase());
		} catch (IllegalArgumentException e) {
			jjk.getLogger().warning("Invalid particle '" + chargeParticleRaw + "' at '" + typePath
					+ ".Charge.Particle.Type', defaulting to CLOUD");
			chargeParticle = Particle.CLOUD;
		}

		return new GroundMeleeBehavior.ChargeConfig(
				config.getDouble(typePath + ".Charge.Speed", 2.0),
				config.getDouble(typePath + ".Charge.Damage", 5.0),
				config.getLong(typePath + ".Charge.StunDurationMillis", 1000),
				config.getLong(typePath + ".Charge.MaxDurationMillis", 5000),
				config.getDouble(typePath + ".Charge.HitRadius", 1.5),
				config.getLong(typePath + ".Charge.TickInterval", 1),
				chargeParticle,
				config.getInt(typePath + ".Charge.Particle.Count", 3),
				config.getDouble(typePath + ".Charge.Particle.Spread", 0.15),
				config.getDouble(typePath + ".Charge.Particle.Speed", 0.02),
				config.getDouble(typePath + ".Charge.Particle.DistanceBehind", 1.0),
				config.getLong(typePath + ".Charge.CooldownMillis", 5000));
	}

	private FlightBehavior.FlightConfig loadFlightConfig(String typePath) {
		return new FlightBehavior.FlightConfig(
				config.getDouble(typePath + ".Flight.OrbitRadius", 4.0),
				config.getDouble(typePath + ".Flight.HeightOffset", 4.0),
				config.getDouble(typePath + ".Flight.Speed", 1.0),
				config.getDouble(typePath + ".Flight.AngularSpeedDegreesPerSecond", 20.0),
				config.getDouble(typePath + ".Flight.AttackRange", 2.0),
				config.getLong(typePath + ".Flight.AttackIntervalMillis", 1000));
	}

	private ShikigamiBehavior behaviorFor(ShikigamiType type) {
		return behaviors.getOrDefault(type, DEFAULT_BEHAVIOR);
	}

	// Used only if a type's config has no Lore.Line1 at all (e.g. a fresh/corrupted config missing
	// the whole section) — otherwise loadControlItemLoreLines reads whatever's actually configured.
	private static final List<String> DEFAULT_CONTROL_ITEM_LORE_LINES = List.of(
			"<#74668F>Press <key:key.sneak> + Right Click to despawn",
			"<#74668F>Right Click a Block to recall",
			"<#74668F>Right Click while looking at Target to attack",
			"<#74668F>Right Click an Entity to track them",
			"<#74668F>Press <key:key.drop> while looking at Target to charge");

	/**
	 * Reads {@code Lore.Line1}, {@code Lore.Line2}, ... from {@code typePath}, stopping at the first
	 * missing line number — so a type can configure as many or as few lore lines as it wants, not
	 * just the fixed 5 the defaults happen to use.
	 */
	private List<String> loadControlItemLoreLines(String typePath) {
		List<String> lines = new ArrayList<>();

		int lineNumber = 1;
		String line;
		while ((line = config.getString(typePath + ".Lore.Line" + lineNumber)) != null) {
			lines.add(line);
			lineNumber++;
		}

		return lines.isEmpty() ? DEFAULT_CONTROL_ITEM_LORE_LINES : lines;
	}

	// Per-type keys on the OWNER's own PersistentDataContainer (not the mob's) for the health a
	// shikigami had when it was last despawned — this is what makes it survive a server restart,
	// unlike the mob entity itself which gets removed on despawn.
	private NamespacedKey savedHealthKey(ShikigamiType type) {
		return new NamespacedKey(jjk, "shikigami_saved_health_" + type.name());
	}

	private NamespacedKey savedHealthTimeKey(ShikigamiType type) {
		return new NamespacedKey(jjk, "shikigami_saved_health_time_" + type.name());
	}

	private NamespacedKey tamedKey(ShikigamiType type) {
		return new NamespacedKey(jjk, "shikigami_tamed_" + type.name());
	}

	/**
	 * Whether {@code player} has tamed this shikigami type — per-player, not global. Falls back to
	 * {@link ShikigamiType#isDefaultTamed()} the first time a player ever summons a given type
	 * (e.g. Divine Dogs start tamed for everyone; other types have to be earned, see
	 * {@link #handleKilled}).
	 */
	public boolean hasTamed(Player player, ShikigamiType type) {
		Boolean tamed = player.getPersistentDataContainer().get(tamedKey(type), PersistentDataType.BOOLEAN);
		return tamed != null ? tamed : type.isDefaultTamed();
	}

	public void setTamed(Player player, ShikigamiType type, boolean tamed) {
		player.getPersistentDataContainer().set(tamedKey(type), PersistentDataType.BOOLEAN, tamed);
	}

	private NamespacedKey summonableKey(ShikigamiType type) {
		return new NamespacedKey(jjk, "shikigami_summonable_" + type.name());
	}

	/**
	 * Whether {@code player} can summon this shikigami type — per-player, not global. Falls back to
	 * {@link ShikigamiType#isDefaultSummonable()} the first time a player's state is looked up.
	 */
	public boolean hasSummonable(Player player, ShikigamiType type) {
		Boolean summonable = player.getPersistentDataContainer().get(summonableKey(type), PersistentDataType.BOOLEAN);
		return summonable != null ? summonable : type.isDefaultSummonable();
	}

	public void setSummonable(Player player, ShikigamiType type, boolean summonable) {
		player.getPersistentDataContainer().set(summonableKey(type), PersistentDataType.BOOLEAN, summonable);
	}

	/**
	 * Called from {@code ShikigamiListener#onDeath} whenever a shikigami dies with a known killer.
	 * If the killer is the shikigami's own owner and they hadn't already tamed this type, killing
	 * their own untamed one earns them taming rights for it going forward — and clears any saved
	 * despawn health so the first real summon starts fresh rather than half-dead.
	 */
	public void handleKilled(Mob shikigami, Player killer) {
		String ownerRaw = shikigami.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
		String typeRaw = shikigami.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
		if (ownerRaw == null || typeRaw == null) {
			return;
		}

		if (!killer.getUniqueId().toString().equals(ownerRaw)) {
			return;
		}

		ShikigamiType type = ShikigamiType.valueOf(typeRaw);
		if (hasTamed(killer, type)) {
			return;
		}

		setTamed(killer, type, true);
		killer.getPersistentDataContainer().remove(savedHealthKey(type));
		killer.getPersistentDataContainer().remove(savedHealthTimeKey(type));
	}

	private TextColor parseColor(String path, String defaultColorName, ShikigamiType type) {
		String raw = config.getString(path, defaultColorName);
		TextColor color = TextColor.fromHexString(raw);
		if (color == null) {
			color = NamedTextColor.NAMES.value(raw.toLowerCase());
		}
		if (color == null) {
			jjk.getLogger().warning("Invalid color '" + raw + "' at '" + path + "' for shikigami type " + type.name()
					+ ", defaulting to " + defaultColorName);
			color = NamedTextColor.NAMES.value(defaultColorName.toLowerCase());
		}
		return color;
	}

	/**
	 * Same idea as {@link #parseColor}, but for a plain RGB {@link org.bukkit.Color} — what
	 * {@link org.bukkit.Particle.DustOptions} needs, rather than Adventure's {@link TextColor}. Only
	 * hex strings are accepted (no named-color fallback list, unlike {@code parseColor}) since dust
	 * particle color has no vanilla "legacy 16 colors" concept to match.
	 */
	private org.bukkit.Color parseBukkitColor(String path, String defaultHex) {
		String raw = config.getString(path, defaultHex);
		TextColor color = TextColor.fromHexString(raw);
		if (color == null) {
			jjk.getLogger().warning("Invalid color '" + raw + "' at '" + path + "', defaulting to " + defaultHex);
			color = TextColor.fromHexString(defaultHex);
		}
		return org.bukkit.Color.fromRGB(color.red(), color.green(), color.blue());
	}

	/**
	 * Summons {@code type} for {@code owner}, or does nothing and returns null if {@code owner}
	 * currently can't summon this type (see {@link #hasSummonable}). Callers must null-check the
	 * result before doing anything further with it (e.g. giving a control item).
	 */
	public Mob summon(Player owner, Location location, ShikigamiType type) {
		if (!hasSummonable(owner, type)) {
			return null;
		}

		// Configure the mob inside the spawn callback (before it's added to the world and
		// broadcast to nearby players) rather than after spawnEntity() returns — attributes
		// registered post-spawn aren't included in the initial spawn packet, so players already
		// nearby would see the default scale until their client re-syncs the entity (e.g. rejoin).
		@SuppressWarnings("unchecked")
		Class<? extends Mob> entityClass = (Class<? extends Mob>) type.getEntityType().getEntityClass();

		// Resume from whatever health this owner's shikigami of this type had when it was last
		// despawned, plus however much it regenerated in the meantime — rather than always
		// spawning at full health. Saved on the owner's own PersistentDataContainer (not an
		// in-memory map) so it survives a server restart.
		Double savedHealthValue = owner.getPersistentDataContainer().get(savedHealthKey(type), PersistentDataType.DOUBLE);
		double finalSpawnHealth;
		if (savedHealthValue == null) {
			finalSpawnHealth = type.getHealth();
		} else {
			Long savedAtMillis = owner.getPersistentDataContainer().get(savedHealthTimeKey(type), PersistentDataType.LONG);
			finalSpawnHealth = regeneratedHealth(savedHealthValue, savedAtMillis == null ? System.currentTimeMillis() : savedAtMillis, type);
			owner.getPersistentDataContainer().remove(savedHealthKey(type));
			owner.getPersistentDataContainer().remove(savedHealthTimeKey(type));
		}

		// Whether THIS owner has tamed this type before — per-player, not a type-level switch (see
		// hasTamed). A player who hasn't earned it yet gets an untamed, uncontrolled spawn.
		boolean tamed = hasTamed(owner, type);

		// Tame the mob to its owner instead of layering custom AI on top — a tamed Wolf already
		// has vanilla goals for following its owner and (once given a target) attacking, so we
		// just drive those directly rather than replacing them with hand-rolled Goal logic.
		Mob shikigami = location.getWorld().spawn(location, entityClass, mob -> {
			mob.registerAttribute(Attribute.SCALE);
			mob.getAttribute(Attribute.SCALE).setBaseValue(type.getScale());

			mob.registerAttribute(Attribute.MOVEMENT_SPEED);
			mob.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(type.getSpeed());

			mob.registerAttribute(Attribute.ATTACK_DAMAGE);
			mob.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(type.getDamage());

			mob.registerAttribute(Attribute.FOLLOW_RANGE);
			mob.getAttribute(Attribute.FOLLOW_RANGE).setBaseValue(type.getFollowRange());

			mob.customName(MiniMessage.miniMessage().deserialize(type.getDisplayName()));
			mob.setCustomNameVisible(true);

			mob.setPersistent(true);
			mob.setRemoveWhenFarAway(false);
			mob.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
			mob.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.name());

			if (tamed && mob instanceof Tameable tameable) {
				tameable.setTamed(true);
				tameable.setOwner(owner);
			}

			// Set health AFTER taming — vanilla Wolf taming resets Attribute.MAX_HEALTH back to its
			// own tamed default (40), clobbering any customization made earlier in this callback.
			mob.registerAttribute(Attribute.MAX_HEALTH);
			mob.getAttribute(Attribute.MAX_HEALTH).setBaseValue(type.getHealth());
			mob.setHealth(finalSpawnHealth);

			if (tamed) {
				// Strip vanilla's own target-acquisition goals (e.g. wolves instinctively hunting
				// skeletons) — targeting is driven entirely by commandAttack()/setTarget() instead,
				// so none of the built-in "pick a target on my own" behavior should remain active.
				Bukkit.getMobGoals().removeAllGoals(mob, GoalType.TARGET);
			}

			// Archetype-specific setup (e.g. FlightBehavior stripping MOVE goals) — applies
			// regardless of tame state, since e.g. FlightBehavior's tick() itself doesn't care
			// whether the shikigami is tamed (it hovers around/recalls to its owner either way, only
			// commandAttack()-driven attacking is actually player-gated via the control item).
			behaviorFor(type).onSummon(mob, type, tamed);
		});

		activeShikigami.put(shikigami.getUniqueId(), shikigami);

		if (highlightEnabled) {
			shikigamiOwners.put(shikigami.getUniqueId(), new ShikigamiOwner(owner.getUniqueId(), shikigami.getEntityId()));
			sendGlowUpdate(owner, shikigami, true);
			sendHighlightTeam(owner, shikigami.getUniqueId(), shikigami, type.getHighlightColor());
		}

		return shikigami;
	}

	/**
	 * Builds the PAPER control item for a summoned shikigami, tagged with its type and UUID so
	 * {@code ShikigamiListener} can read commands back off it and route them to this manager. Lore
	 * is built internally (see {@link #buildControlItemLore}) rather than passed in, since it needs
	 * to be re-rendered later as the track-ability cooldown changes. Display name comes from
	 * {@link ShikigamiType#getDisplayName()} (configured as {@code DisplayName}), same as everything
	 * else about the item.
	 */
	public void giveShikigamiItem(Player player, ShikigamiType type, int number, UUID shikigamiId) {
		ItemStack item = new ItemStack(Material.PAPER);
		ItemMeta meta = item.getItemMeta();
		PersistentDataContainer container = meta.getPersistentDataContainer();

		meta.displayName(MiniMessage.miniMessage().deserialize(type.getDisplayName()));
		meta.lore(buildControlItemLore(type, shikigamiId));

		NamespacedKey typeTagKey = new NamespacedKey(jjk, type.name() + number);
		container.set(typeTagKey, PersistentDataType.BOOLEAN, true);

		container.set(entityIdKey, PersistentDataType.STRING, shikigamiId.toString());

		item.setItemMeta(meta);

		player.getInventory().addItem(item);
	}

	// Indices (within ControlItemLore.lines()) of the ability lines that get a strikethrough prefix
	// applied whenever that specific ability is on cooldown for that specific shikigami. A type whose
	// archetype doesn't support a given ability just never has that ability's cooldown query return
	// true (see ShikigamiBehavior's default no-op/false implementations), so this stays harmless for
	// types with fewer lines or without that ability at all. Index 4 is shared by two different
	// abilities depending on archetype — Divine Dogs' Line5 is charge (a GroundMeleeBehavior ability),
	// Nue's Line5 is shield (a FlightBehavior ability) — never both at once for the same type, so
	// checking both cooldown queries and taking whichever is actually true is safe.
	private static final int TRACK_LORE_LINE_INDEX = 3;
	private static final int SECONDARY_OR_SHIELD_LORE_LINE_INDEX = 4;
	private static final int CLAP_LORE_LINE_INDEX = 5;

	/**
	 * The control item's lore, configured per type (see {@link ShikigamiType#getControlItemLore()})
	 * as a flat list of lines (Lore.Line1, Lore.Line2, ...), with each ability's line struck through
	 * whenever that ability is currently on cooldown for this specific shikigami (see
	 * {@link #isTrackOnCooldown}, {@link ShikigamiBehavior#isSecondaryCommandOnCooldown},
	 * {@link ShikigamiBehavior#isShieldOnCooldown}, {@link ShikigamiBehavior#isClapOnCooldown}).
	 */
	private List<Component> buildControlItemLore(ShikigamiType type, UUID shikigamiId) {
		List<String> lines = type.getControlItemLore().lines();
		ShikigamiBehavior behavior = behaviorFor(type);

		String trackPrefix = isTrackOnCooldown(shikigamiId) ? "<strikethrough>" : "";
		boolean secondaryOrShieldOnCooldown = behavior.isSecondaryCommandOnCooldown(shikigamiId)
				|| behavior.isShieldOnCooldown(shikigamiId);
		String secondaryOrShieldPrefix = secondaryOrShieldOnCooldown ? "<strikethrough>" : "";
		String clapPrefix = behavior.isClapOnCooldown(shikigamiId) ? "<strikethrough>" : "";

		List<Component> components = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			String prefix = switch (i) {
				case TRACK_LORE_LINE_INDEX -> trackPrefix;
				case SECONDARY_OR_SHIELD_LORE_LINE_INDEX -> secondaryOrShieldPrefix;
				case CLAP_LORE_LINE_INDEX -> clapPrefix;
				default -> "";
			};
			components.add(MiniMessage.miniMessage().deserialize(prefix + lines.get(i)));
		}
		return components;
	}

	/**
	 * Whether the track ability for this specific shikigami is currently on cooldown.
	 */
	public boolean isTrackOnCooldown(UUID shikigamiId) {
		Long expiry = trackCooldownExpiry.get(shikigamiId);
		return expiry != null && expiry > System.currentTimeMillis();
	}

	/**
	 * Re-renders every summoned shikigami's control item lore, so the track line's strikethrough
	 * reflects the current cooldown state instead of only whatever it looked like when the item was
	 * first given out.
	 */
	private void refreshControlItemLore() {
		for (Mob shikigami : activeShikigami.values()) {
			String ownerRaw = shikigami.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
			if (ownerRaw == null) {
				continue;
			}

			Player owner = Bukkit.getPlayer(UUID.fromString(ownerRaw));
			if (owner == null) {
				continue;
			}

			ShikigamiType type = getShikigamiType(shikigami);
			if (type == null) {
				continue;
			}

			updateControlItemLore(owner, type, shikigami.getUniqueId());
		}
	}

	private void updateControlItemLore(Player owner, ShikigamiType type, UUID shikigamiId) {
		Inventory inventory = owner.getInventory();
		for (int slot = 0; slot < inventory.getSize(); slot++) {
			ItemStack item = inventory.getItem(slot);
			if (item == null || !item.hasItemMeta()) {
				continue;
			}

			ItemMeta meta = item.getItemMeta();
			String idRaw = meta.getPersistentDataContainer().get(entityIdKey, PersistentDataType.STRING);
			if (!shikigamiId.toString().equals(idRaw)) {
				continue;
			}

			meta.lore(buildControlItemLore(type, shikigamiId));
			item.setItemMeta(meta);
			return;
		}
	}

	/**
	 * Removes the control item tagged with {@code shikigamiId} from {@code owner}'s inventory, so a
	 * despawn — whatever triggered it — doesn't leave behind an item pointing at a shikigami that no
	 * longer exists.
	 */
	private void removeShikigamiItem(Player owner, UUID shikigamiId) {
		Inventory inventory = owner.getInventory();
		for (int slot = 0; slot < inventory.getSize(); slot++) {
			ItemStack item = inventory.getItem(slot);
			if (item == null || !item.hasItemMeta()) {
				continue;
			}

			String idRaw = item.getItemMeta().getPersistentDataContainer().get(entityIdKey, PersistentDataType.STRING);
			if (shikigamiId.toString().equals(idRaw)) {
				inventory.setItem(slot, null);
				return;
			}
		}
	}

	/**
	 * Caches {@code player}'s latest raw movement input, reported by Paper's
	 * {@code PlayerInputEvent} regardless of whether they're currently able to act on it via normal
	 * movement (e.g. while riding a non-steerable passenger vehicle, which ignores WASD/jump
	 * entirely). Only consulted while that player is being carried — see
	 * {@link FlightBehavior#tick}.
	 */
	public void cacheInput(UUID playerId, Input input) {
		latestInput.put(playerId, input);
	}

	/**
	 * {@code null} if no input has been cached for this player yet.
	 */
	public Input getInput(UUID playerId) {
		return latestInput.get(playerId);
	}

	/**
	 * Drops a player's cached input on quit, so it doesn't sit in {@link #latestInput} forever.
	 */
	public void clearInput(UUID playerId) {
		latestInput.remove(playerId);
	}

	/**
	 * Whether {@code entity} is currently flagged as part of an active carry ride — either the
	 * shikigami itself (checked by {@link ShikigamiBehavior#tick} to branch into carry movement, and
	 * by {@link #despawn} indirectly via the behavior's own {@code onDespawn}) or, for archetypes that
	 * ride via a stand-in "seat" entity rather than the shikigami directly, that seat (checked by
	 * {@code ShikigamiListener#onDismount} to block vanilla's sneak-to-dismount for whichever entity
	 * the owner actually mounted).
	 */
	public boolean isCarrying(Entity entity) {
		return Boolean.TRUE.equals(
				entity.getPersistentDataContainer().get(carryingKey, PersistentDataType.BOOLEAN));
	}

	public void markCarrying(Entity entity) {
		entity.getPersistentDataContainer().set(carryingKey, PersistentDataType.BOOLEAN, true);
	}

	/**
	 * Cleared before whatever removes {@code entity} as a rider's vehicle, so the
	 * {@code EntityDismountEvent} that produces is let through by {@code ShikigamiListener#onDismount}
	 * instead of being (correctly, for vanilla's own sneak-to-dismount) blocked.
	 */
	public void clearCarrying(Entity entity) {
		entity.getPersistentDataContainer().remove(carryingKey);
	}

	/**
	 * Starts or ends a carry ride. A no-op for any type whose archetype doesn't support carrying (see
	 * {@link ShikigamiBehavior#supportsCarry}) — same "silently does nothing" contract as
	 * {@link #charge} and {@link #track} for unsupported types — and for anyone but the shikigami's
	 * own owner. The actual riding mechanics (what the owner mounts, and how it's driven) are entirely
	 * up to the archetype; see {@link ShikigamiBehavior#onCarryToggle}.
	 */
	public void toggleCarry(UUID shikigamiId, Player requester) {
		Mob shikigami = activeShikigami.get(shikigamiId);
		if (shikigami == null) {
			return;
		}

		ShikigamiType type = getShikigamiType(shikigami);
		if (type == null || !behaviorFor(type).supportsCarry()) {
			return;
		}

		Player owner = getOwner(shikigami);
		if (owner == null || !owner.getUniqueId().equals(requester.getUniqueId())) {
			return;
		}

		// Not carrying yet could mean "idle" or "already mid-approach" — the behavior itself decides
		// what a start request means from there (see FlightBehavior#onCarryToggle); this only knows
		// the definitive "actively carrying" state, which behaviorFor(type).onCarryToggle is
		// responsible for setting (via markCarrying/clearCarrying) once a ride actually begins/ends.
		behaviorFor(type).onCarryToggle(shikigami, type, owner, !isCarrying(shikigami));
	}

	/**
	 * Whether {@code entity} — the shikigami itself — is currently engaged in an active shield (see
	 * {@link #startShieldApproach}). Mirrors {@link #isCarrying}; the archetype (see
	 * {@link ShikigamiBehavior#onShieldToggle}) owns when this actually flips, since "engaged" is
	 * distinct from "approaching" (the latter is archetype-internal state this manager doesn't see).
	 */
	public boolean isShielding(Entity entity) {
		return Boolean.TRUE.equals(
				entity.getPersistentDataContainer().get(shieldingKey, PersistentDataType.BOOLEAN));
	}

	public void markShielding(Entity entity) {
		entity.getPersistentDataContainer().set(shieldingKey, PersistentDataType.BOOLEAN, true);
	}

	public void clearShielding(Entity entity) {
		entity.getPersistentDataContainer().remove(shieldingKey);
	}

	/**
	 * Records that {@code shikigamiId} is now (or is no longer, if {@code shikigamiId} is null)
	 * shielding {@code ownerId} — the reverse index {@link #absorbShieldDamage} uses to route a
	 * player-keyed damage event back to the right shikigami without the listener needing to know
	 * which one it is.
	 */
	public void registerShieldOwner(UUID ownerId, UUID shikigamiId) {
		shieldingOwners.put(ownerId, shikigamiId);
	}

	public void unregisterShieldOwner(UUID ownerId) {
		shieldingOwners.remove(ownerId);
	}

	/**
	 * Routes an incoming damage amount against {@code owner} through whatever's currently shielding
	 * them (if anything), returning how much was actually absorbed — 0 if they have no active shield.
	 * Called from {@code ShikigamiListener#onDamage} for every damage event against a player.
	 */
	public double absorbShieldDamage(Player owner, double incomingDamage) {
		UUID shikigamiId = shieldingOwners.get(owner.getUniqueId());
		if (shikigamiId == null) {
			return 0;
		}

		Mob shikigami = activeShikigami.get(shikigamiId);
		if (shikigami == null) {
			return 0;
		}

		ShikigamiType type = getShikigamiType(shikigami);
		if (type == null) {
			return 0;
		}

		return behaviorFor(type).absorbShieldDamage(shikigami, incomingDamage);
	}

	/**
	 * Starts a shield attempt: {@code shikigamiId} flies to {@code requester} and, once it arrives,
	 * both become immobile and a configurable pool of the owner's incoming damage is absorbed. A
	 * no-op for any type whose archetype doesn't support it (see {@link ShikigamiBehavior#supportsShield}),
	 * same contract as {@link #toggleCarry}, and for anyone but the shikigami's own owner. What "start"
	 * actually means from here (begin approaching, or do nothing if already busy with something else)
	 * is entirely up to the archetype; see {@link ShikigamiBehavior#onShieldToggle}.
	 */
	public void startShieldApproach(UUID shikigamiId, Player requester) {
		Mob shikigami = activeShikigami.get(shikigamiId);
		if (shikigami == null) {
			return;
		}

		ShikigamiType type = getShikigamiType(shikigami);
		if (type == null || !behaviorFor(type).supportsShield()) {
			return;
		}

		Player owner = getOwner(shikigami);
		if (owner == null || !owner.getUniqueId().equals(requester.getUniqueId())) {
			return;
		}

		behaviorFor(type).onShieldToggle(shikigami, type, owner, true);
	}

	/**
	 * Ends whatever shield state {@code shikigamiId} is currently in (a pending approach or an
	 * already-engaged shield) — a safe no-op if neither. Unlike {@link #startShieldApproach}, this
	 * isn't gated on {@code supportsShield()} — ending something should never itself be blocked.
	 */
	public void endShield(UUID shikigamiId, Player requester) {
		Mob shikigami = activeShikigami.get(shikigamiId);
		if (shikigami == null) {
			return;
		}

		ShikigamiType type = getShikigamiType(shikigami);
		if (type == null) {
			return;
		}

		Player owner = getOwner(shikigami);
		if (owner == null || !owner.getUniqueId().equals(requester.getUniqueId())) {
			return;
		}

		behaviorFor(type).onShieldToggle(shikigami, type, owner, false);
	}

	/**
	 * Fires the "clap" ability (a one-shot effect centered on the shikigami's own current location,
	 * e.g. Nue's Electric Wing Clap) — a no-op for any type whose archetype doesn't support it (see
	 * {@link ShikigamiBehavior#supportsClap}), same contract as {@link #toggleCarry}, and for anyone
	 * but the shikigami's own owner. What it actually does (damage, visuals, cooldown) is entirely up
	 * to the archetype; see {@link ShikigamiBehavior#onClap}.
	 */
	public void activateClap(UUID shikigamiId, Player requester) {
		Mob shikigami = activeShikigami.get(shikigamiId);
		if (shikigami == null) {
			return;
		}

		ShikigamiType type = getShikigamiType(shikigami);
		if (type == null || !behaviorFor(type).supportsClap()) {
			return;
		}

		Player owner = getOwner(shikigami);
		if (owner == null || !owner.getUniqueId().equals(requester.getUniqueId())) {
			return;
		}

		behaviorFor(type).onClap(shikigami, type, owner);
	}

	/**
	 * Whether {@code player}'s current main-hand item is the control item for {@code shikigamiId} —
	 * exposed so {@code FlightBehavior}'s per-tick shield rechecks (mirroring {@code Barrage}'s own
	 * per-tick "stillChanneling" recheck) don't need their own copy of
	 * {@code ShikigamiListener#readShikigamiId}.
	 */
	public boolean isHoldingControlItemFor(Player player, UUID shikigamiId) {
		ItemStack item = player.getInventory().getItemInMainHand();
		if (!item.hasItemMeta()) {
			return false;
		}

		String idRaw = item.getItemMeta().getPersistentDataContainer().get(entityIdKey, PersistentDataType.STRING);
		return shikigamiId.toString().equals(idRaw);
	}

	/**
	 * Marks {@code playerId} so their very next fall-damage instance is cancelled, then immediately
	 * consumed — not a timed effect, since there's no way to know in advance how long they'll be
	 * falling for. Used when a carry ride ends (by choice or by the shikigami being force-despawned
	 * out from under them mid-air), so that landing doesn't cost them fall damage.
	 */
	public void markFallDamageImmune(UUID playerId) {
		fallDamageImmune.add(playerId);
	}

	/**
	 * Returns whether {@code playerId} currently has fall-damage immunity pending, consuming it (it
	 * only ever protects the one fall) if so.
	 */
	public boolean consumeFallDamageImmunity(UUID playerId) {
		return fallDamageImmune.remove(playerId);
	}

	/**
	 * Commands the shikigami to attack {@code target} via vanilla combat AI — it already has a
	 * melee-attack goal that activates whenever it has a target set.
	 */
	public void commandAttack(UUID shikigamiId, LivingEntity target) {
		Mob shikigami = activeShikigami.get(shikigamiId);
		if (shikigami != null) {
			shikigami.setTarget(target);
		}
	}

	/**
	 * The "secondary" control-item trigger (drop, today) — e.g. Divine Dogs' charge rush. What it
	 * actually does (if anything) is entirely up to the shikigami's archetype; see
	 * {@link ShikigamiBehavior#onSecondaryCommand}.
	 */
	public void charge(UUID shikigamiId, LivingEntity target) {
		Mob shikigami = activeShikigami.get(shikigamiId);
		if (shikigami == null) {
			return;
		}

		ShikigamiType type = getShikigamiType(shikigami);
		if (type == null) {
			return;
		}

		behaviorFor(type).onSecondaryCommand(shikigami, type, target);
	}

	/**
	 * Clears any commanded attack target, then hands off to the shikigami's own archetype (see
	 * {@link ShikigamiBehavior#onRecall}) for anything beyond that — e.g. a ground type issuing a
	 * one-shot Pathfinder command back to the owner. A flying type can leave this at the default
	 * no-op since its own tick() already keeps it near the owner whenever it has no commanded target.
	 */
	public void recall(UUID shikigamiId) {
		Mob shikigami = activeShikigami.get(shikigamiId);
		if (shikigami == null) {
			return;
		}

		shikigami.setTarget(null);

		ShikigamiType type = getShikigamiType(shikigami);
		if (type == null) {
			return;
		}

		Player owner = getOwner(shikigami);
		if (owner != null) {
			behaviorFor(type).onRecall(shikigami, type, owner);
		}
	}

	/**
	 * "Track" ability: makes {@code target} glow, visible only to the shikigami's owner.
	 * Each shikigami tracks its own target independently — a player with two shikigami can
	 * have each one tracking a different entity at the same time.
	 */
	public void track(UUID shikigamiId, LivingEntity target) {
		Mob shikigami = activeShikigami.get(shikigamiId);
		if (shikigami == null) {
			return;
		}

		ShikigamiType type = getShikigamiType(shikigami);
		if (type == null || !behaviorFor(type).supportsTracking()) {
			return;
		}

		String ownerRaw = shikigami.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
		if (ownerRaw == null) {
			return;
		}

		Player owner = Bukkit.getPlayer(UUID.fromString(ownerRaw));
		if (owner == null) {
			return;
		}

		Long cooldownExpiry = trackCooldownExpiry.get(shikigamiId);
		if (cooldownExpiry != null && cooldownExpiry > System.currentTimeMillis()) {
			return;
		}

		if (jjk.getCursedEnergyManager().getCursedEnergy(owner) < type.getTrackCursedEnergyCost()) {
			return;
		}

		jjk.getCursedEnergyManager().reduceCursedEnergy(owner, type.getTrackCursedEnergyCost());
		trackCooldownExpiry.put(shikigamiId, System.currentTimeMillis() + type.getTrackCooldownMillis());

		UUID ownerId = owner.getUniqueId();
		TrackedTarget previousTracked =
				trackedTargets.put(shikigamiId, new TrackedTarget(ownerId, target.getUniqueId(), target.getEntityId()));
		if (previousTracked != null && !previousTracked.entityUuid().equals(target.getUniqueId())) {
			unglowIfNoLongerTracked(previousTracked, shikigamiId);
		}

		sendGlowUpdate(owner, target, true);
		sendTrackTeam(owner, shikigamiId, target, type.getTrackEntityColor());
		jjk.getLocatorBarManager().track(owner, target, new Color(type.getTrackLocatorColor().value()));
	}

	public void clearTrack(UUID shikigamiId) {
		TrackedTarget removed = trackedTargets.remove(shikigamiId);
		if (removed == null) {
			return;
		}

		unglowIfNoLongerTracked(removed, shikigamiId);

		Player owner = Bukkit.getPlayer(removed.ownerId());
		if (owner != null) {
			removeTrackTeam(owner, shikigamiId);
		}
	}

	/**
	 * Turns off {@code removed}'s glow for its owner, unless another one of that owner's
	 * shikigami (other than {@code excludingShikigamiId}) still has the same entity tracked.
	 */
	private void unglowIfNoLongerTracked(TrackedTarget removed, UUID excludingShikigamiId) {
		boolean stillTrackedElsewhere = trackedTargets.entrySet().stream()
				.anyMatch(entry -> !entry.getKey().equals(excludingShikigamiId)
						&& entry.getValue().ownerId().equals(removed.ownerId())
						&& entry.getValue().entityUuid().equals(removed.entityUuid()));
		if (stillTrackedElsewhere) {
			return;
		}

		Player owner = Bukkit.getPlayer(removed.ownerId());
		Entity entity = Bukkit.getEntity(removed.entityUuid());
		if (owner != null && entity instanceof LivingEntity living) {
			sendGlowUpdate(owner, living, false);
			jjk.getLocatorBarManager().untrack(owner, living);
		}
	}

	private void sendGlowUpdate(Player viewer, LivingEntity entity, boolean glowing) {
		byte flags = 0;
		if (entity.getFireTicks() > 0) {
			flags |= 0x01;
		}
		if (entity instanceof Player player && player.isSprinting()) {
			flags |= 0x08;
		}
		if (entity.isSwimming()) {
			flags |= 0x10;
		}
		if (entity.isInvisible()) {
			flags |= 0x20;
		}
		if (glowing) {
			flags |= GLOWING_BIT;
		}
		if (entity.isGliding()) {
			flags |= (byte) 0x80;
		}

		List<EntityData<?>> metadata = List.of(new EntityData<>(ENTITY_FLAGS_INDEX, EntityDataTypes.BYTE, flags));
		WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(entity.getEntityId(), metadata);
		PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
	}

	/**
	 * Assigns {@code target} to a fake scoreboard team (visible only to {@code viewer}) with the
	 * given color, which is what actually determines the glow outline's color client-side. Each
	 * shikigami gets its own dedicated team name, so re-tracking a new target just replaces this
	 * shikigami's own team membership without touching any sibling shikigami's team/color.
	 *
	 * <p>Vanilla team colors can only ever be one of the 16 legacy named colors — unlike text
	 * components, they never got full hex/RGB support. If {@code color} is a hex color (from
	 * config), this approximates it to the nearest legal named color via
	 * {@link NamedTextColor#nearestTo}; a configured named color passes through unchanged.
	 */
	private void sendTrackTeam(Player viewer, UUID shikigamiId, LivingEntity target, TextColor color) {
		NamedTextColor teamColor = NamedTextColor.nearestTo(color);
		WrapperPlayServerTeams.ScoreBoardTeamInfo teamInfo = new WrapperPlayServerTeams.ScoreBoardTeamInfo(
				Component.empty(), Component.empty(), Component.empty(),
				WrapperPlayServerTeams.NameTagVisibility.ALWAYS,
				WrapperPlayServerTeams.CollisionRule.ALWAYS,
				teamColor, WrapperPlayServerTeams.OptionData.NONE);

		WrapperPlayServerTeams packet = new WrapperPlayServerTeams(
				trackTeamName(shikigamiId), WrapperPlayServerTeams.TeamMode.CREATE, teamInfo,
				target.getUniqueId().toString());
		PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
	}

	private void removeTrackTeam(Player viewer, UUID shikigamiId) {
		WrapperPlayServerTeams packet = new WrapperPlayServerTeams(
				trackTeamName(shikigamiId), WrapperPlayServerTeams.TeamMode.REMOVE,
				(WrapperPlayServerTeams.ScoreBoardTeamInfo) null);
		PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
	}

	private String trackTeamName(UUID shikigamiId) {
		return "jjk_track_" + shikigamiId;
	}

	/**
	 * Assigns the shikigami itself to a fake team (visible only to {@code viewer}, its owner) so
	 * it stands out with a colored glow for as long as it's summoned. Same nearest-legal-color
	 * approximation caveat as {@link #sendTrackTeam} applies here.
	 */
	private void sendHighlightTeam(Player viewer, UUID shikigamiId, LivingEntity shikigami, TextColor color) {
		NamedTextColor teamColor = NamedTextColor.nearestTo(color);
		WrapperPlayServerTeams.ScoreBoardTeamInfo teamInfo = new WrapperPlayServerTeams.ScoreBoardTeamInfo(
				Component.empty(), Component.empty(), Component.empty(),
				WrapperPlayServerTeams.NameTagVisibility.ALWAYS,
				WrapperPlayServerTeams.CollisionRule.ALWAYS,
				teamColor, WrapperPlayServerTeams.OptionData.NONE);

		WrapperPlayServerTeams packet = new WrapperPlayServerTeams(
				highlightTeamName(shikigamiId), WrapperPlayServerTeams.TeamMode.CREATE, teamInfo,
				shikigami.getUniqueId().toString());
		PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
	}

	private void removeHighlightTeam(Player viewer, UUID shikigamiId) {
		WrapperPlayServerTeams packet = new WrapperPlayServerTeams(
				highlightTeamName(shikigamiId), WrapperPlayServerTeams.TeamMode.REMOVE,
				(WrapperPlayServerTeams.ScoreBoardTeamInfo) null);
		PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
	}

	private String highlightTeamName(UUID shikigamiId) {
		return "jjk_highlight_" + shikigamiId;
	}

	@Override
	public void onPacketSend(PacketSendEvent event) {
		if (event.getPacketType() != PacketType.Play.Server.ENTITY_METADATA) {
			return;
		}

		UUID viewerId = event.getUser().getUUID();
		boolean hasTrackedTargets = trackedTargets.values().stream().anyMatch(tracked -> tracked.ownerId().equals(viewerId));
		boolean hasOwnShikigami = shikigamiOwners.values().stream().anyMatch(info -> info.ownerId().equals(viewerId));
		if (!hasTrackedTargets && !hasOwnShikigami) {
			return;
		}

		WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
		int packetEntityId = packet.getEntityId();
		boolean isTracked = trackedTargets.values().stream()
				.anyMatch(tracked -> tracked.ownerId().equals(viewerId) && tracked.entityId() == packetEntityId);
		boolean isOwnShikigami = shikigamiOwners.values().stream()
				.anyMatch(info -> info.ownerId().equals(viewerId) && info.entityId() == packetEntityId);
		if (!isTracked && !isOwnShikigami) {
			return;
		}

		List<EntityData<?>> metadata = new ArrayList<>(packet.getEntityMetadata());
		boolean found = false;

		for (int i = 0; i < metadata.size(); i++) {
			EntityData<?> data = metadata.get(i);
			if (data.getIndex() == ENTITY_FLAGS_INDEX && data.getType() == EntityDataTypes.BYTE) {
				byte flags = (byte) ((Byte) data.getValue() | GLOWING_BIT);
				metadata.set(i, new EntityData<>(ENTITY_FLAGS_INDEX, EntityDataTypes.BYTE, flags));
				found = true;
				break;
			}
		}

		if (!found) {
			metadata.add(new EntityData<>(ENTITY_FLAGS_INDEX, EntityDataTypes.BYTE, GLOWING_BIT));
		}

		packet.setEntityMetadata(metadata);
		event.markForReEncode(true);
	}

	public void despawn(UUID shikigamiId) {
		Mob shikigami = activeShikigami.remove(shikigamiId);
		if (shikigami != null) {
			String ownerRaw = shikigami.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
			ShikigamiType type = getShikigamiType(shikigami);
			if (ownerRaw != null && type != null) {
				UUID ownerId = UUID.fromString(ownerRaw);

				// Written to the owner's own PDC (not an in-memory map) so it survives a server
				// restart — despawn() runs on every player's own quit event before shutdown
				// finishes, so the owner is still a valid, writable Player at this point.
				Player owner = Bukkit.getPlayer(ownerId);
				if (owner != null) {
					owner.getPersistentDataContainer().set(savedHealthKey(type), PersistentDataType.DOUBLE, shikigami.getHealth());
					owner.getPersistentDataContainer().set(savedHealthTimeKey(type), PersistentDataType.LONG, System.currentTimeMillis());
					removeShikigamiItem(owner, shikigamiId);
				}

				behaviorFor(type).onDespawn(shikigami, type);

				// Fired after the entity is already removed from activeShikigami, so listeners
				// checking hasActiveShikigami(...) see the post-despawn state.
				for (DespawnListener listener : despawnListeners) {
					listener.onDespawn(ownerId, type);
				}
			}

			shikigami.remove();
		}
		trackCooldownExpiry.remove(shikigamiId);
		clearTrack(shikigamiId);

		ShikigamiOwner highlight = shikigamiOwners.remove(shikigamiId);
		if (highlight != null) {
			Player owner = Bukkit.getPlayer(highlight.ownerId());
			if (owner != null) {
				removeHighlightTeam(owner, shikigamiId);
			}
		}
	}

	public void despawnAll(UUID ownerId) {
		List<UUID> owned = new ArrayList<>();

		for (Map.Entry<UUID, Mob> entry : activeShikigami.entrySet()) {
			String ownerRaw = entry.getValue().getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
			if (ownerId.toString().equals(ownerRaw)) {
				owned.add(entry.getKey());
			}
		}

		owned.forEach(this::despawn);
	}

	/**
	 * Despawns every currently summoned shikigami, regardless of owner — used on plugin disable so
	 * players don't end up with orphaned shikigami left behind in the world.
	 */
	public void despawnAll() {
		new ArrayList<>(activeShikigami.keySet()).forEach(this::despawn);
	}

	public Mob getShikigami(UUID shikigamiId) {
		return activeShikigami.get(shikigamiId);
	}

	/**
	 * Whether {@code ownerId} currently has at least one shikigami summoned in the world.
	 */
	public boolean hasActiveShikigami(UUID ownerId) {
		for (Mob shikigami : activeShikigami.values()) {
			String ownerRaw = shikigami.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
			if (ownerId.toString().equals(ownerRaw)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether {@code ownerId} currently has a shikigami of the specific given {@code type} summoned
	 * in the world.
	 */
	public boolean hasActiveShikigami(UUID ownerId, ShikigamiType type) {
		for (Mob shikigami : activeShikigami.values()) {
			String ownerRaw = shikigami.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
			if (ownerId.toString().equals(ownerRaw) && getShikigamiType(shikigami) == type) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Every shikigami {@code ownerId} currently has summoned in the world, regardless of type —
	 * for admin tooling (e.g. mass despawn/kill) that needs the live {@code Mob} instances rather
	 * than just the {@code ShikigamiInfo} summary {@link #getStoredShikigami} returns.
	 */
	public List<Mob> getActiveShikigami(UUID ownerId) {
		List<Mob> owned = new ArrayList<>();
		for (Mob shikigami : activeShikigami.values()) {
			String ownerRaw = shikigami.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
			if (ownerId.toString().equals(ownerRaw)) {
				owned.add(shikigami);
			}
		}
		return owned;
	}

	/**
	 * {@code ownerId}'s currently-summoned shikigami of the specific given {@code type}, or null if
	 * they don't have one out right now.
	 */
	public Mob getActiveShikigami(UUID ownerId, ShikigamiType type) {
		for (Mob shikigami : activeShikigami.values()) {
			String ownerRaw = shikigami.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
			if (ownerId.toString().equals(ownerRaw) && getShikigamiType(shikigami) == type) {
				return shikigami;
			}
		}
		return null;
	}

	public ShikigamiType getShikigamiType(Mob shikigami) {
		String typeRaw = shikigami.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
		return typeRaw == null ? null : ShikigamiType.valueOf(typeRaw);
	}

	/**
	 * The player who summoned {@code shikigami}, or null if they're offline (or the mob has no
	 * owner tag at all, which shouldn't happen for anything {@link #summon} produced).
	 */
	public Player getOwner(Mob shikigami) {
		String ownerRaw = shikigami.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
		return ownerRaw == null ? null : Bukkit.getPlayer(UUID.fromString(ownerRaw));
	}

	/**
	 * Every shikigami {@code ownerId} currently has — summoned or not. A shikigami is "had" from
	 * the moment it's first summoned onward: despawning (whether by player command or by dying)
	 * saves its health instead of discarding it, so it's still owned while despawned, just
	 * resting and regenerating. Used by the shikigami menu GUI, which shows this full roster
	 * rather than only what's currently standing in the world.
	 */
	public List<ShikigamiInfo> getStoredShikigami(Player owner) {
		List<ShikigamiInfo> stored = new ArrayList<>();
		String ownerId = owner.getUniqueId().toString();

		for (Mob shikigami : activeShikigami.values()) {
			String ownerRaw = shikigami.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
			if (ownerId.equals(ownerRaw)) {
				ShikigamiType type = getShikigamiType(shikigami);
				double maxHealth = shikigami.getAttribute(Attribute.MAX_HEALTH).getValue();
				stored.add(new ShikigamiInfo(type, shikigami.getHealth(), maxHealth, true));
			}
		}

		for (ShikigamiType type : ShikigamiType.values()) {
			Double savedHealthValue = owner.getPersistentDataContainer().get(savedHealthKey(type), PersistentDataType.DOUBLE);
			if (savedHealthValue == null) {
				continue;
			}

			Long savedAtMillis = owner.getPersistentDataContainer().get(savedHealthTimeKey(type), PersistentDataType.LONG);
			double health = regeneratedHealth(savedHealthValue, savedAtMillis == null ? System.currentTimeMillis() : savedAtMillis, type);
			stored.add(new ShikigamiInfo(type, health, type.getHealth(), false));
		}

		return stored;
	}

	/**
	 * A stored shikigami's stats as shown in the shikigami menu: its type, its health right now
	 * (regenerated up to the current moment if despawned), its max health, and whether it's
	 * currently summoned in the world.
	 */
	public record ShikigamiInfo(ShikigamiType type, double health, double maxHealth, boolean active) {
	}

	/**
	 * Applies elapsed-time regen to a saved despawn health, capped at max health.
	 */
	private double regeneratedHealth(double savedHealth, long despawnedAtMillis, ShikigamiType type) {
		double elapsedSeconds = (System.currentTimeMillis() - despawnedAtMillis) / 1000.0;
		double regenerated = elapsedSeconds * type.getDespawnedRegenPerSecond();
		return Math.min(type.getHealth(), savedHealth + regenerated);
	}

	@Override
	public void start() {
		drainTask = Bukkit.getScheduler().runTaskTimer(jjk, this::drainCursedEnergy,
				cursedEnergyDrainPeriodTicks, cursedEnergyDrainPeriodTicks);
		regenTask = Bukkit.getScheduler().runTaskTimer(jjk, this::regenerateActiveShikigami,
				regenPeriodTicks, regenPeriodTicks);
		itemLoreUpdateTask = Bukkit.getScheduler().runTaskTimer(jjk, this::refreshControlItemLore,
				itemLoreUpdatePeriodTicks, itemLoreUpdatePeriodTicks);
		aiTickTask = Bukkit.getScheduler().runTaskTimer(jjk, this::tickBehaviors,
				aiTickPeriodTicks, aiTickPeriodTicks);
		PacketEvents.getAPI().getEventManager().registerListener(this, PacketListenerPriority.NORMAL);
	}

	public void stop() {
		if (drainTask != null) {
			drainTask.cancel();
		}
		if (regenTask != null) {
			regenTask.cancel();
		}
		if (itemLoreUpdateTask != null) {
			itemLoreUpdateTask.cancel();
		}
		if (aiTickTask != null) {
			aiTickTask.cancel();
		}
	}

	private void drainCursedEnergy() {
		// Iterate a snapshot: reduceCursedEnergy() below can despawn this owner's shikigami mid-loop
		// (once their cursed energy hits 0), which would mutate activeShikigami while the live map's
		// own iterator is still in use.
		for (Mob shikigami : new ArrayList<>(activeShikigami.values())) {
			if (!activeShikigami.containsKey(shikigami.getUniqueId())) {
				continue; // already despawned earlier this tick
			}

			String ownerRaw = shikigami.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
			String typeRaw = shikigami.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
			if (ownerRaw == null || typeRaw == null) {
				continue;
			}

			Player owner = Bukkit.getPlayer(UUID.fromString(ownerRaw));
			if (owner != null) {
				ShikigamiType type = ShikigamiType.valueOf(typeRaw);
				jjk.getCursedEnergyManager().reduceCursedEnergy(owner, type.getCursedEnergyDrainAmount());
			}
		}
	}

	/**
	 * Regenerates every summoned, alive shikigami extremely slowly, up to its max health.
	 * Complements the elapsed-time-based regen applied in {@link #summon} for despawned shikigami.
	 */
	private void regenerateActiveShikigami() {
		for (Mob shikigami : activeShikigami.values()) {
			if (shikigami.isDead()) {
				continue;
			}

			String typeRaw = shikigami.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
			if (typeRaw == null) {
				continue;
			}

			ShikigamiType type = ShikigamiType.valueOf(typeRaw);
			double maxHealth = shikigami.getAttribute(Attribute.MAX_HEALTH).getValue();
			if (shikigami.getHealth() < maxHealth) {
				shikigami.setHealth(Math.min(maxHealth, shikigami.getHealth() + type.getRegenAmount()));
			}
		}
	}

	/**
	 * Ticks every active shikigami's own archetype behavior (see {@link ShikigamiBehavior#tick}) —
	 * e.g. {@code FlightBehavior}'s idle hover/attack movement. Ground types leave {@code tick()} at
	 * the default no-op (vanilla goals drive them instead), so this costs them nothing.
	 */
	private void tickBehaviors() {
		for (Mob shikigami : activeShikigami.values()) {
			ShikigamiType type = getShikigamiType(shikigami);
			if (type == null) {
				continue;
			}

			behaviorFor(type).tick(shikigami, type);
		}
	}
}
