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
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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

		// RIGHT_CLICK_AIR: attack whatever the player is aiming at.
		LivingEntity target = resolveTargetedEntity(player);
		if (target != null) {
			jjk.getShikigamiManager().commandAttack(shikigamiId, target);
		}
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
	public void onQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();

		jjk.getShikigamiManager().despawnAll(player.getUniqueId());
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
