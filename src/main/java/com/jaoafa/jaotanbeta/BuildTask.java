package com.jaoafa.jaotanbeta;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BuildTask extends Thread {
    Member member;
    Message message;
    ProgressMessageGenerator pmg;
    String user;
    String repo;
    String branch;

    public BuildTask(Member member, Message message, ProgressMessageGenerator pmg, String user, String repo, String branch) {
        this.member = member;
        this.message = message;
        this.pmg = pmg;
        this.user = user;
        this.repo = repo;
        this.branch = branch;
    }

    public void run() {
        System.out.println("[INFO] CHECK_REPOSITORY");
        pmg.set(ProgressMessageGenerator.Type.CHECK_REPOSITORY, ProgressMessageGenerator.Status.RUNNING);
        message.editMessage(pmg.build()).queue();
        if (!user.equals("jaoafa") || !repo.equals("Javajaotan2")) {
            JSONObject info = getRepo(user, repo);

            if (info == null) {
                pmg.set(
                    ProgressMessageGenerator.Type.CHECK_REPOSITORY,
                    ProgressMessageGenerator.Status.ERROR
                );
                pmg.setMessage("指定されたリポジトリ「" + user + "/" + repo + "」は見つかりませんでした。");
                message.editMessage(pmg.build()).queue();
                return;
            }

            if (!info.getBoolean("fork")) {
                pmg.set(
                    ProgressMessageGenerator.Type.CHECK_REPOSITORY,
                    ProgressMessageGenerator.Status.ERROR
                );
                pmg.setMessage("指定されたリポジトリ「" + user + "/" + repo + "」はフォークされたリポジトリではありません。");
                message.editMessage(pmg.build()).queue();
                return;
            }

            if (!info.getJSONObject("source").getString("full_name").equals("jaoafa/Javajaotan2")) {
                pmg.set(
                    ProgressMessageGenerator.Type.CHECK_REPOSITORY,
                    ProgressMessageGenerator.Status.ERROR
                );
                pmg.setMessage("指定されたリポジトリ「" + user + "/" + repo + "」は `jaoafa/Javajaotan2` からフォークされたリポジトリではありません。");
                message.editMessage(pmg.build()).queue();
                return;
            }
        }
        JSONObject branchInfo = getRepoBranch(user, repo, branch);
        if (branchInfo == null) {
            pmg.set(
                ProgressMessageGenerator.Type.CHECK_REPOSITORY,
                ProgressMessageGenerator.Status.ERROR
            );
            pmg.setMessage("ブランチ情報の取得に失敗しました。");
            message.editMessage(pmg.build()).queue();
            return;
        }
        String sha = branchInfo.getJSONObject("commit").getString("sha");
        pmg.setTypeMessage(ProgressMessageGenerator.Type.CHECK_REPOSITORY, "最終コミット https://github.com/" + user + "/" + repo + "/commit/" + sha);
        pmg.set(ProgressMessageGenerator.Type.CHECK_REPOSITORY, ProgressMessageGenerator.Status.SUCCESSFUL);

        System.out.println("[INFO] STOP_PROCESS");
        pmg.set(ProgressMessageGenerator.Type.STOP_PROCESS, ProgressMessageGenerator.Status.RUNNING);
        message.editMessage(pmg.build()).queue();

        boolean stopSystemd = runCommand(null, "systemctl stop Javajaotan2Beta");
        if (!stopSystemd) {
            pmg.setMessage("Javajaotan (Beta)の停止に失敗しました。");
            pmg.set(ProgressMessageGenerator.Type.STOP_PROCESS, ProgressMessageGenerator.Status.ERROR);
            message.editMessage(pmg.build()).queue();
            return;
        }
        pmg.set(ProgressMessageGenerator.Type.STOP_PROCESS, ProgressMessageGenerator.Status.SUCCESSFUL);

        System.out.println("[INFO] REMOVE_WORK_DIRECTORY");
        pmg.set(ProgressMessageGenerator.Type.REMOVE_WORK_DIRECTORY, ProgressMessageGenerator.Status.RUNNING);
        message.editMessage(pmg.build()).queue();
        // 作業ディレクトリを削除
        Path dir = Paths.get("work/");
        if(dir.toFile().exists()) {
            try (Stream<Path> walk = Files.walk(dir, FileVisitOption.FOLLOW_LINKS)) {
                List<File> missDeletes = walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .filter(f -> !f.delete())
                    .collect(Collectors.toList());
                if (missDeletes.size() != 0) {
                    pmg.set(ProgressMessageGenerator.Type.REMOVE_WORK_DIRECTORY, ProgressMessageGenerator.Status.ERROR);
                    pmg.setMessage(missDeletes.size() + "個のファイル削除に失敗しました。");
                } else {
                    pmg.set(ProgressMessageGenerator.Type.REMOVE_WORK_DIRECTORY, ProgressMessageGenerator.Status.SUCCESSFUL);
                }
            } catch (IOException ie) {
                ie.printStackTrace();
                pmg.set(ProgressMessageGenerator.Type.REMOVE_WORK_DIRECTORY, ProgressMessageGenerator.Status.ERROR);
                pmg.setMessage("ファイルの削除に失敗しました: " + ie.getMessage());
            }
        }else{
            pmg.set(ProgressMessageGenerator.Type.REMOVE_WORK_DIRECTORY, ProgressMessageGenerator.Status.SUCCESSFUL);
        }

        System.out.println("[INFO] CLONE_REPOSITORY");
        pmg.set(ProgressMessageGenerator.Type.CLONE_REPOSITORY, ProgressMessageGenerator.Status.RUNNING);
        message.editMessage(pmg.build()).queue();

        // クローン
        File cloneDir = new File("work/" + user + "/" + repo + "/");
        if (!cloneDir.exists()) {
            boolean isCreatedDir = cloneDir.mkdirs();
            if (!isCreatedDir) {
                pmg.setMessage("cloneDirディレクトリの作成に失敗しました。");
                pmg.set(ProgressMessageGenerator.Type.CLONE_REPOSITORY, ProgressMessageGenerator.Status.ERROR);
                message.editMessage(pmg.build()).queue();
                return;
            }
        }

        String githubUrl = "https://github.com/" + user + "/" + repo;
        boolean cloneBool = runCommand(cloneDir, String.format("git clone %s .", githubUrl));
        if (!cloneBool) {
            pmg.setMessage("GitHubからのクローンに失敗しました。");
            pmg.set(ProgressMessageGenerator.Type.CLONE_REPOSITORY, ProgressMessageGenerator.Status.ERROR);
            message.editMessage(pmg.build()).queue();
            return;
        }

        // チェックアウト
        boolean checkoutBool = runCommand(cloneDir, String.format("git checkout %s", branch));
        if (!checkoutBool) {
            pmg.setMessage("ブランチチェックアウトに失敗しました。");
            pmg.set(ProgressMessageGenerator.Type.CLONE_REPOSITORY, ProgressMessageGenerator.Status.ERROR);
            message.editMessage(pmg.build()).queue();
            return;
        }
        pmg.set(ProgressMessageGenerator.Type.CLONE_REPOSITORY, ProgressMessageGenerator.Status.SUCCESSFUL);

        System.out.println("[INFO] BUILD");
        pmg.set(ProgressMessageGenerator.Type.BUILD, ProgressMessageGenerator.Status.RUNNING);
        message.editMessage(pmg.build()).queue();
        // ビルド
        boolean buildBool = runCommand(cloneDir, "mvn clean package");
        if (!buildBool) {
            pmg.setMessage("ビルドに失敗しました。");
            pmg.set(ProgressMessageGenerator.Type.BUILD, ProgressMessageGenerator.Status.ERROR);
            message.editMessage(pmg.build()).queue();
            return;
        }
        pmg.set(ProgressMessageGenerator.Type.BUILD, ProgressMessageGenerator.Status.SUCCESSFUL);

        System.out.println("[INFO] START_PROCESS");
        pmg.set(ProgressMessageGenerator.Type.START_PROCESS, ProgressMessageGenerator.Status.RUNNING);
        message.editMessage(pmg.build()).queue();
        // jarコピー
        File[] files = new File(cloneDir, "target").listFiles();
        if (files == null) {
            pmg.setMessage("targetディレクトリのファイルをリストアップできませんでした。");
            pmg.set(ProgressMessageGenerator.Type.START_PROCESS, ProgressMessageGenerator.Status.ERROR);
            message.editMessage(pmg.build()).queue();
            return;
        }
        Optional<File> file = Arrays.stream(files)
            .filter(_file -> _file.getName().endsWith(".jar")).max(Comparator.comparingLong(File::length));
        if (!file.isPresent()) {
            pmg.setMessage("jarファイルが見つかりませんでした。");
            pmg.set(ProgressMessageGenerator.Type.START_PROCESS, ProgressMessageGenerator.Status.ERROR);
            message.editMessage(pmg.build()).queue();
            return;
        }
        File from = file.get();
        pmg.setTypeMessage(ProgressMessageGenerator.Type.START_PROCESS, String.format("プラグインファイル: %s", from.getName()));
        message.editMessage(pmg.build()).queue();

        File to = new File("beta/", "Javajaotan2Beta.jar");
        if (to.exists() && !to.delete()) {
            pmg.setTypeMessage(ProgressMessageGenerator.Type.START_PROCESS, "古いjarファイルの削除に失敗");
            message.editMessage(pmg.build()).queue();
        }

        try {
            Files.copy(from.toPath(), to.toPath());
        } catch (IOException e) {
            pmg.setMessage("jarファイルのコピーに失敗しました。");
            pmg.set(ProgressMessageGenerator.Type.START_PROCESS, ProgressMessageGenerator.Status.ERROR);
            message.editMessage(pmg.build()).queue();
            return;
        }

        boolean startSystemd = runCommand(null, "systemctl start Javajaotan2Beta");
        if (!startSystemd) {
            pmg.setMessage("Javajaotan (Beta)の起動に失敗しました。");
            pmg.set(ProgressMessageGenerator.Type.START_PROCESS, ProgressMessageGenerator.Status.ERROR);
            message.editMessage(pmg.build()).queue();
            return;
        }
        pmg.set(ProgressMessageGenerator.Type.START_PROCESS, ProgressMessageGenerator.Status.SUCCESSFUL);
        message.editMessage(pmg.build()).queue();

        JSONObject data = new JSONObject();
        data.put("builder", member.getUser().getAsTag());
        data.put("builderId", member.getId());
        data.put("user", user);
        data.put("repo", repo);
        data.put("branch", branch);
        data.put("time", System.currentTimeMillis());
        try {
            Files.write(Paths.get("build.json"), Collections.singleton(data.toString()), Charset.defaultCharset());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static boolean runCommand(File currentDir, String command) {
        try {
            Process p;
            ProcessBuilder pb = new ProcessBuilder();
            pb.command(command.split(" "));
            if(currentDir != null) pb.directory(currentDir);
            pb.redirectErrorStream(true);
            p = pb.start();
            new Thread(() -> {
                InputStream is = p.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                while (true) {
                    String line = null;
                    try {
                        line = br.readLine();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if (line == null) {
                        break;
                    }
                    System.out.println(line);
                }
            }).start();

            boolean end = p.waitFor(10, TimeUnit.MINUTES);
            if (end) {
                return p.exitValue() == 0;
            } else {
                System.out.println("-> TIMEOUT");
                return false;
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Nullable
    JSONObject getRepo(String user, String repo) {
        try {
            String url = String.format("https://api.github.com/repos/%s/%s", user, repo);
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).get().build();
            JSONObject obj;
            try (Response response = client.newCall(request).execute()) {
                if (response.code() != 200) {
                    return null;
                }
                obj = new JSONObject(Objects.requireNonNull(response.body()).string());
            }
            return obj;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Nullable
    JSONObject getRepoBranch(String user, String repo, String branch) {
        try {
            String url = String.format("https://api.github.com/repos/%s/%s/branches/%s", user, repo, branch);
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).get().build();
            JSONObject obj;
            try (Response response = client.newCall(request).execute()) {
                if (response.code() != 200) {
                    return null;
                }
                obj = new JSONObject(Objects.requireNonNull(response.body()).string());
            }

            return obj;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
