package com.zyf.zojcodesandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.zyf.zojcodesandbox.model.ExecuteMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate {

    private static final String IMAGE_NAME = "openjdk:8-alpine";

    private static final String DOCKER_VOLUME = "/app";

    private static final long TIME_OUT = 5 * 1000L;

    private static final double MEMORY_UNIT = 1024.0 * 1024.0;

    @Override
    protected List<ExecuteMessage> runFile(List<String> inputList, File userCodeFile) {
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        // 拉取镜像（如果镜像不存在的话）
        List<Image> imageList = dockerClient.listImagesCmd().exec();
        boolean isImageExists = imageList.stream().anyMatch(image -> image.getRepoTags() != null && Arrays.asList(image.getRepoTags()).contains(IMAGE_NAME));
        if (!isImageExists) {
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(IMAGE_NAME);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd.exec(pullImageResultCallback).awaitCompletion();
            } catch (InterruptedException e) {
                log.error("拉取镜像异常", e);
                throw new RuntimeException("程序运行异常", e);
            }
        }
        // 创建容器
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(100 * 1024 * 1024L);
        hostConfig.withMemorySwap(0L);
        hostConfig.withCpuCount(1L);
        hostConfig.setBinds(new Bind(userCodeFile.getParentFile().getAbsolutePath(), new Volume(DOCKER_VOLUME)));
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(IMAGE_NAME);
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)
                .withReadonlyRootfs(true)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withTty(true)
                .exec();
        String containerId = createContainerResponse.getId();
        // 启动容器
        dockerClient.startContainerCmd(containerId).exec();
        // 在容器中运行Java代码
        // 执行docker命令：docker exec -it heuristic_burnell /bin/sh -c "java -cp /app Main < /app/input.txt"
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (int i = 0; i < inputList.size(); i++) {
            StopWatch stopWatch = new StopWatch();
            String inputPath = DOCKER_VOLUME + "/input" + (i + 1) + ".txt";
            String javaCmd = "java -cp " + DOCKER_VOLUME + " Main < " + inputPath;
            String[] cmdArray = new String[]{"/bin/sh", "-c", javaCmd};
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();

            String execId = execCreateCmdResponse.getId();
            ExecuteMessage executeMessage = new ExecuteMessage();
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    if (StreamType.STDERR.equals(streamType)) {
                        executeMessage.setErrorMessage(new String(frame.getPayload()));
                    } else {
                        String message = new String(frame.getPayload());
                        String handleMessage = message.replace("\n", " ").trim();
                        executeMessage.setMessage(handleMessage);
                    }
                    super.onNext(frame);
                }
            };
            // 获取占用内存
            final double[] maxMemoryArray = {0.0};
            ResultCallback<Statistics> statisticsResultCallback = new ResultCallback<Statistics>() {
                @Override
                public void onNext(Statistics statistics) {
                    maxMemoryArray[0] = Math.max(statistics.getMemoryStats().getUsage() / MEMORY_UNIT, maxMemoryArray[0]);
                }

                @Override
                public void onStart(Closeable closeable) {}

                @Override
                public void onError(Throwable throwable) {}

                @Override
                public void onComplete() {}

                @Override
                public void close() throws IOException {}
            };
            dockerClient.statsCmd(containerId).exec(statisticsResultCallback);
            try {
                // 开始执行命令
                stopWatch.start();
                dockerClient
                        .execStartCmd(execId)
                        .exec(execStartResultCallback)
                        .awaitCompletion(TIME_OUT, TimeUnit.MILLISECONDS);
                stopWatch.stop();
                executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
                executeMessage.setMemory(maxMemoryArray[0]);
            } catch (InterruptedException e) {
                log.error("程序运行异常：" + e);
                throw new RuntimeException("程序运行异常", e);
            }
            executeMessageList.add(executeMessage);
        }
        // 停止容器
        dockerClient.stopContainerCmd(containerId).exec();
        // 删除容器
        dockerClient.removeContainerCmd(containerId).exec();
        return executeMessageList;
    }
}
