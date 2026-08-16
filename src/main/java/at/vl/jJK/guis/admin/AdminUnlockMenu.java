package at.vl.jJK.guis.admin;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.minimessage.MiniMessage;

import at.vl.jJK.JJK;
import at.vl.jJK.cursedtechniques.Bindable;
import at.vl.jJK.cursedtechniques.melee.MeleeType;
import at.vl.jJK.cursedtechniques.specialmove.SpecialMoveType;
import at.vl.jJK.cursedtechniques.weaponeering.WeaponeeringType;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemBuilder;
import xyz.xenondevs.invui.window.Window;

/**
 * Admin "unlock" tool: picks a category (Cursed Technique / Melee / Weaponeering / Special Move) to
 * unlock abilities from for {@code target}. Reached from {@link AdminMenu}, operating on whoever
 * opened the admin menu as the target (self-testing), same as {@link CursedTechniqueMenu} already does.
 */
public class AdminUnlockMenu {
	private final JJK jjk;
	private final String title;

	public AdminUnlockMenu(JJK jjk) {
		this.jjk = jjk;

		title = "<gradient:#4BAEB7:#700F52>Unlock: Choose Category</gradient>";
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
		Item techniqueItem = Item.builder()
				.setItemProvider(new ItemBuilder(Material.NETHER_STAR).setName("Cursed Techniques"))
				.addClickHandler(click -> new AdminUnlockTechniqueMenu(jjk).open(admin, target))
				.build();

		Item meleeItem = Item.builder()
				.setItemProvider(new ItemBuilder(Material.IRON_SWORD).setName("Melee"))
				.addClickHandler(click -> openList(admin, target, "Melee", Arrays.asList(MeleeType.values()),
						bindable -> jjk.getMeleeManager().hasMove(target, (MeleeType) bindable),
						bindable -> jjk.getMeleeManager().unlockMove(target, (MeleeType) bindable),
						() -> jjk.getMeleeManager().unlockAllMoves(target)))
				.build();

		Item weaponeeringItem = Item.builder()
				.setItemProvider(new ItemBuilder(Material.BOW).setName("Weaponeering"))
				.addClickHandler(click -> openList(admin, target, "Weaponeering", Arrays.asList(WeaponeeringType.values()),
						bindable -> jjk.getWeaponeeringManager().hasMove(target, (WeaponeeringType) bindable),
						bindable -> jjk.getWeaponeeringManager().unlockMove(target, (WeaponeeringType) bindable),
						() -> jjk.getWeaponeeringManager().unlockAllMoves(target)))
				.build();

		Item specialMoveItem = Item.builder()
				.setItemProvider(new ItemBuilder(Material.BEACON).setName("Special Moves"))
				.addClickHandler(click -> openList(admin, target, "Special Moves", Arrays.asList(SpecialMoveType.values()),
						bindable -> jjk.getSpecialMoveManager().hasMove(target, (SpecialMoveType) bindable),
						bindable -> jjk.getSpecialMoveManager().unlockMove(target, (SpecialMoveType) bindable),
						() -> jjk.getSpecialMoveManager().unlockAllMoves(target)))
				.build();

		Item back = Item.builder()
				.setItemProvider(new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
						.setName(MiniMessage.miniMessage().deserialize("<red>Back")))
				.addClickHandler(click -> new AdminMenu(jjk).open(admin))
				.build();

		return Gui.builder()
				.setStructure(
						"a b c d x x x x x",
						"x x x x x x x x x",
						"x x x x x x x x !"
				)
				.addIngredient('a', techniqueItem)
				.addIngredient('b', meleeItem)
				.addIngredient('c', weaponeeringItem)
				.addIngredient('d', specialMoveItem)
				.addIngredient('!', back)
				.build();
	}

	private void openList(Player admin, Player target, String categoryName, List<? extends Bindable> entries,
			Predicate<Bindable> hasUnlocked, Consumer<Bindable> unlockOne, Runnable unlockAll) {
		new AdminUnlockListMenu(jjk, "<gradient:#4BAEB7:#700F52>Unlock: " + categoryName + "</gradient>")
				.open(admin, target, entries, hasUnlocked, unlockOne, unlockAll, () -> open(admin, target));
	}
}
