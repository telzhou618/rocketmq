/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.rocketmq.srvutil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.common.ServiceThread;
import org.apache.rocketmq.common.UtilAll;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.logging.InternalLoggerFactory;

public class FileWatchService extends ServiceThread {
    private static final InternalLogger log = InternalLoggerFactory.getLogger(LoggerName.COMMON_LOGGER_NAME);

    private final List<String> watchFiles;
    private final List<String> fileCurrentHash;
    private final Listener listener;
    private static final int WATCH_INTERVAL = 500;
    private MessageDigest md = MessageDigest.getInstance("MD5");

    public FileWatchService(final String[] watchFiles,
        final Listener listener) throws Exception {
        this.listener = listener;
        this.watchFiles = new ArrayList<>();
        this.fileCurrentHash = new ArrayList<>();

        for (int i = 0; i < watchFiles.length; i++) {
            if (StringUtils.isNotEmpty(watchFiles[i]) && new File(watchFiles[i]).exists()) {
                this.watchFiles.add(watchFiles[i]);
                this.fileCurrentHash.add(hash(watchFiles[i]));
            }
        }
    }

    @Override
    public String getServiceName() {
        return "FileWatchService";
    }

    /**
     * 开启一个新的线程，循环遍历所有的文件路径，检查是否有更新。
     */
    @Override
    public void run() {
        log.info(this.getServiceName() + " service started");

        while (!this.isStopped()) {
            try {
                // 让线程等等一段时间
                this.waitForRunning(WATCH_INTERVAL);

                for (int i = 0; i < watchFiles.size(); i++) {
                    String newHash;
                    try {
                        // 计算新文件的md5
                        newHash = hash(watchFiles.get(i));
                    } catch (Exception ignored) {
                        log.warn(this.getServiceName() + " service has exception when calculate the file hash. ", ignored);
                        continue;
                    }
                    // 如果文件新的md5值和旧的不相等说明文件有变化，执行更新
                    if (!newHash.equals(fileCurrentHash.get(i))) {
                        fileCurrentHash.set(i, newHash);
                        listener.onChanged(watchFiles.get(i));
                    }
                }
            } catch (Exception e) {
                log.warn(this.getServiceName() + " service has exception. ", e);
            }
        }
        log.info(this.getServiceName() + " service end");
    }

    /**
     * 根据文件绝对路径获取文件的md5值
     * @param filePath 文件绝对路径
     * @return
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    private String hash(String filePath) throws IOException, NoSuchAlgorithmException {
        Path path = Paths.get(filePath);
        md.update(Files.readAllBytes(path));
        byte[] hash = md.digest();
        return UtilAll.bytes2string(hash);
    }

    /**
     * 文件有变化时，执行该事件
     */
    public interface Listener {
        /**
         * Will be called when the target files are changed
         * @param path the changed file path
         */
        void onChanged(String path);
    }
}
