package at.vl.jJK.guis.admin;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.minimessage.MiniMessage;

import at.vl.jJK.JJK;
import at.vl.jJK.cursedtechniques.Bindable;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemBuilder;
import xyz.xenondevs.invui.window.Window;

/**
 * Generic admin "unlock" list: shows every {@link Bindable} in {@code entries} for {@code target},
 * colored by whether they already have it, plus a pinned "Unlock All" item. Reused for the Melee,
 * Weaponeering and Special Move categories directly, and for a single Cursed Technique's abilities
 * after picking one in {@link AdminUnlockTechniqueMenu}.
 */
public class AdminUnlockListMenu {
	private final JJK jjk;
	private final String title;

	public AdminUnlockListMenu(JJK jjk, String title) {
		this.jjk = jjk;
		this.title = title;
	}

	public void open(Player admin, Player target, List<? extends Bindable> entries,
			Predicate<Bindable> hasUnlocked, Consumer<Bindable> unlockOne, Runnable unlockAll, Runnable back) {
		Window window = Window.builder()
				.setTitle(MiniMessage.miniMessage().deserialize(title))
				.setUpperGui(createGui(admin, target, entries, hasUnlocked, unlockOne, unlockAll, back))
				.setViewer(admin)
				.build();

		window.open();
	}

	private Gui createGui(Player admin, Player target, List<? extends Bindable> entries,
			Predicate<Bindable> hasUnlocked, Consumer<Bindable> unlockOne, Runnable unlockAll, Runnable back) {
		Item grayGlass = Item.builder()
				.setItemProvider(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setName(""))
				.build();

		int rows = Math.max(1, Math.min((int) Math.ceil((entries.size() + 2) / 9.0), 6));
		Gui gui = Gui.empty(9, rows);

		for (int i = 0; i < entries.size(); i++) {
			gui.setItem(i, createEntryItem(admin, target, entries.get(i), hasUnlocked, unlockOne,
					entries, unlockAll, back));
		}

		int lastSlot = gui.getSize() - 1;
		int unlockAllSlot = lastSlot - 1;

		for (int s = entries.size(); s < unlockAllSlot; s++) {
			gui.setItem(s, grayGlass);
		}

		gui.setItem(unlockAllSlot, createUnlockAllItem(admin, target, entries, hasUnlocked, unlockOne, unlockAll, back));
		gui.setItem(lastSlot, createBackItem(back));

		return gui;
	}

	private Item createEntryItem(Player admin, Player target, Bindable entry, Predicate<Bindable> hasUnlocked,
			Consumer<Bindable> unlockOne, List<? extends Bindable> entries, Runnable unlockAll, Runnable back) {
		boolean unlocked = hasUnlocked.test(entry);
		String name = (unlocked ? "<green>" : "<gray>") + entry.getDisplayName();

		return Item.builder()
				.setItemProvider(new ItemBuilder(entry.getMaterial())
						.setName(MiniMessage.miniMessage().deserialize(name)))
				.addClickHandler(click -> {
					unlockOne.accept(entry);
					admin.sendMessage(MiniMessage.miniMessage().deserialize(jjk.getLogo()
							+ " <green>Unlocked " + entry.getDisplayName() + " <green>for " + target.getName()));
					open(admin, target, entries, hasUnlocked, unlockOne, unlockAll, back);
				})
				.build();
	}

	private Item createUnlockAllItem(Player admin, Player target, List<? extends Bindable> entries,
			Predicate<Bindable> hasUnlocked, Consumer<Bindable> unlockOne, Runnable unlockAll, Runnable back) {
		return Item.builder()
				.setItemProvider(new ItemBuilder(Material.LIME_DYE)
						.setName(MiniMessage.miniMessage().deserialize("<green>Unlock All")))
				.addClickHandler(click -> {
					unlockAll.run();
					admin.sendMessage(MiniMessage.miniMessage().deserialize(
							jjk.getLogo() + " <green>Unlocked everything for " + target.getName()));
					open(admin, target, entries, hasUnlocked, unlockOne, unlockAll, back);
				})
				.build();
	}

	private Item createBackItem(Runnable back) {
		return Item.builder()
				.setItemProvider(new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
						.setName(MiniMessage.miniMessage().deserialize("<red>Back")))
				.addClickHandler(click -> back.run())
				.build();
	}
}
