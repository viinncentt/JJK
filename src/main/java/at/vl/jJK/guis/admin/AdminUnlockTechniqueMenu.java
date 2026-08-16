package at.vl.jJK.guis.admin;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.minimessage.MiniMessage;

import at.vl.jJK.JJK;
import at.vl.jJK.cursedtechniques.technique.AbilityType;
import at.vl.jJK.cursedtechniques.technique.CursedTechniqueType;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemBuilder;
import xyz.xenondevs.invui.window.Window;

public class AdminUnlockTechniqueMenu {
	private final JJK jjk;
	private final String title;

	public AdminUnlockTechniqueMenu(JJK jjk) {
		this.jjk = jjk;

		title = "<gradient:#4BAEB7:#700F52>Unlock: Choose Technique</gradient>";
	}

	public void open(Player admin, Player target) {
		Window window = Window.builder()
				.setTitle(MiniMessage.miniMessage().deserialize(title))
				.setUpperGui(createGui(admin, target))
				.setViewer(admin)
				.build();

		window.open();
	}

	private Gui createGui(Player admin, Player target) {
		Item tenShadowsItem = createTechniqueItem(admin, target, CursedTechniqueType.TEN_SHADOWS, Material.BLACK_DYE);
		Item projectionSorceryItem = createTechniqueItem(admin, target, CursedTechniqueType.PROJECTION_SORCERY, Material.GREEN_DYE);
		Item strawDollItem = createTechniqueItem(admin, target, CursedTechniqueType.STRAW_DOLL, Material.YELLOW_DYE);

		Item back = Item.builder()
				.setItemProvider(new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
						.setName(MiniMessage.miniMessage().deserialize("<red>Back")))
				.addClickHandler(click -> new AdminUnlockMenu(jjk).open(admin, target))
				.build();

		return Gui.builder()
				.setStructure(
						"a b c x x x x x x",
						"x x x x x x x x x",
						"x x x x x x x x x",
						"x x x x x x x x !"
				)
				.addIngredient('a', tenShadowsItem)
				.addIngredient('b', projectionSorceryItem)
				.addIngredient('c', strawDollItem)
				.addIngredient('!', back)
				.build();
	}

	private Item createTechniqueItem(Player admin, Player target, CursedTechniqueType technique, Material material) {
		return Item.builder()
				.setItemProvider(new ItemBuilder(material)
						.setName(MiniMessage.miniMessage().deserialize(technique.getDisplayName())))
				.addClickHandler(click -> {
					List<AbilityType> abilities = Arrays.stream(AbilityType.values())
							.filter(ability -> ability.getCursedTechniques() == technique)
							.toList();

					new AdminUnlockListMenu(jjk, "<gradient:#4BAEB7:#700F52>Unlock: " + technique.getDisplayName() + "</gradient>")
							.open(admin, target, abilities,
									bindable -> jjk.getAbilityManager().hasAbility(target, (AbilityType) bindable),
									bindable -> jjk.getAbilityManager().unlockAbility(target, (AbilityType) bindable),
									() -> jjk.getAbilityManager().unlockCursedTechniqueAbilities(target, technique),
									() -> open(admin, target));
				})
				.build();
	}
}
