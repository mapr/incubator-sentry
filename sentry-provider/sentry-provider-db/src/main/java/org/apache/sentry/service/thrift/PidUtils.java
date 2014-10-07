/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.sentry.service.thrift;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Arrays;

public final class PidUtils {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(PidUtils.class);

    private PidUtils(){}
    private final static int RETRY_COUNT = 10;
    private final static long ONE_SECOND = 1000;
    private static final String MAPR_HOME_PATH_DEFAULT = "/opt/mapr";
    private static final String MAPR_PID_DEFAULT = "pid";
    private static final String SENTRY_PID = "sentry.pid";
    private static final String SENTRY_THRIFT_PID = "sentry-thrift.pid";

    public static void createSentryServicePidFile(){
      LOGGER.info("Creating sentry pid file...");
      File sentryPidFile = new File(MAPR_HOME_PATH_DEFAULT + "/" + MAPR_PID_DEFAULT + "/" + SENTRY_PID);
      if(sentryPidFile.exists()){
          removeFile(sentryPidFile);
      }
      int sentryServicePid = findSentryServicePid();
      try {
        FileWriter fw = new FileWriter(sentryPidFile);
        fw.write(Integer.toString(sentryServicePid));
        fw.close();
      } catch (IOException e) {
          LOGGER.error("IO Exception while trying to write to: " + sentryPidFile, e);
      }
      LOGGER.info("Sentry pid = " + sentryServicePid);
    }

    public static void createSentryThriftServicePidFile(int port){
      LOGGER.info("Creating sentry-thrift pid file...");
      File sentryThriftPidFile = new File(MAPR_HOME_PATH_DEFAULT + "/" + MAPR_PID_DEFAULT + "/" + SENTRY_THRIFT_PID);
      if(sentryThriftPidFile.exists()){
          removeFile(sentryThriftPidFile);
      }
      int sentryThriftServicePid = findSentryThriftServicePid(port);
      try {
        FileWriter fw = new FileWriter(sentryThriftPidFile);
        fw.write(Integer.toString(sentryThriftServicePid));
        fw.close();
      } catch (IOException e) {
        LOGGER.error("IO Exception while trying to write to: " + sentryThriftPidFile, e);
      }
      LOGGER.info("Sentry-thrift pid = " + sentryThriftServicePid);
    }

    private static int findSentryServicePid(){
      String [] commandArray = new String [] {"pgrep", "sentry"};
      int i = 0;
      int sentryServicePid;
      do {
        LOGGER.info("Attempt " + Integer.toString(i) + ". Getting PID for sentry.");
        sentryServicePid = execBashReturnInt(commandArray);
        i++;
        waitForTimeout(ONE_SECOND);
      }
      while (sentryServicePid < 0 && i < RETRY_COUNT);
      return sentryServicePid;
    }

    private static int findSentryThriftServicePid(int port){
      String [] commandArray = new String [] {"/bin/bash","-c","netstat -nltp | grep " + Integer.toString(port) +
              "|awk -F \" \" '{print $7}'|awk -F \"/\" '{print $1}'"};
      int i = 0;
      int sentryThriftServicePid;
      do {
        LOGGER.info("Attempt " + Integer.toString(i) + ". Getting PID for sentry-thrift.");
        sentryThriftServicePid = execBashReturnInt(commandArray);
        i++;
        waitForTimeout(ONE_SECOND);
      }
      while (sentryThriftServicePid < 0 && i <= RETRY_COUNT);
      return sentryThriftServicePid;
    }

    private static int execBashReturnInt(String[] cmdarray){
      try {
        Process process = Runtime.getRuntime().exec(cmdarray);
        BufferedInputStream is = new BufferedInputStream(process.getInputStream());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte [] buf = new byte[1024];
        int retValue = -1;
        int len;
        while ((len = is.read(buf)) != -1) {
            baos.write(buf, 0, len);
          }
          try {
            retValue = process.waitFor();
            is.close();
          } catch (InterruptedException e) {
            LOGGER.error("Interrupted Exception during command: " + Arrays.asList(cmdarray) + " execution");
            is.close();
            process.destroy();
            Thread.currentThread().interrupt();
            return -1;
          }
          process.destroy();
          if (retValue == 0) {
              // parse out output
            String output = baos.toString();
            String[] pidStrs = output.split("\\s+");
            if ( pidStrs.length > 0 ) {
              for ( String pidStr : pidStrs ) {
                try {
                  return Integer.valueOf(pidStr);
                    } catch(NumberFormatException nfe) {
                      LOGGER.error("PID is not integer: " + pidStr);
                    }
              }
            }
          }
      } catch (IOException e) {
          LOGGER.error("IOException while trying to get PID.", e);
      }
      return -1;
    }

    private static void waitForTimeout(long timeout){
      try {
          Thread.sleep(timeout);
      } catch(InterruptedException ex) {
          Thread.currentThread().interrupt();
      }
    }

    private static void removeFile(File file){
      try{
          if(file.delete()){
            LOGGER.info("Existing " + file.getName() + " is deleted!");
          }else{
            LOGGER.info("Delete operation is failed for file " + file.getCanonicalPath());
          }
      }catch(Exception e){
        LOGGER.error("Error deleting pid file.", e);
      }
    }
}
