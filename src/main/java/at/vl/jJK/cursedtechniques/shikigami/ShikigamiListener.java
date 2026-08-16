package at.vl.jJK.cursedtechniques.shikigami;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;

import at.vl.jJK.JJK;

public class ShikigamiListener implements Listener {
	private final JJK jjk;
	private final NamespacedKey entityIdKey;
	private double targetRange;
	private double targetRaySize;

	public ShikigamiListener(JJK jjk) {
		this.jjk = jjk;
		this.entityIdKey = new NamespacedKey(jjk, ShikigamiManager.ENTITY_ID_KEY);
		loadConfig();
		Bukkit.getPluginManager().registerEvents(this, jjk);
	}

	public void reload() {
		loadConfig();
	}

	private void loadConfig() {
		targetRange = jjk.getShikigamiManager().getConfig().getDouble("Order.TargetRange");
		targetRaySize = jjk.getShikigamiManager().getConfig().getDouble("Order.TargetRaySize");
	}

	@EventHandler
	public void onInteract(PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
			return;
		}

		Player player = event.getPlayer();

		UUID shikigamiId = readShikigamiId(event.getItem());
		if (shikigamiId == null) {
			return;
		}

		event.setCancelled(true);

		if (player.isSneaking()) {
			// despawn() removes the matching control item from the inventory itself, so no manual
			// amount decrement needed here.
			jjk.getShikigamiManager().despawn(shikigamiId);
			return;
		}

