package com.zyf.zojcodesandbox;

import com.zyf.zojcodesandbox.model.ExecuteMessage;
import com.zyf.zojcodesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class JavaNativeCodeSandbox extends JavaCodeSandboxTemplate {
    private  static final long TIME_OUT = 5 * 1000L;

    @Override
    protected List<ExecuteMessage> runFile(List<String> inputList, File userCodeFile) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {
            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, inputArgs);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                // 超时控制
                new Thread(() -> {
                    try {
                        Thread.sleep(TIME_OUT);
                        if (runProcess.isAlive()) {
                            runProcess.destroy();
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
                ExecuteMessage runMessage = ProcessUtils.runProcess(runProcess);
                executeMessageList.add(runMessage);
            } catch (IOException | InterruptedException e) {
                log.error("程序运行异常：" + e);
                throw new RuntimeException("程序运行异常", e);
            }
        }
        return executeMessageList;
    }
}
