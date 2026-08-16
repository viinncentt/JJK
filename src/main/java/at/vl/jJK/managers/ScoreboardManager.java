package at.vl.jJK.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar;
import net.megavex.scoreboardlibrary.api.sidebar.component.ComponentSidebarLayout;
import net.megavex.scoreboardlibrary.api.sidebar.component.SidebarComponent;
import net.megavex.scoreboardlibrary.api.sidebar.component.animation.CollectionSidebarAnimation;
import net.megavex.scoreboardlibrary.api.sidebar.component.animation.SidebarAnimation;

import at.vl.jJK.JJK;
import at.vl.jJK.cursedtechniques.technique.AbilityType;
import at.vl.jJK.cursedtechniques.Bindable;
import at.vl.jJK.cursedtechniques.melee.MeleeType;
import at.vl.jJK.cursedtechniques.shikigami.ShikigamiType;
import at.vl.jJK.cursedtechniques.specialmove.SpecialMoveType;
import at.vl.jJK.cursedtechniques.weaponeering.WeaponeeringType;

public class ScoreboardManager implements JJKManager {
	private JJK jjk;

	private final Map<UUID, Sidebar> sidebars = new HashMap<>();
	private final Map<UUID, ComponentSidebarLayout> layouts = new HashMap<>();
	private final Map<UUID, SidebarAnimation<Component>> titleAnimations = new HashMap<>();

	public ScoreboardManager(JJK jjk) {
		this.jjk = jjk;

		for (Player p : Bukkit.getOnlinePlayers()) {
			createScoreboard(p);
		}

		Bukkit.getScheduler().runTaskTimer(jjk, () -> {
			for (UUID uuid : sidebars.keySet()) {
				SidebarAnimation<Component> anim = titleAnimations.get(uuid);
				ComponentSidebarLayout layout = layouts.get(uuid);
				Sidebar sidebar = sidebars.get(uuid);
				if (anim != null && layout != null && sidebar != null) {
					anim.nextFrame();
					layout.apply(sidebar);
				}
			}
		}, 0L, 1L);
	}

	public void createScoreboard(Player player) {
		UUID uuid = player.getUniqueId();

		Sidebar sidebar = jjk.getScoreboardLibrary().createSidebar();

		// Title
		SidebarAnimation<Component> titleAnimation =
				createGradientAnimation(MiniMessage.miniMessage().deserialize("<bold>JJW</bold>"));
		SidebarComponent title = SidebarComponent.animatedLine(titleAnimation);

		// Cursed Energy

		// Lines
		SidebarComponent lines = SidebarComponent.builder()
				.addBlankLine()
				// Cursed Energy
				.addDynamicLine(() -> {
					int cursedEnergy = (int) Math.round(jjk.getCursedEnergyManager().getCursedEnergy(player));
					int maxCursedEnergy = (int) Math.round(jjk.getCursedEnergyManager().getMaxCursedEnergy(player));
					return MiniMessage.miniMessage().deserialize("<gradient:#BF606C:#77C255>CE: </gradient>" + cursedEnergy + "/" + maxCursedEnergy);
				})
				.addBlankLine()

				// Slots
				.addDynamicLine(() -> getBindDisplay(player, 0))
				.addDynamicLine(() -> getBindDisplay(player, 1))
				.addDynamicLine(() -> getBindDisplay(player, 2))
				.addDynamicLine(() -> getBindDisplay(player, 3))
				.addDynamicLine(() -> getBindDisplay(player, 4))
				.addDynamicLine(() -> getBindDisplay(player, 5))
				.addDynamicLine(() -> getBindDisplay(player, 6))
				.addDynamicLine(() -> getBindDisplay(player, 7))
				.addDynamicLine(() -> getBindDisplay(player, 8))
				.addBlankLine()
				.build();

		ComponentSidebarLayout layout = new ComponentSidebarLayout(title, lines);
		layout.apply(sidebar);

		sidebar.addPlayer(player);

		sidebars.put(uuid, sidebar);
		layouts.put(uuid, layout);
		titleAnimations.put(uuid, titleAnimation);
	}

	public void removeScoreboard(Player player) {
		UUID uuid = player.getUniqueId();
		Sidebar sidebar = sidebars.remove(uuid);
		layouts.remove(uuid);
		titleAnimations.remove(uuid);
		if (sidebar != null) {
			sidebar.close();
		}
	}

	private SidebarAnimation<Component> createGradientAnimation(Component text) {
		float step = 1f / 32f;

		TagResolver.Single textPlaceholder = Placeholder.component("text", text);
		List<Component> frames = new ArrayList<>((int) (2f / step));

		float phase = -1f;
		while (phase < 1) {
			frames.add(MiniMessage.miniMessage().deserialize("<gradient:#6937CB:#1DCDEF:" + phase + "><text>", textPlaceholder));
			phase += step;
		}

		return new CollectionSidebarAnimation<>(frames);
	}

	@Override
	public void start() {

	}

	private Component getBindDisplay(Player player, int bindSlot) {
		Bindable bound = jjk.getBindManager().getBoundAbility(player, bindSlot);
		boolean selected = player.getInventory().getHeldItemSlot() == bindSlot;

		if (bound != null) {
			Component prefix = MiniMessage.miniMessage().deserialize(selected ? "> " : "<gray>> ");

			boolean onCooldown = switch (bound.getCategory()) {
				case CURSED_TECHNIQUE -> jjk.getAbilityManager().isOnCooldown(player, (AbilityType) bound);
				case MELEE -> jjk.getMeleeManager().isOnCooldown(player, (MeleeType) bound);
				case WEAPONEERING -> jjk.getWeaponeeringManager().isOnCooldown(player, (WeaponeeringType) bound);
				case SPECIAL_MOVE -> jjk.getSpecialMoveManager().isOnCooldown(player, (SpecialMoveType) bound);
			};

			// Divine Dogs can't be re-activated while both dogs are already out (see
			// TenShadows#activateDivineDogs), so show it as unusable the same way cooldown does.
			boolean bothDivineDogsOut = bound == AbilityType.DIVINE_DOGS
					&& jjk.getShikigamiManager().hasActiveShikigami(player.getUniqueId(), ShikigamiType.WHITE_DIVINE_DOG)
					&& jjk.getShikigamiManager().hasActiveShikigami(player.getUniqueId(), ShikigamiType.BLACK_DIVINE_DOG);

			// Same idea for Nue (see TenShadows#activateNue) — re-activating while it's already
			// summoned is a no-op, so show it as unusable for as long as it's out.
			boolean nueActive = bound == AbilityType.NUE
					&& jjk.getShikigamiManager().hasActiveShikigami(player.getUniqueId(), ShikigamiType.NUE);

			boolean unusable = onCooldown || bothDivineDogsOut || nueActive;

			Component name = unusable
					? MiniMessage.miniMessage().deserialize("<strikethrough>" + bound.getDisplayName())
					: MiniMessage.miniMessage().deserialize(bound.getDisplayName());

			return prefix.append(name);
		}

		if (selected) {
			return MiniMessage.miniMessage().deserialize("> " + "<gray>--Slot " + (bindSlot + 1) + "--");
		} else {
			return MiniMessage.miniMessage().deserialize("<gray>> " + "--Slot " + (bindSlot + 1) + "--");
		}
	}
}