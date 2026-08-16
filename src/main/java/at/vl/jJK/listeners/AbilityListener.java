package at.vl.jJK.listeners;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import at.vl.jJK.JJK;
import at.vl.jJK.cursedtechniques.technique.AbilityType;
import at.vl.jJK.cursedtechniques.Bindable;
import at.vl.jJK.cursedtechniques.BindableCategory;
import at.vl.jJK.cursedtechniques.technique.CursedTechnique;
import at.vl.jJK.cursedtechniques.technique.CursedTechniqueType;
import at.vl.jJK.cursedtechniques.melee.MeleeType;
import at.vl.jJK.cursedtechniques.specialmove.SpecialMoveType;
import at.vl.jJK.cursedtechniques.weaponeering.WeaponeeringType;

public class AbilityListener implements Listener {

	// Tune these to taste — not documented Minecraft values, just debounce windows.
	private static final long NON_ATTACK_SWING_IGNORE_WINDOW_MS = 150;
	private static final long CLICK_COOLDOWN_MS = 150;

	private final JJK jjk;
	// Last time the player dropped an item or right-clicked to use one — both also fire the same
	// ARM_SWING animation event a genuine left-click attack does, so onAttack needs to ignore any
	// swing that immediately follows one of these to avoid spuriously triggering an ability (e.g.
	// throwing an egg/snowball/potion swings the arm exactly like an attack would).
	private final Map<UUID, Long> lastNonAttackActionTime = new HashMap<>();
	private final Map<UUID, Long> lastActivationTime = new HashMap<>();

	public AbilityListener(JJK jjk) {
		this.jjk = jjk;
		Bukkit.getPluginManager().registerEvents(this, jjk);
	}

	@EventHandler
	public void onDrop(PlayerDropItemEvent event) {
		lastNonAttackActionTime.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
	}

	@EventHandler
	public void onInteract(PlayerInteractEvent event) {
		if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
			lastNonAttackActionTime.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
		}
	}

	@EventHandler
	public void onAttack(PlayerAnimationEvent event) {
		if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
			return;
		}

		Player player = event.getPlayer();
		UUID uuid = player.getUniqueId();
		long now = System.currentTimeMillis();

		// Ignore the swing Minecraft fires automatically for a drop or a right-click item use
		Long nonAttackTime = lastNonAttackActionTime.get(uuid);
		if (nonAttackTime != null && now - nonAttackTime < NON_ATTACK_SWING_IGNORE_WINDOW_MS) {
			return;
		}

		// Debounce so holding left click doesn't spam-trigger the ability
		Long lastActivation = lastActivationTime.get(uuid);
		if (lastActivation != null && now - lastActivation < CLICK_COOLDOWN_MS) {
			return;
		}
		lastActivationTime.put(uuid, now);

		handleLeftClick(player);
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		UUID uuid = event.getPlayer().getUniqueId();
		lastNonAttackActionTime.remove(uuid);
		lastActivationTime.remove(uuid);
	}

	private void handleLeftClick(Player player) {
		int slot = player.getInventory().getHeldItemSlot();

		Bindable bound = jjk.getBindManager().getBoundAbility(player, slot);
		if (bound == null) {
			return;
		}

		// Cursed techniques, melee, and special moves are all channeled bare-handed — weaponeering is
		// the one category actually built around holding a specific weapon item (see
		// WeaponeeringType's own Material field), so it's the only one exempt from this.
		if (bound.getCategory() != BindableCategory.WEAPONEERING
				&& player.getInventory().getItemInMainHand().getType() != Material.AIR) {
			return;
		}

		switch (bound.getCategory()) {
			case CURSED_TECHNIQUE -> {
				AbilityType type = (AbilityType) bound;
				CursedTechniqueType playerTechnique = jjk.getCursedTechniqueManager().getPlayerCursedTechnique(player);
				CursedTechnique technique = jjk.getCursedTechniqueManager().getTechnique(playerTechnique);
				if (technique != null) {
					technique.activateAbility(player, type);
				}
			}
			case MELEE -> jjk.getMeleeManager().activate(player, (MeleeType) bound);
			case WEAPONEERING -> jjk.getWeaponeeringManager().activate(player, (WeaponeeringType) bound);
			case SPECIAL_MOVE -> jjk.getSpecialMoveManager().activate(player, (SpecialMoveType) bound);
		}
	}
}