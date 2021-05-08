package com.jaoafa.jaotanbeta;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class ProgressMessageGenerator {
    private final LinkedHashMap<Type, Status> progresses = new LinkedHashMap<>();
    private final Map<Type, String> typeMessage = new HashMap<>();
    private Color color = Color.YELLOW;
    private String message = null;

    public ProgressMessageGenerator() {
        Arrays.asList(
            Type.CHECK_REPOSITORY,
            Type.STOP_PROCESS,
            Type.REMOVE_WORK_DIRECTORY,
            Type.CLONE_REPOSITORY,
            Type.BUILD,
            Type.START_PROCESS
        ).forEach(type -> progresses.put(type, Status.WAITING));
    }

    public void set(Type type, Status status) {
        progresses.put(type, status);

        if (progresses.containsValue(Status.ERROR)) {
            color = Color.RED;
        } else if (progresses.containsValue(Status.WAITING) || progresses.containsValue(Status.RUNNING)) {
            color = Color.YELLOW;
        }
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setTypeMessage(Type type, String message) {
        this.typeMessage.put(type, message);
    }

    public MessageEmbed build() {
        return new EmbedBuilder()
            .setTitle("Building Javajaotan2 Beta")
            .setDescription(progresses.entrySet().stream()
                .map(entry -> String.format("%s %s %s",
                    entry.getValue().getEmoji(),
                    entry.getKey().getMessage(),
                    typeMessage.containsKey(entry.getKey()) ? " : " + typeMessage.get(entry.getKey()) : ""))
                .collect(Collectors.joining("\n")))
            .setColor(color)
            .addField("メッセージ", message != null ? message : "", false)
            .setTimestamp(Instant.now())
            .build();
    }

    enum Type {
        CHECK_REPOSITORY("リポジトリのチェック"),
        STOP_PROCESS("Javajaotan (Beta)の停止"),
        REMOVE_WORK_DIRECTORY("作業ディレクトリの削除"),
        CLONE_REPOSITORY("GitHubからリポジトリをクローン"),
        BUILD("コードのビルド"),
        START_PROCESS("Javajaotan (Beta)の起動");

        String message;

        Type(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    enum Status {
        WAITING(":play_pause:"),
        RUNNING(":arrow_forward:"),
        SUCCESSFUL(":white_check_mark:"),
        ERROR(":warning:");

        String emoji;

        Status(String emoji) {
            this.emoji = emoji;
        }

        public String getEmoji() {
            return emoji;
        }
    }
}
