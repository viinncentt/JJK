package at.vl.jJK.commands;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;

import net.kyori.adventure.text.minimessage.MiniMessage;

import at.vl.jJK.JJK;
import at.vl.jJK.cursedtechniques.technique.AbilityType;
import at.vl.jJK.cursedtechniques.technique.CursedTechniqueType;
import at.vl.jJK.cursedtechniques.melee.MeleeType;
import at.vl.jJK.cursedtechniques.shikigami.ShikigamiType;
import at.vl.jJK.cursedtechniques.specialmove.SpecialMoveType;
import at.vl.jJK.cursedtechniques.weaponeering.WeaponeeringType;
import at.vl.jJK.guis.admin.AdminMenu;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;

public class AdminMenuCommand implements JJKCommand {

	private final JJK jjk;

	public AdminMenuCommand(JJK jjk) {
		this.jjk = jjk;
	}

	@Override
	public LiteralCommandNode<CommandSourceStack> build() {
		return Commands.literal("adminmenu")
				.requires(source -> source.getSender().hasPermission("jjk.admin"))
				.executes(ctx -> {
					Player player = (Player) ctx.getSource().getSender();

					// Create GUI
					AdminMenu am = new AdminMenu(jjk);
					am.open(player);

					player.sendMessage(MiniMessage.miniMessage()
							.deserialize(jjk.getLogo() + "<white> Admin Menu opened</white>"));
					return Command.SINGLE_SUCCESS;
				})

				// Arguments

				// reload
				.then(Commands.literal("reload")
						.executes(ctx -> {
							Player player = (Player) ctx.getSource().getSender();

							jjk.reloadConfig();
							jjk.getCursedEnergyManager().reload();
							jjk.getCursedTechniqueManager().reload();
							jjk.getLocatorBarManager().reload();
							jjk.getShikigamiManager().reload();
							jjk.getShikigamiListener().reload();
							jjk.getMeleeManager().reload();
							jjk.getWeaponeeringManager().reload();
							jjk.getSpecialMoveManager().reload();

							player.sendMessage(MiniMessage.miniMessage()
									.deserialize(jjk.getLogo() + " <green>Successfully reloaded</green>"));

							return Command.SINGLE_SUCCESS;
						})
				)

				// cursedenergy
				.then(cursedEnergyCommand("cursedenergy"))
				.then(cursedEnergyCommand("ce"))

				// technique
				.then(Commands.literal("technique")
						.then(Commands.argument("player", ArgumentTypes.player())
								.then(Commands.argument("technique", StringArgumentType.word())
										.suggests(enumSuggestions(CursedTechniqueType.class))
										.executes(ctx -> {
											PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
											Player target = resolver.resolve(ctx.getSource()).getFirst();
											CommandSender sender = ctx.getSource().getSender();
											String raw = StringArgumentType.getString(ctx, "technique");

											CursedTechniqueType technique;
											try {
												technique = CursedTechniqueType.valueOf(raw.toUpperCase());
											} catch (IllegalArgumentException e) {
												sender.sendMessage(MiniMessage.miniMessage()
														.deserialize(jjk.getLogo() + " <red>Unknown technique: " + raw + "</red>"));
												return Command.SINGLE_SUCCESS;
											}

											jjk.getCursedTechniqueManager().addCursedTechnique(target, technique);
											jjk.getCursedTechniqueManager().activateTechnique(target);

											sender.sendMessage(MiniMessage.miniMessage().deserialize(jjk.getLogo()
													+ " <green>Set " + target.getName() + "'s cursed technique to " + technique.name() + "</green>"));
											return Command.SINGLE_SUCCESS;
										})
								)
						)
				)

				// unlock
				.then(Commands.literal("unlock")
						.then(unlockTechniqueCommand())
						.then(unlockAbilityCommand())
						.then(unlockCategoryCommand("melee", MeleeType.class,
								jjk.getMeleeManager()::unlockAllMoves, jjk.getMeleeManager()::unlockMove))
						.then(unlockCategoryCommand("weaponeering", WeaponeeringType.class,
								jjk.getWeaponeeringManager()::unlockAllMoves, jjk.getWeaponeeringManager()::unlockMove))
						.then(unlockCategoryCommand("specialmove", SpecialMoveType.class,
								jjk.getSpecialMoveManager()::unlockAllMoves, jjk.getSpecialMoveManager()::unlockMove))
				)

				// shikigami
				.then(Commands.literal("shikigami")
						.then(shikigamiTameCommand())
						.then(shikigamiSummonableCommand())
						.then(shikigamiSummonCommand())
						.then(shikigamiDespawnCommand())
						.then(shikigamiKillCommand())
				)

				.build();
	}

