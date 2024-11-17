package com.zyf.zojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.zyf.zojcodesandbox.model.ExecuteCodeRequest;
import com.zyf.zojcodesandbox.model.ExecuteCodeResponse;
import com.zyf.zojcodesandbox.model.ExecuteMessage;
import com.zyf.zojcodesandbox.model.JudgeInfo;
import com.zyf.zojcodesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
public abstract class JavaCodeSandboxTemplate implements CodeSandbox {
    private static final String USER_DIR = "user.dir";

    private static final String GLOBAL_CODE_DIR_NAME = "tempCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {

        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();

        // 1. 把用户代码和输入用例保存为文件
        File userCodeFile = saveCodeAndInputCaseToFile(code, inputList);

        // 2. 编译.java文件，得到.class文件
        ExecuteMessage compileMessage = compileFile(userCodeFile);

        // 3. 运行.class文件，得到输出结果
        List<ExecuteMessage> executeMessageList = runFile(inputList, userCodeFile);

        // 4. 整理输出结果
        ExecuteCodeResponse executeCodeResponse = getOutputResponse(executeMessageList);

        // 5. 清理文件
        boolean isClean = cleanFile(userCodeFile);
        if (!isClean) {
            log.error("cleanFile error, userCodeFilePath = {}", userCodeFile.getAbsolutePath());
        }
        return executeCodeResponse;
    }

    /**
     * 1. 把用户的代码和输入用例保存为文件
     *
     * @param code
     * @return
     */
    protected File saveCodeAndInputCaseToFile(String code, List<String> inputList) {

        String userDir = System.getProperty(USER_DIR);
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        // 保存每个输入用例到文件中（1个输入用例对应1个文件）
        for (int i = 0; i < inputList.size(); i++) {
            String inputCasePath = userCodeParentPath + File.separator + "input" + (i + 1) + ".txt";
            File inputCaseFile = FileUtil.newFile(inputCasePath);
            try {
                PrintStream printStream = new PrintStream(inputCaseFile);
                String inputCase = inputList.get(i);
                String[] inputCaseArgsArray = inputCase.split(" ");
                for (String inputCaseArgs : inputCaseArgsArray) {
                    printStream.println(inputCaseArgs);
                }
                printStream.close();
            } catch (FileNotFoundException e) {
                log.error("输入用例写入文件异常：", e);
                throw new RuntimeException("输入用例写入文件异常", e);
            }
        }
        return userCodeFile;
    }

    /**
     * 2. 编译.java文件，得到.class文件
     *
     * @param userCodeFile
     * @return
     */
    protected ExecuteMessage compileFile(File userCodeFile) {
        String compileCmd = String.format("javac -encoding UTF-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process complieProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage compileMessage = ProcessUtils.runProcess(complieProcess);
            return compileMessage;
        } catch (IOException | InterruptedException e) {
            log.error("程序编译异常：", e);
            throw new RuntimeException("程序编译异常", e);
        }
    }

    /**
     * 3. 运行.class文件，得到输出结果
     *
     * @param inputList
     * @param userCodeFile
     * @return
     */
    protected abstract List<ExecuteMessage> runFile(List<String> inputList, File userCodeFile);

    /**
     * 4. 整理输出结果
     *
     * @param executeMessageList
     * @return
     */
    protected ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessageList) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        long maxTime = 0L;
        double maxMemory = 0.0;
        for (ExecuteMessage executeMessage : executeMessageList) {
            if (StrUtil.isNotBlank(executeMessage.getErrorMessage())) {
                executeCodeResponse.setMessage(executeMessage.getErrorMessage());
                executeCodeResponse.setStatus(3);
                break;
            }
            outputList.add(executeMessage.getMessage());
            Long time = executeMessage.getTime();
            Double memory = executeMessage.getMemory();
            if (time != null) {
                maxTime = Math.max(maxTime, time);
            }
            if (memory != null) {
                maxMemory = Math.max(maxMemory, memory);
            }
        }
        executeCodeResponse.setOutputList(outputList);
        if (executeMessageList.size() == outputList.size()) {
            executeCodeResponse.setStatus(1);
        }
        // 设置运行时间
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(maxTime);
        judgeInfo.setMemory(maxMemory);
        executeCodeResponse.setJudgeInfo(judgeInfo);
        return executeCodeResponse;
    }

    /**
     * 5. 清理文件
     *
     * @param userCodeFile
     * @return
     */
    protected boolean cleanFile(File userCodeFile) {
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeFile.getParentFile());
            return del;
        }
        return true;
    }

    /**
     * 6. 获取错误响应（错误处理方法）
     *
     * @param e
     * @return
     */
    protected ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        // 2：表示代码沙箱错误
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }

}