		if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
			jjk.getShikigamiManager().recall(shikigamiId);
			return;
		}

		// RIGHT_CLICK_AIR: attack whatever the player is aiming at, or — if aiming at nothing —
		// toggle riding it (a no-op for any archetype that doesn't support carrying).
		LivingEntity target = resolveTargetedEntity(player);
		if (target != null) {
			jjk.getShikigamiManager().commandAttack(shikigamiId, target);
		} else {
			jjk.getShikigamiManager().toggleCarry(shikigamiId, player);
		}
	}

	@EventHandler
	public void onInput(PlayerInputEvent event) {
		jjk.getShikigamiManager().cacheInput(event.getPlayer().getUniqueId(), event.getInput());
	}

	@EventHandler
	public void onDismount(EntityDismountEvent event) {
		// Vanilla dismounts any passenger the moment they press sneak — but sneak is also "fly down"
		// while carrying, so block that automatic dismount for as long as the ride is still flagged
		// active. Whatever ends a carry intentionally (ShikigamiManager#toggleCarry,
		// FlightBehavior#endCarry) clears the flag before its own removePassenger() call, so that
		// end-of-ride dismount still goes through.
		if (event.getEntity() instanceof Player && jjk.getShikigamiManager().isCarrying(event.getDismounted())) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onDamage(EntityDamageEvent event) {
		// Applies to every damage cause, not just FALL — an active shield absorbs any incoming
		// damage against its owner, up to its remaining pool.
		if (event.getEntity() instanceof Player player) {
			double absorbed = jjk.getShikigamiManager().absorbShieldDamage(player, event.getDamage());
			if (absorbed > 0) {
				event.setDamage(Math.max(0, event.getDamage() - absorbed));
			}
		}

		if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
			return;
		}

		// Shikigami are meant to fly, not walk — never take fall damage regardless of type or archetype.
		if (jjk.getShikigamiManager().getShikigami(event.getEntity().getUniqueId()) != null) {
			event.setCancelled(true);
			return;
		}

		// One-shot: a carry ride ending (by choice, or the shikigami being force-despawned out from
		// under the rider mid-air) marks exactly the next fall as immune, not a timed window.
		if (event.getEntity() instanceof Player player
				&& jjk.getShikigamiManager().consumeFallDamageImmunity(player.getUniqueId())) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onToggleSneak(PlayerToggleSneakEvent event) {
		Player player = event.getPlayer();
		UUID shikigamiId = readShikigamiId(player.getInventory().getItemInMainHand());
		if (shikigamiId == null) {
			return;
		}

		if (event.isSneaking()) {
			jjk.getShikigamiManager().startShieldApproach(shikigamiId, player);
		} else {
			// endShield() is a safe no-op if nothing was pending/active for this shikigami — sneaking
			// with the item held while NOT shielding (e.g. just walking around crouched) shouldn't do
			// anything, and this covers that for free.
			jjk.getShikigamiManager().endShield(shikigamiId, player);
		}
	}

	@EventHandler
	public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
		UUID shikigamiId = readShikigamiId(event.getMainHandItem());
		if (shikigamiId == null) {
			return;
		}

		event.setCancelled(true);
		// The client predicts the hand-swap visually before the server confirms it — cancelling alone
		// doesn't undo that prediction, so force a resync or the item appears swapped client-side even
		// though the server-side inventory never actually changed.
		event.getPlayer().updateInventory();
		jjk.getShikigamiManager().activateClap(shikigamiId, event.getPlayer());
	}

	@EventHandler
	public void onInteractEntity(PlayerInteractEntityEvent event) {
		if (event.getHand() != EquipmentSlot.HAND) {
			return;
		}

		UUID shikigamiId = readShikigamiId(event.getPlayer().getInventory().getItemInMainHand());
		if (shikigamiId == null) {
			return;
		}

		event.setCancelled(true);

		// Riding a shikigami puts the rider's own crosshair on its hitbox for virtually any right
		// click near it, so the client sends this (an entity-interact) instead of the plain
		// RIGHT_CLICK_AIR onInteract() handles — right-clicking the shikigami itself always means
		// "toggle the ride", the same as aiming at nothing does there, not "track it".
		if (event.getRightClicked().getUniqueId().equals(shikigamiId)) {
			jjk.getShikigamiManager().toggleCarry(shikigamiId, event.getPlayer());
			return;
		}

		if (event.getRightClicked() instanceof LivingEntity target) {
			jjk.getShikigamiManager().track(shikigamiId, target);
		}
	}

	@EventHandler
	public void onDrop(PlayerDropItemEvent event) {
		UUID shikigamiId = readShikigamiId(event.getItemDrop().getItemStack());
		if (shikigamiId == null) {
			return;
		}

		event.setCancelled(true);

		LivingEntity target = resolveTargetedEntity(event.getPlayer());
		if (target != null) {
			jjk.getShikigamiManager().charge(shikigamiId, target);
		}
	}

	@EventHandler
	public void onTeleport(EntityTeleportEvent event) {
		// Vanilla tamed pets teleport back to their owner when they fall too far behind — that's
		// core TamableAnimal tick logic, not a removable Goal, so cancelling this public event is
		// the only way to disable it without NMS.
		if (jjk.getShikigamiManager().getShikigami(event.getEntity().getUniqueId()) != null) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onDeath(EntityDeathEvent event) {
		UUID shikigamiId = event.getEntity().getUniqueId();
		Mob shikigami = jjk.getShikigamiManager().getShikigami(shikigamiId);
		if (shikigami == null) {
			return;
		}

		// If the owner is the one who landed the kill on their own untamed shikigami, that earns
		// them taming rights for the type going forward — see ShikigamiManager#handleKilled.
		Player killer = event.getEntity().getKiller();
		if (killer != null) {
			jjk.getShikigamiManager().handleKilled(shikigami, killer);
		}

		// Routes through despawn(), which saves the shikigami's health (0, since it died) the same
		// way an explicit despawn does — it's still "owned" while dead, just resting and
		// regenerating from empty. This is also the hook point for any future custom death effects.
		jjk.getShikigamiManager().despawn(shikigamiId);
	}

	@EventHandler
	public void onPlayerDeath(PlayerDeathEvent event) {
		Player player = event.getEntity();

		// Shikigami control items are bound to their owner, not lootable — strip them out of the
		// death-drop pile before despawning whatever they were controlling, the same way running out
		// of cursed energy does (CursedEnergyManager#reduceCursedEnergy -> ShikigamiManager#despawnAll).
		event.getDrops().removeIf(item -> readShikigamiId(item) != null);
		jjk.getShikigamiManager().despawnAll(player.getUniqueId());
	}

	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		// Shikigami control items are bound to their owner's own inventory — block moving one into any
		// other inventory that's currently open alongside it (a chest, another player's inventory,
		// etc.) via shift-click or a manual cursor placement. Dropping is already blocked in onDrop().
		if (!(event.getWhoClicked() instanceof Player)) {
			return;
		}

		InventoryType topType = event.getView().getTopInventory().getType();
		if (topType == InventoryType.PLAYER || topType == InventoryType.CRAFTING) {
			return;
		}

		ItemStack movingItem = event.getClick().isShiftClick() ? event.getCurrentItem() : event.getCursor();
		if (readShikigamiId(movingItem) != null) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();

		jjk.getShikigamiManager().despawnAll(player.getUniqueId());
		jjk.getShikigamiManager().clearInput(player.getUniqueId());
		jjk.getLocatorBarManager().untrackAll(player);

		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getSize(); slot++) {
			if (readShikigamiId(inventory.getItem(slot)) != null) {
				inventory.setItem(slot, null);
			}
		}
	}

	private UUID readShikigamiId(ItemStack item) {
		if (item == null || !item.hasItemMeta()) {
			return null;
		}

		ItemMeta meta = item.getItemMeta();
		PersistentDataContainer container = meta.getPersistentDataContainer();
		String shikigamiIdRaw = container.get(entityIdKey, PersistentDataType.STRING);

		return shikigamiIdRaw == null ? null : UUID.fromString(shikigamiIdRaw);
	}

	private LivingEntity resolveTargetedEntity(Player caster) {
		Location eye = caster.getEyeLocation();
		RayTraceResult result = caster.getWorld().rayTraceEntities(
				eye, eye.getDirection(), targetRange, targetRaySize,
				entity -> entity instanceof LivingEntity && !entity.equals(caster)
						&& jjk.getShikigamiManager().getShikigami(entity.getUniqueId()) == null);

		if (result == null) {
			return null;
		}

		return result.getHitEntity() instanceof LivingEntity target ? target : null;
	}
}
