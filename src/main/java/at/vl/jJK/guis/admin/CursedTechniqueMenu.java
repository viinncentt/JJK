package at.vl.jJK.guis.admin;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.minimessage.MiniMessage;

import at.vl.jJK.JJK;
import at.vl.jJK.cursedtechniques.technique.CursedTechniqueType;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemBuilder;
import xyz.xenondevs.invui.window.Window;

public class CursedTechniqueMenu {
	private final JJK jjk;

	// Admin Menu GUI
	private final String cursedTechniqueTitle;

	public CursedTechniqueMenu(JJK jjk) {
		this.jjk = jjk;

		cursedTechniqueTitle = "<gradient:#4BAEB7:#700F52>JJK Cursed Technique Menu</gradient>";
	}

	public void open(Player player) {
		// Ten Shadows
		Item tenShadowsItem = Item.builder()
				.setItemProvider(new ItemBuilder(Material.BLACK_DYE)
						.setName(MiniMessage.miniMessage().deserialize(CursedTechniqueType.TEN_SHADOWS.getDisplayName())))
				.addClickHandler(click -> {
					jjk.getCursedTechniqueManager().addCursedTechnique(player, CursedTechniqueType.TEN_SHADOWS);
					player.closeInventory();
				})
				.build();

		// Projection Sorcery
		Item projectionSorceryItem = Item.builder()
				.setItemProvider(new ItemBuilder(Material.GREEN_DYE)
						.setName(MiniMessage.miniMessage().deserialize(CursedTechniqueType.PROJECTION_SORCERY.getDisplayName())))
				.addClickHandler(click -> {
					jjk.getCursedTechniqueManager().addCursedTechnique(player, CursedTechniqueType.PROJECTION_SORCERY);
					player.closeInventory();
				})
				.build();

		// Straw Doll
		Item strawDollItem = Item.builder()
				.setItemProvider(new ItemBuilder(Material.YELLOW_DYE)
						.setName(MiniMessage.miniMessage().deserialize(CursedTechniqueType.STRAW_DOLL.getDisplayName())))
				.addClickHandler(click -> {
					jjk.getCursedTechniqueManager().addCursedTechnique(player, CursedTechniqueType.STRAW_DOLL);
					player.closeInventory();
				})
				.build();

		// Back
		Item back = Item.builder()
				.setItemProvider(new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
						.setName(MiniMessage.miniMessage().deserialize("<red>Back")))
				.addClickHandler(click -> {
					AdminMenu adminMenu = new AdminMenu(jjk);
					adminMenu.open(player);
				})
				.build();

		Gui am = Gui.builder()
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

		Window window = Window.builder()
				.setTitle(MiniMessage.miniMessage().deserialize(cursedTechniqueTitle))
				.setUpperGui(am)
				.setViewer(player)
				.build();

		window.open();
	}
}
