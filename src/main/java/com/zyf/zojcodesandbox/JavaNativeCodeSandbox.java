package com.zyf.zojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;
import com.zyf.zojcodesandbox.model.ExecuteCodeRequest;
import com.zyf.zojcodesandbox.model.ExecuteCodeResponse;
import com.zyf.zojcodesandbox.model.ExecuteMessage;
import com.zyf.zojcodesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
public class JavaNativeCodeSandbox implements CodeSandbox {

    private static final String USER_DIR = "user.dir";

    private static final String GLOBAL_CODE_DIR_NAME = "tempCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final String SECURITY_MANAGER_CLASS_NAME = "MySecurityManager";

    private static final String SECURITY_MANAGER_PATH = "D:\\JavaWork\\projects\\zoj\\zoj-code-sandbox\\src\\main\\resources\\security";

    private static final long TIME_OUT = 5 * 1000L;

    private static final List<String> blackList = Arrays.asList("Files", "exec");

    private static final WordTree WORD_TREE = new WordTree();

    static {
        // 字典树中添加禁止词
        WORD_TREE.addWords(blackList);
    }


    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {

        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();

        // 校验代码中是否包含黑名单中的命令
        FoundWord foundWord = WORD_TREE.matchWord(code);
        if (foundWord != null) {
            log.info("包含禁止词：" + foundWord);
            return null;
        }

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

        // 3. 运行代码，得到输出结果
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {
            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s;%s -Djava.security.manager=%s Main %s", userCodeParentPath, SECURITY_MANAGER_PATH, SECURITY_MANAGER_CLASS_NAME, inputArgs);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                // 超时控制
                new Thread(() -> {
                    try {
                        Thread.sleep(TIME_OUT);
                        if (runProcess.isAlive()) {
                            runProcess.destroy();
                            log.info("超时了，中断");
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
                ExecuteMessage runMessage = ProcessUtils.runProcess(runProcess);
                executeMessageList.add(runMessage);
                log.info("程序运行信息：" + runMessage);
            } catch (IOException | InterruptedException e) {
                return getErrorResponse(e);
            }
        }

        // 4. 整理输出结果
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        long maxTime = 0L;
        for (ExecuteMessage executeMessage : executeMessageList) {
            if (StrUtil.isBlank(executeMessage.getErrorMessage())) {
                executeCodeResponse.setMessage(executeMessage.getErrorMessage());
                executeCodeResponse.setStatus(3);
                break;
            }
            outputList.add(executeMessage.getMessage());
            Long time = executeMessage.getTime();
            if (time != null) {
                maxTime = Math.max(maxTime, time);
            }
        }
        executeCodeResponse.setOutputList(outputList);
        if (executeMessageList.size() == outputList.size()) {
            executeCodeResponse.setStatus(1);
        }
        // 设置运行时间、运行内存（运行内存在Java原生开发中不好获取，采用另一种方式获取）
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(maxTime);
        executeCodeResponse.setJudgeInfo(judgeInfo);

        // 5. 清理文件
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeFile.getParentFile());
            log.info("删除" + (del ? "成功" : "失败"));
        }
        return executeCodeResponse;
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
        executeCodeRequest.setInputList(Arrays.asList("1 2"));
        executeCodeRequest.setLanguage("Java");

        JavaNativeCodeSandbox javaNativeCodeSandbox = new JavaNativeCodeSandbox();
        System.out.println(javaNativeCodeSandbox.executeCode(executeCodeRequest));
    }
}
