/*
 * * Copyright 2019-2020 github.com/ReflxctionDev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.reflxction.commands;

import io.github.reflxction.commands.CommandCallback.CommandCallbackException;
import io.github.reflxction.commands.CommandResolvers.ResolverFallback;
import org.apache.commons.lang.ArrayUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The command processor
 */
public class CommandHandler {

    /**
     * A map of all commands
     */
    private final Map<String, SubcommandInvokation> commands = new HashMap<>();

    /**
     * A map of all commands, main names only
     */
    private final Map<String, SubcommandInvokation> namesOnly = new HashMap<>();

    /**
     * The command resolvers
     */
    private final CommandResolvers resolvers = new CommandResolvers();

    /**
     * The messaging prefix
     */
    String messagingPrefix = "";

    Consumer<CommandContext> invalidCommand = (c) -> c.reply("&cInvalid sub-command. Run &e/%s help &cfor a list of commands", c.getCommand().getName());
    Consumer<CommandContext> noPermission = (c) -> c.reply("&cYou do not have permission to run this command!");
    Consumer<CommandContext> notPlayer = (c) -> c.reply("&cYou must be a player to use this command!");
    Consumer<CommandContext> invalidCommandUsage = (c) -> c.reply("&cInvalid usage. Try &e/%s %s &d%s&c.", c.getCommand().getName(), c.getSubscription().name, c.getSubscription().parameters);
    ResolverFallback resolverFail = (name, argument, context) -> context.reply("&cInvalid %s: &e%s", name, argument);

    /**
     * Registers the specified command
     *
     * @param callback Command to register. Must be annotated with {@link PluginSubcommand}.
     */
    public void registerCallback(CommandCallback callback) {
        if (!callback.getClass().isAnnotationPresent(PluginSubcommand.class))
            throw new IllegalArgumentException("Class " + callback.getClass().getName() + " must be annotated with PluginSubcommand!");
        PluginSubcommand p = callback.getClass().getAnnotation(PluginSubcommand.class);
        CommandWrapper wrapper = new CommandWrapper(p.name(), p.description(), p.parameters(), p.aliases(), Arrays.stream(p.helpMenu()).map(CommandCallback::colorize).collect(Collectors.toList()), p.permission(), p.permissionAccess(), p.minimumArguments(), p.requirePlayer(), p.tabCompletions(), callback);
        commands.put(p.name(), wrapper);
        namesOnly.put(p.name(), wrapper);
        for (String alias : p.aliases())
            commands.put(alias, wrapper);

    }

    public void register(Object object) {
        if (object instanceof CommandCallback) registerCallback(((CommandCallback) object));
        for (Method method : object.getClass().getDeclaredMethods()) registerMethod(method, object);
        for (Method method : object.getClass().getMethods()) registerMethod(method, object);
    }

    private void registerMethod(Method method, Object instance) {
        if (method.isAnnotationPresent(PluginSubcommand.class)) {
            PluginSubcommand p = method.getAnnotation(PluginSubcommand.class);
            MethodSubcommand subcommand = new MethodSubcommand(method, instance, p.name(), p.description(), p.parameters(), p.aliases(), Arrays.stream(p.helpMenu()).map(CommandCallback::colorize).collect(Collectors.toList()), p.permission(), p.permissionAccess(), p.minimumArguments(), p.requirePlayer(), p.tabCompletions());
            commands.put(p.name(), subcommand);
            namesOnly.put(p.name(), subcommand);
            for (String alias : p.aliases())
                commands.put(alias, subcommand);
        }
    }

    /**
     * Processes the appropriate subcommand
     *
     * @param command The internal Bukkit command
     * @param sender  The command sender
     * @param args    The main command arguments
     */
    public void onCommand(Command command, CommandSender sender, String[] args) {
        try {
            @Nullable SubcommandInvokation subscription = commands.get(args[0]);
            String[] finalArgs = (String[]) ArrayUtils.subarray(args, 1, args.length);
            CommandContext context = new CommandContext(sender, finalArgs, command, subscription, this);
            if (subscription == null) {
                invalidCommand.accept(context);
                throw new CommandCallbackException();
            }
            if (subscription.requirePlayer) context.requirePlayer();
            context.requireArgs(subscription.minimumArgs);
            if (subscription.permission != null)
                context.checkPermission(subscription.permission);
            subscription.invoke(context);
        } catch (CommandCallbackException e) {
            if (e.getMessage().isEmpty()) return;
            sender.sendMessage((e.prefix() ? messagingPrefix : "") + e.getMessage());
        }
    }

    /**
     * Returns a map of all commands. This includes aliases
     *
     * @return A map of all commands
     */
    public Map<String, SubcommandInvokation> getCommands() {
        return commands;
    }

    /**
     * Returns a map of all commands, with their names only.
     *
     * @return A map of all commands, excluding aliases
     */
    public Map<String, SubcommandInvokation> getNamesOnly() {
        return namesOnly;
    }

    /**
     * Returns the resolvers instance of this handler
     *
     * @return The resolver instance
     */
    public CommandResolvers getResolvers() {
        return resolvers;
    }

    /**
     * Sets the messaging prefix. Every message in {@link CommandContext#reply(String, Object...)} will use this prefix
     *
     * @param messagingPrefix Prefix to set
     */
    public void setMessagingPrefix(@NotNull String messagingPrefix) {
        this.messagingPrefix = CommandCallback.colorize(messagingPrefix);
    }

}