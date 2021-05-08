package com.jaoafa.jaotanbeta;

import cloud.commandframework.arguments.standard.StringArgument;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.exceptions.InvalidSyntaxException;
import cloud.commandframework.exceptions.NoPermissionException;
import cloud.commandframework.exceptions.NoSuchCommandException;
import cloud.commandframework.execution.AsynchronousCommandExecutionCoordinator;
import cloud.commandframework.jda.JDA4CommandManager;
import cloud.commandframework.jda.JDACommandSender;
import cloud.commandframework.jda.JDAGuildSender;
import cloud.commandframework.jda.JDAPrivateSender;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.List;

public class Main {
    static JDA jda;
    public static BuildTask task = null;

    public static void main(String[] args) {
        File file = new File("config.json");
        if (!file.exists()) {
            System.out.println("[WARNING] config.json not found.");
            System.exit(1);
            return;
        }
        File allowUsersFile = new File("allowUsers.json");
        if (!allowUsersFile.exists()) {
            System.out.println("[WARNING] allowUsers.json not found.");
            System.exit(1);
            return;
        }

        try {
            String json = String.join("\n", Files.readAllLines(file.toPath()));
            JSONObject object = new JSONObject(json);
            jda = JDABuilder.createDefault(object.getString("token"))
                .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_PRESENCES,
                    GatewayIntent.GUILD_MESSAGE_TYPING, GatewayIntent.DIRECT_MESSAGE_TYPING)
                .setAutoReconnect(true)
                .setBulkDeleteSplittingEnabled(false)
                .setContextEnabled(false)
                .build().awaitReady();
        } catch (IOException | LoginException | InterruptedException e) {
            e.printStackTrace();
        }

        try {
            final JDA4CommandManager<JDACommandSender> manager = new JDA4CommandManager<>(
                jda,
                message -> "/",
                (sender, perm) -> {
                    try {
                        String allowUsersJson = String.join("\n", Files.readAllLines(allowUsersFile.toPath()));
                        List<Object> allowUsers = new JSONArray(allowUsersJson).toList();
                        return allowUsers.contains(sender.getUser().getId());
                    } catch (IOException e) {
                        e.printStackTrace();
                        return false;
                    }
                },
                AsynchronousCommandExecutionCoordinator.simpleCoordinator(),
                sender -> {
                    MessageReceivedEvent event = sender.getEvent().orElse(null);

                    if (sender instanceof JDAPrivateSender) {
                        JDAPrivateSender jdaPrivateSender = (JDAPrivateSender) sender;
                        return new JDAPrivateSender(event, jdaPrivateSender.getUser(), jdaPrivateSender.getPrivateChannel());
                    }

                    if (sender instanceof JDAGuildSender) {
                        JDAGuildSender jdaGuildSender = (JDAGuildSender) sender;
                        return new JDAGuildSender(event, jdaGuildSender.getMember(), jdaGuildSender.getTextChannel());
                    }

                    return null;
                },
                user -> {
                    MessageReceivedEvent event = user.getEvent().orElse(null);
                    if (user instanceof JDAPrivateSender) {
                        JDAPrivateSender privateUser = (JDAPrivateSender) user;
                        return new JDAPrivateSender(event, privateUser.getUser(), privateUser.getPrivateChannel());
                    }

                    if (user instanceof JDAGuildSender) {
                        JDAGuildSender guildUser = (JDAGuildSender) user;
                        return new JDAGuildSender(event, guildUser.getMember(), guildUser.getTextChannel());
                    }

                    return null;
                }
            );

            manager.registerExceptionHandler(NoSuchCommandException.class, (c, e) -> {
            }); // コマンドがなくてもなにもしない

            manager.registerExceptionHandler(InvalidSyntaxException.class,
                (c, e) -> {
                    if (c.getEvent().isPresent()) {
                        c.getEvent().get().getMessage().reply(String.format("コマンドの構文が不正です。正しい構文: `%s`", e.getCorrectSyntax())).queue();
                    }
                });

            manager.registerExceptionHandler(NoPermissionException.class, (c, e) -> {
                if (c.getEvent().isPresent()) {
                    c.getEvent().get().getMessage().reply("コマンドを使用する権限がありません。").queue();
                }
            });

            manager.registerExceptionHandler(Exception.class, (c, e) -> {
                if (e instanceof NoSuchCommandException || e instanceof InvalidSyntaxException || e instanceof NoPermissionException) {
                    return;
                }
                if (c.getEvent().isPresent()) {
                    c.getEvent().get().getMessage().reply(MessageFormat.format("コマンドの実行に失敗しました: {0} ({1})",
                        e.getMessage(),
                        e.getClass().getName())).queue();
                }
            });

            manager.command(manager.commandBuilder("beta")
                .argument(StringArgument
                    .<JDACommandSender>newBuilder("user")
                    .asOptionalWithDefault("jaoafa"))
                .argument(StringArgument
                    .<JDACommandSender>newBuilder("repo")
                    .asOptionalWithDefault("Javajaotan2"))
                .argument(StringArgument
                    .<JDACommandSender>newBuilder("branch")
                    .asOptionalWithDefault("master"))
                .handler(Main::commandBeta)
                .build());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void commandBeta(CommandContext<JDACommandSender> context) {
        if (!context.getSender().getEvent().isPresent()) {
            return;
        }
        if (!context.getSender().getEvent().get().isFromGuild()) {
            return;
        }
        Guild guild = context.getSender().getEvent().get().getGuild();
        Member member = guild.getMember(context.getSender().getUser());
        if (member == null) return;
        Message message = context.getSender().getEvent().get().getMessage();

        File allowUsersFile = new File("allowUsers.json");
        if (!allowUsersFile.exists()) {
            return;
        }
        try {
            String allowUsersJson = String.join("\n", Files.readAllLines(allowUsersFile.toPath()));
            List<Object> allowUsers = new JSONArray(allowUsersJson).toList();
            if (!allowUsers.contains(member.getId())) {
                message.reply("あなたにはこのコマンドを使用する権限がありません。").queue();
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
            message.reply("システムエラーが発生しました。").queue();
            return;
        }

        String user = context.get("user");
        String repo = context.get("repo");
        String branch = context.get("branch");

        ProgressMessageGenerator pmg = new ProgressMessageGenerator();
        Message sendMessage = message.reply(new EmbedBuilder().setTitle("LOADING...").build()).complete();
        task = new BuildTask(member, sendMessage, pmg, user, repo, branch);
        task.start();
    }
}
