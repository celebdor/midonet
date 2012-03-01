/*
 * Copyright 2012 Midokura Pte. Ltd.
 */
package com.midokura.util.process;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Mihai Claudiu Toader <mtoader@midokura.com>
 *         Date: 11/20/11
 */
public class ProcessOutputDrainer {

    private Process process;
    private boolean separateErrorStream;

    public ProcessOutputDrainer(Process process) {
        this(process, false);
    }

    public ProcessOutputDrainer(Process process, boolean separateErrorStream) {
        this.process = process;
        this.separateErrorStream = separateErrorStream;
    }

    public void drainOutput(DrainTarget drainTarget) {
        drainOutput(drainTarget, true);
    }

    public void drainOutput(DrainTarget drainTarget, boolean wait) {

        Thread stdoutThread = new Thread(
            new InputStreamDrainer(process.getInputStream(), drainTarget,
                                   true));
        stdoutThread.start();

        Thread stderrThread = null;
        if (separateErrorStream) {
            stderrThread = new Thread(
                new InputStreamDrainer(process.getErrorStream(), drainTarget,
                                       false));
            stderrThread.start();
        }
        if (wait) {
            try {
                stdoutThread.join();
                if (stderrThread != null) {
                    stderrThread.join();
                }
            } catch (InterruptedException e) {
                //
            }
        }
    }

    public interface DrainTarget {

        public void outLine(String line);

        public void errLine(String line);

    }

    private class InputStreamDrainer implements Runnable {

        private InputStream inputStream;
        private DrainTarget drainTarget;
        private boolean stdoutOrStderr;

        public InputStreamDrainer(InputStream inputStream,
                                  DrainTarget drainTarget,
                                  boolean stdoutOrStderr) {
            this.inputStream = inputStream;
            this.drainTarget = drainTarget;
            this.stdoutOrStderr = stdoutOrStderr;
        }

        @Override
        public void run() {
            try {
                LineIterator lineIterator = IOUtils.lineIterator(inputStream,
                                                                 "UTF-8");
                while (lineIterator.hasNext()) {
                    String line = lineIterator.nextLine();

                    if (stdoutOrStderr) {
                        drainTarget.outLine(line);
                    } else {
                        drainTarget.errLine(line);
                    }
                }
            } catch (IllegalStateException ex) {
                Throwable cause = ex.getCause();
                if ( cause != null
                    && cause instanceof IOException
                    && cause.getMessage().equals("Stream closed") ) {
                    // we ignore an IllegalStateException caused by a stream close
                    // because this usually happens when the watched process is
                    // destroyed forcibly (and we tend to that.
                } else {
                    throw ex;
                }
            } catch (IOException e) {
                // catch and ignore the output. Normally this happens when the
                // reading input stream (which is connected to a process) is
                // closed which usually means that the process died or it was
                // killed. So we bail the loop and end the draining thread.
            }
        }
    }
}
