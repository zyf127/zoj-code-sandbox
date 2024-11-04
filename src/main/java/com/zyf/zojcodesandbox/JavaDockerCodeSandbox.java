package com.zyf.zojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.zyf.zojcodesandbox.model.ExecuteCodeRequest;
import com.zyf.zojcodesandbox.model.ExecuteCodeResponse;
import com.zyf.zojcodesandbox.model.ExecuteMessage;
import com.zyf.zojcodesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.core.DockerClientBuilder;
import org.springframework.util.StopWatch;

@Slf4j
public class JavaDockerCodeSandbox implements CodeSandbox {

    private static final String USER_DIR = "user.dir";

    private static final String GLOBAL_CODE_DIR_NAME = "tempCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final String IMAGE_NAME = "openjdk:8-alpine";

    private static final String DOCKER_VOLUME = "/app";

    private static boolean fistInit = true;

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {

        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();

        // 1. 把用户的代码存放在相应的目录中（tempCode/UUID/Main.java）
        String userDir = System.getProperty(USER_DIR);
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);

        // 2. 编译Java代码，得到class文件
        String compileCmd = String.format("javac -encoding UTF-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process complieProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage compileMessage = ProcessUtils.runProcess(complieProcess);
            log.info("程序编译信息：" + compileMessage);
        } catch (IOException | InterruptedException e) {
            return getErrorResponse(e);
        }

        // 3. 使用docker运行Java代码
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        // 拉取镜像（第一次执行的时候）
        if (fistInit) {
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(IMAGE_NAME);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    log.info("拉取镜像：" + item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd.exec(pullImageResultCallback).awaitCompletion();
            } catch (InterruptedException e) {
                log.error("拉取镜像异常");
                return getErrorResponse(e);
            }
            log.info("拉取镜像完成");
            fistInit = false;
        }
        // 创建容器
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(100 * 1024 * 1024L);
        hostConfig.withCpuCount(1L);
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume(DOCKER_VOLUME)));
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(IMAGE_NAME);
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
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
                    .exec();;
            log.info("创建执行命令：" + execCreateCmdResponse);

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
            try {
                stopWatch.start();
                dockerClient
                        .execStartCmd(execId)
                        .exec(execStartResultCallback)
                        .awaitCompletion();
                stopWatch.stop();
                executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
            } catch (InterruptedException e) {
                log.error("程序执行异常");
                return getErrorResponse(e);
            }
            executeMessageList.add(executeMessage);
        }
        // 停止容器
        dockerClient.stopContainerCmd(containerId).exec();
        // 删除容器
        dockerClient.removeContainerCmd(containerId).exec();
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        System.out.println("========================" + executeMessageList);
        return null;
    }

    /**
     * 获取错误响应（错误处理方法）
     *
     * @param e
     * @return
     */
    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        // 2. 表示代码沙箱错误
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }


    public static void main(String[] args) {
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setInputList(Arrays.asList("1 2", "1 3"));
        executeCodeRequest.setLanguage("Java");

        JavaDockerCodeSandbox javaNativeCodeSandbox = new JavaDockerCodeSandbox();
        System.out.println(javaNativeCodeSandbox.executeCode(executeCodeRequest));
    }
}
