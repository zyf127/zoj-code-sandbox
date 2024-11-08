package com.zyf.zojcodesandbox;

import cn.hutool.core.util.ArrayUtil;
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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
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
        // 执行docker命令：docker exec optimistic_neumann java -cp /app Main 1 3
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {
            StopWatch stopWatch = new StopWatch();
            String[] inputArgsArray = inputArgs.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", DOCKER_VOLUME, "Main"}, inputArgsArray);
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
                        executeMessage.setMessage(new String(frame.getPayload()));
                    }
                    super.onNext(frame);
                }
            };
            // 获取占用内存
            final long[] maxMemoryArray = {0L};
            ResultCallback<Statistics> statisticsResultCallback = new ResultCallback<Statistics>() {
                @Override
                public void onNext(Statistics statistics) {
                    maxMemoryArray[0] = Math.max(statistics.getMemoryStats().getUsage(), maxMemoryArray[0]);
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