	@Override
	public List<String> aliases() {
		return List.of("am");
	}

	@Override
	public String description() {
		return "Opens Admin Menu";
	}

	/**
	 * "&lt;name&gt; set &lt;player&gt; &lt;amount&gt;" and "&lt;name&gt; max &lt;player&gt; &lt;amount&gt;" —
	 * literal subcommands matching every other command's style (technique/unlock/shikigami), rather
	 * than the old free-typed "set" word argument that wasn't validated or suggested and only ever
	 * did one thing (set the max).
	 */
	private LiteralCommandNode<CommandSourceStack> cursedEnergyCommand(String name) {
		return Commands.literal(name)
				.then(Commands.literal("set")
						.then(Commands.argument("player", ArgumentTypes.player())
								.then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
									.executes(ctx -> {
										PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
										Player target = resolver.resolve(ctx.getSource()).getFirst();
										CommandSender sender = ctx.getSource().getSender();
										Double amount = DoubleArgumentType.getDouble(ctx, "amount");

										jjk.getCursedEnergyManager().setCursedEnergy(target, amount);

										sender.sendMessage(MiniMessage.miniMessage().deserialize(jjk.getLogo()
												+ " <green>Successfully set " + target.getName() + "'s Cursed Energy to " + amount + "</green>"));
										return Command.SINGLE_SUCCESS;
									})
								)
						)
				)
				.then(Commands.literal("max")
						.then(Commands.argument("player", ArgumentTypes.player())
								.then(Commands.argument("amount", DoubleArgumentType.doubleArg(1))
									.executes(ctx -> {
										PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
										Player target = resolver.resolve(ctx.getSource()).getFirst();
										CommandSender sender = ctx.getSource().getSender();
										Double amount = DoubleArgumentType.getDouble(ctx, "amount");

										jjk.getCursedEnergyManager().setMaxCursedEnergy(target, amount);

										sender.sendMessage(MiniMessage.miniMessage().deserialize(jjk.getLogo()
												+ " <green>Successfully set " + target.getName() + "'s Max Cursed Energy to " + amount + "</green>"));
										return Command.SINGLE_SUCCESS;
									})
								)
						)
				)
				.build();
	}

	/**
	 * "unlock technique <player> <technique>" — unlocks every {@link AbilityType} belonging to the
	 * given {@link CursedTechniqueType} for the target player.
	 */
	private LiteralCommandNode<CommandSourceStack> unlockTechniqueCommand() {
		return Commands.literal("technique")
				.then(Commands.argument("player", ArgumentTypes.player())
						.then(Commands.argument("technique", StringArgumentType.word())
								.suggests(enumSuggestions(CursedTechniqueType.class))
								.executes(ctx -> {
									PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
									Player target = resolver.resolve(ctx.getSource()).getFirst();
									CommandSender sender = ctx.getSource().getSender();
									String raw = StringArgumentType.getString(ctx, "technique");

									CursedTechniqueType technique;
									try {
										technique = CursedTechniqueType.valueOf(raw.toUpperCase());
									} catch (IllegalArgumentException e) {
										sender.sendMessage(MiniMessage.miniMessage()
												.deserialize(jjk.getLogo() + " <red>Unknown technique: " + raw + "</red>"));
										return Command.SINGLE_SUCCESS;
									}

									jjk.getAbilityManager().unlockCursedTechniqueAbilities(target, technique);

									sender.sendMessage(MiniMessage.miniMessage().deserialize(jjk.getLogo()
											+ " <green>Unlocked all " + technique.name() + " abilities for " + target.getName() + "</green>"));
									return Command.SINGLE_SUCCESS;
								})
						)
				)
				.build();
	}

	/**
	 * "unlock ability <player> <ability>" — unlocks a single {@link AbilityType} for the target
	 * player.
	 */
	private LiteralCommandNode<CommandSourceStack> unlockAbilityCommand() {
		return Commands.literal("ability")
				.then(Commands.argument("player", ArgumentTypes.player())
						.then(Commands.argument("ability", StringArgumentType.word())
								.suggests(enumSuggestions(AbilityType.class))
								.executes(ctx -> {
									PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
									Player target = resolver.resolve(ctx.getSource()).getFirst();
									CommandSender sender = ctx.getSource().getSender();
									String raw = StringArgumentType.getString(ctx, "ability");

									AbilityType ability;
									try {
										ability = AbilityType.valueOf(raw.toUpperCase());
									} catch (IllegalArgumentException e) {
										sender.sendMessage(MiniMessage.miniMessage()
												.deserialize(jjk.getLogo() + " <red>Unknown ability: " + raw + "</red>"));
										return Command.SINGLE_SUCCESS;
									}

									jjk.getAbilityManager().unlockAbility(target, ability);

									sender.sendMessage(MiniMessage.miniMessage().deserialize(jjk.getLogo()
											+ " <green>Unlocked " + ability.name() + " for " + target.getName() + "</green>"));
									return Command.SINGLE_SUCCESS;
								})
						)
				)
				.build();
	}

	/**
	 * Builds an "unlock &lt;name&gt; &lt;player&gt; [&lt;type&gt;]" branch for a flat, technique-
	 * independent move category (Melee/Weaponeering/Special Move): with no type argument it unlocks
	 * every value of {@code enumClass} via {@code unlockAll}, with one it unlocks just that value via
	 * {@code unlockOne}.
	 */
	private <E extends Enum<E>> LiteralCommandNode<CommandSourceStack> unlockCategoryCommand(
			String name, Class<E> enumClass, Consumer<Player> unlockAll, BiConsumer<Player, E> unlockOne) {
		return Commands.literal(name)
				.then(Commands.argument("player", ArgumentTypes.player())
						.executes(ctx -> {
							PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
							Player target = resolver.resolve(ctx.getSource()).getFirst();
							CommandSender sender = ctx.getSource().getSender();

							unlockAll.accept(target);

							sender.sendMessage(MiniMessage.miniMessage().deserialize(jjk.getLogo()
									+ " <green>Unlocked all " + name + " moves for " + target.getName() + "</green>"));
							return Command.SINGLE_SUCCESS;
						})
						.then(Commands.argument(name, StringArgumentType.word())
								.suggests(enumSuggestions(enumClass))
								.executes(ctx -> {
									PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
									Player target = resolver.resolve(ctx.getSource()).getFirst();
									CommandSender sender = ctx.getSource().getSender();
									String raw = StringArgumentType.getString(ctx, name);

									E value;
									try {
										value = Enum.valueOf(enumClass, raw.toUpperCase());
									} catch (IllegalArgumentException e) {
										sender.sendMessage(MiniMessage.miniMessage()
												.deserialize(jjk.getLogo() + " <red>Unknown " + name + ": " + raw + "</red>"));
										return Command.SINGLE_SUCCESS;
									}

									unlockOne.accept(target, value);

									sender.sendMessage(MiniMessage.miniMessage().deserialize(jjk.getLogo()
											+ " <green>Unlocked " + value.name() + " for " + target.getName() + "</green>"));
									return Command.SINGLE_SUCCESS;
								})
						)
				)
				.build();
	}

	private <E extends Enum<E>> SuggestionProvider<CommandSourceStack> enumSuggestions(Class<E> enumClass) {
		return (ctx, builder) -> {
			for (E value : enumClass.getEnumConstants()) {
				builder.suggest(value.name());
			}
			return builder.buildFuture();
		};
	}

	/**
	 * "shikigami tame <player> <type> <tamed>" — sets whether the target player has tamed the given
	 * shikigami type (per-player, see ShikigamiManager#setTamed).
	 */
	private LiteralCommandNode<CommandSourceStack> shikigamiTameCommand() {
		return Commands.literal("tame")
				.then(Commands.argument("player", ArgumentTypes.player())
						.then(Commands.argument("type", StringArgumentType.word())
								.suggests(enumSuggestions(ShikigamiType.class))
								.then(Commands.argument("tamed", BoolArgumentType.bool())
										.executes(ctx -> {
											PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
											Player target = resolver.resolve(ctx.getSource()).getFirst();
											CommandSender sender = ctx.getSource().getSender();
											boolean tamed = BoolArgumentType.getBool(ctx, "tamed");

											ShikigamiType type = parseShikigamiType(ctx, sender);
											if (type == null) {
												return Command.SINGLE_SUCCESS;
											}

											jjk.getShikigamiManager().setTamed(target, type, tamed);

											sender.sendMessage(MiniMessage.miniMessage().deserialize(jjk.getLogo()
													+ " <green>Set " + target.getName() + "'s " + type.name() + " to "
													+ (tamed ? "tamed" : "untamed") + "</green>"));
											return Command.SINGLE_SUCCESS;
										})
								)
						)
				)
				.build();
	}

	/**
	 * "shikigami summonable <player> <type> <summonable>" — sets whether the target player can
	 * summon the given shikigami type at all (per-player, see ShikigamiManager#setSummonable).
	 */
	private LiteralCommandNode<CommandSourceStack> shikigamiSummonableCommand() {
		return Commands.literal("summonable")
				.then(Commands.argument("player", ArgumentTypes.player())
						.then(Commands.argument("type", StringArgumentType.word())
								.suggests(enumSuggestions(ShikigamiType.class))
								.then(Commands.argument("summonable", BoolArgumentType.bool())
										.executes(ctx -> {
											PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
											Player target = resolver.resolve(ctx.getSource()).getFirst();
											CommandSender sender = ctx.getSource().getSender();
											boolean summonable = BoolArgumentType.getBool(ctx, "summonable");

											ShikigamiType type = parseShikigamiType(ctx, sender);
											if (type == null) {
												return Command.SINGLE_SUCCESS;
											}

											jjk.getShikigamiManager().setSummonable(target, type, summonable);

											sender.sendMessage(MiniMessage.miniMessage().deserialize(jjk.getLogo()
													+ " <green>Set " + target.getName() + "'s " + type.name() + " summonable to "
													+ summonable + "</green>"));
											return Command.SINGLE_SUCCESS;
										})
								)
						)
				)
				.build();
	}

	/**
	 * "shikigami summon <player> <type>" — directly summons a shikigami for the target player,
	 * bypassing cursed energy/cooldown checks (this is an admin tool, not the real ability path).
	 * Still respects summonable (summon() itself returns null if it isn't) and only hands over a
	 * control item if the type is currently tamed for that player.
	 */
	private LiteralCommandNode<CommandSourceStack> shikigamiSummonCommand() {
		return Commands.literal("summon")
				.then(Commands.argument("player", ArgumentTypes.player())
						.then(Commands.argument("type", StringArgumentType.word())
								.suggests(enumSuggestions(ShikigamiType.class))
								.executes(ctx -> {
									PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
									Player target = resolver.resolve(ctx.getSource()).getFirst();
									CommandSender sender = ctx.getSource().getSender();

									ShikigamiType type = parseShikigamiType(ctx, sender);
									if (type == null) {
										return Command.SINGLE_SUCCESS;
									}

									Mob shikigami = jjk.getShikigamiManager().summon(target, target.getLocation(), type);
									if (shikigami == null) {
										sender.sendMessage(MiniMessage.miniMessage().deserialize(jjk.getLogo()
												+ " <red>" + target.getName() + " can't summon " + type.name() + " right now.</red>"));
										return Command.SINGLE_SUCCESS;
									}

									if (jjk.getShikigamiManager().hasTamed(target, type)) {
										jjk.getShikigamiManager().giveShikigamiItem(target, formatShikigamiName(type),
												type, 1, shikigami.getUniqueId());
									}

									sender.sendMessage(MiniMessage.miniMessage().deserialize(jjk.getLogo()
											+ " <green>Summoned " + type.name() + " for " + target.getName() + "</green>"));
									return Command.SINGLE_SUCCESS;
								})
						)
				)
				.build();
	}

	/**
	 * "shikigami despawn <player> [type]" — cleanly despawns (health saved, item removed) either
	 * every shikigami the target has out, or just one specific type.
	 */
	private LiteralCommandNode<CommandSourceStack> shikigamiDespawnCommand() {
		return Commands.literal("despawn")
				.then(Commands.argument("player", ArgumentTypes.player())
						.executes(ctx -> {
							PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
							Player target = resolver.resolve(ctx.getSource()).getFirst();
							CommandSender sender = ctx.getSource().getSender();

							jjk.getShikigamiManager().despawnAll(target.getUniqueId());

							sender.sendMessage(MiniMessage.miniMessage().deserialize(jjk.getLogo()
									+ " <green>Despawned all of " + target.getName() + "'s shikigami</green>"));
							return Command.SINGLE_SUCCESS;
						})
						.then(Commands.argument("type", StringArgumentType.word())
								.suggests(enumSuggestions(ShikigamiType.class))
								.executes(ctx -> {
									PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
									Player target = resolver.resolve(ctx.getSource()).getFirst();
									CommandSender sender = ctx.getSource().getSender();

									ShikigamiType type = parseShikigamiType(ctx, sender);
									if (type == null) {
										return Command.SINGLE_SUCCESS;
									}

									Mob shikigami = jjk.getShikigamiManager().getActiveShikigami(target.getUniqueId(), type);
									if (shikigami == null) {
										sender.sendMessage(MiniMessage.miniMessage().deserialize(jjk.getLogo()
												+ " <red>" + target.getName() + " has no active " + type.name() + "</red>"));
										return Command.SINGLE_SUCCESS;
									}

									jjk.getShikigamiManager().despawn(shikigami.getUniqueId());

									sender.sendMessage(MiniMessage.miniMessage().deserialize(jjk.getLogo()
											+ " <green>Despawned " + target.getName() + "'s " + type.name() + "</green>"));
									return Command.SINGLE_SUCCESS;
								})
						)
				)
				.build();
	}

	/**
	 * "shikigami kill <player> [type]" — force-kills (setHealth(0), routes through the normal death
	 * event/pipeline) either every shikigami the target has out, or just one specific type. Unlike
	 * a real combat kill this never has a Player killer, so it never trips the
	 * kill-your-own-untamed-one-to-tame-it logic in ShikigamiListener#onDeath.
	 */
	private LiteralCommandNode<CommandSourceStack> shikigamiKillCommand() {
		return Commands.literal("kill")
				.then(Commands.argument("player", ArgumentTypes.player())
						.executes(ctx -> {
							PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
							Player target = resolver.resolve(ctx.getSource()).getFirst();
							CommandSender sender = ctx.getSource().getSender();

							for (Mob shikigami : jjk.getShikigamiManager().getActiveShikigami(target.getUniqueId())) {
								shikigami.setHealth(0);
							}

							sender.sendMessage(MiniMessage.miniMessage().deserialize(jjk.getLogo()
									+ " <green>Killed all of " + target.getName() + "'s shikigami</green>"));
							return Command.SINGLE_SUCCESS;
						})
						.then(Commands.argument("type", StringArgumentType.word())
								.suggests(enumSuggestions(ShikigamiType.class))
								.executes(ctx -> {
									PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
									Player target = resolver.resolve(ctx.getSource()).getFirst();
									CommandSender sender = ctx.getSource().getSender();

									ShikigamiType type = parseShikigamiType(ctx, sender);
									if (type == null) {
										return Command.SINGLE_SUCCESS;
									}

									Mob shikigami = jjk.getShikigamiManager().getActiveShikigami(target.getUniqueId(), type);
									if (shikigami == null) {
										sender.sendMessage(MiniMessage.miniMessage().deserialize(jjk.getLogo()
												+ " <red>" + target.getName() + " has no active " + type.name() + "</red>"));
										return Command.SINGLE_SUCCESS;
									}

									shikigami.setHealth(0);

									sender.sendMessage(MiniMessage.miniMessage().deserialize(jjk.getLogo()
											+ " <green>Killed " + target.getName() + "'s " + type.name() + "</green>"));
									return Command.SINGLE_SUCCESS;
								})
						)
				)
				.build();
	}

	private ShikigamiType parseShikigamiType(CommandContext<CommandSourceStack> ctx, CommandSender sender) {
		String raw = StringArgumentType.getString(ctx, "type");
		try {
			return ShikigamiType.valueOf(raw.toUpperCase());
		} catch (IllegalArgumentException e) {
			sender.sendMessage(MiniMessage.miniMessage().deserialize(jjk.getLogo() + " <red>Unknown shikigami: " + raw + "</red>"));
			return null;
		}
	}

	/**
	 * "WHITE_DIVINE_DOG" -> "White Divine Dog", for the control item's display name when summoned
	 * generically through this admin command (which has no per-type flavor text the way
	 * TenShadows#activateDivineDogs does).
	 */
	private String formatShikigamiName(ShikigamiType type) {
		String[] words = type.name().split("_");
		StringBuilder builder = new StringBuilder();
		for (String word : words) {
			if (!builder.isEmpty()) {
				builder.append(' ');
			}
			builder.append(word.charAt(0)).append(word.substring(1).toLowerCase());
		}
		return builder.toString();
	}
}
