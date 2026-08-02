package com.termux.terminal;

/**
 * Native methods for creating and managing pseudoterminal subprocesses. C code is in jni/termux.c.
 */
final class JNI {

    private static boolean sLoaded = false;

    static {
        try {
            System.loadLibrary("termux");
            sLoaded = true;
        } catch (Throwable t) {
            sLoaded = false;
        }
    }

    /**
     * Create a subprocess. Differs from {@link ProcessBuilder} in that a pseudoterminal is used to communicate with the
     * subprocess.
     * <p/>
     * Callers are responsible for calling {@link #close(int)} on the returned file descriptor.
     *
     * @param cmd       The command to execute
     * @param cwd       The current working directory for the executed command
     * @param args      An array of arguments to the command
     * @param envVars   An array of strings of the form "VAR=value" to be added to the environment of the process
     * @param processId A one-element array to which the process ID of the started process will be written.
     * @return the file descriptor resulting from opening /dev/ptmx master device. The sub process will have opened the
     * slave device counterpart (/dev/pts/$N) and have it as stdint, stdout and stderr.
     */
    public static int createSubprocessSafe(String cmd, String cwd, String[] args, String[] envVars, int[] processId, int rows, int columns, int cellWidth, int cellHeight) {
        if (sLoaded) {
            try {
                return createSubprocess(cmd, cwd, args, envVars, processId, rows, columns, cellWidth, cellHeight);
            } catch (Throwable t) {
                // Fallback if native call fails
            }
        }
        return createSubprocessFallback(cmd, cwd, args, envVars, processId);
    }

    /** Set the window size for a given pty, which allows connected programs to learn how large their screen is. */
    public static void setPtyWindowSizeSafe(int fd, int rows, int cols, int cellWidth, int cellHeight) {
        if (sLoaded && fd >= 0) {
            try {
                setPtyWindowSize(fd, rows, cols, cellWidth, cellHeight);
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Causes the calling thread to wait for the process associated with the receiver to finish executing.
     *
     * @return if >= 0, the exit status of the process. If < 0, the signal causing the process to stop negated.
     */
    public static int waitForSafe(int processId) {
        if (sLoaded && processId > 0) {
            try {
                return waitFor(processId);
            } catch (Throwable t) {
                // Fallback if native wait fails
            }
        }
        return waitForFallback(processId);
    }

    /** Close a file descriptor through the close(2) system call. */
    public static void closeSafe(int fileDescriptor) {
        if (sLoaded && fileDescriptor >= 0) {
            try {
                close(fileDescriptor);
                return;
            } catch (Throwable ignored) {}
        }
        if (fileDescriptor >= 0) {
            try {
                android.system.Os.close(wrapFd(fileDescriptor));
            } catch (Throwable ignored) {}
        }
    }

    private static native int createSubprocess(String cmd, String cwd, String[] args, String[] envVars, int[] processId, int rows, int columns, int cellWidth, int cellHeight);

    private static native void setPtyWindowSize(int fd, int rows, int cols, int cellWidth, int cellHeight);

    private static native int waitFor(int processId);

    private static native void close(int fileDescriptor);

    private static final java.util.Map<Integer, Process> sProcesses = new java.util.concurrent.ConcurrentHashMap<>();

    private static int createSubprocessFallback(String cmd, String cwd, String[] args, String[] envVars, int[] processId) {
        try {
            String execCmd = cmd != null ? cmd : "/system/bin/sh";
            if (!new java.io.File(execCmd).exists()) {
                execCmd = "/system/bin/sh";
            }
            java.util.List<String> commandList = new java.util.ArrayList<>();
            commandList.add(execCmd);
            if (args != null && args.length > 1) {
                for (int i = 1; i < args.length; i++) {
                    if (args[i] != null && !args[i].isEmpty()) {
                        commandList.add(args[i]);
                    }
                }
            }
            if (commandList.size() == 1 && (execCmd.endsWith("/bash") || execCmd.endsWith("/sh") || execCmd.endsWith("/zsh"))) {
                commandList.add("-i");
            }
            ProcessBuilder pb = new ProcessBuilder(commandList);
            pb.redirectErrorStream(true);

            pb.environment().put("PATH", "/data/data/com.termux/files/usr/bin:/system/bin:/system/xbin");
            pb.environment().put("LD_LIBRARY_PATH", "/data/data/com.termux/files/usr/lib");
            pb.environment().put("HOME", "/data/data/com.termux/files/home");
            pb.environment().put("PREFIX", "/data/data/com.termux/files/usr");
            pb.environment().put("TERM", "xterm-256color");

            if (cwd != null && !cwd.isEmpty()) {
                java.io.File dir = new java.io.File(cwd);
                if (dir.exists()) pb.directory(dir);
            }
            if (envVars != null) {
                for (String envVar : envVars) {
                    int idx = envVar.indexOf('=');
                    if (idx > 0) {
                        pb.environment().put(envVar.substring(0, idx), envVar.substring(idx + 1));
                    }
                }
            }
            Process p = pb.start();
            int pid = getPidReflection(p);
            if (processId != null && processId.length > 0) {
                processId[0] = pid;
            }
            if (pid > 0) {
                sProcesses.put(pid, p);
            }

            java.io.FileDescriptor fd0 = new java.io.FileDescriptor();
            java.io.FileDescriptor fd1 = new java.io.FileDescriptor();
            android.system.Os.socketpair(android.system.OsConstants.AF_UNIX, android.system.OsConstants.SOCK_STREAM, 0, fd0, fd1);

            int masterFd = getFdFromObj(fd0);

            java.io.FileDescriptor fd1ForWrite = android.system.Os.dup(fd1);
            java.io.FileDescriptor fd1ForRead = android.system.Os.dup(fd1);

            Thread t1 = new Thread(() -> {
                try (java.io.InputStream in = p.getInputStream();
                     java.io.FileOutputStream out = new java.io.FileOutputStream(fd1ForWrite)) {
                    byte[] buf = new byte[4096];
                    int len;
                    while ((len = in.read(buf)) != -1) {
                        out.write(buf, 0, len);
                        out.flush();
                    }
                } catch (Throwable ignored) {}
            });
            t1.setDaemon(true);
            t1.start();

            Thread t2 = new Thread(() -> {
                try (java.io.FileInputStream in = new java.io.FileInputStream(fd1ForRead);
                     java.io.OutputStream out = p.getOutputStream()) {
                    byte[] buf = new byte[4096];
                    int len;
                    while ((len = in.read(buf)) != -1) {
                        out.write(buf, 0, len);
                        out.flush();
                    }
                } catch (Throwable ignored) {}
            });
            t2.setDaemon(true);
            t2.start();

            return masterFd;
        } catch (Throwable t) {
            if (processId != null && processId.length > 0) processId[0] = -1;
            return -1;
        }
    }

    private static int waitForFallback(int processId) {
        Process p = sProcesses.remove(processId);
        if (p != null) {
            try {
                return p.waitFor();
            } catch (InterruptedException e) {
                return -1;
            }
        }
        return -1;
    }

    private static int getPidReflection(Process p) {
        try {
            java.lang.reflect.Field f = p.getClass().getDeclaredField("pid");
            f.setAccessible(true);
            return f.getInt(p);
        } catch (Throwable t) {
            return p.hashCode();
        }
    }

    private static int getFdFromObj(java.io.FileDescriptor fdObj) {
        if (fdObj == null) return -1;
        try {
            java.lang.reflect.Field field = java.io.FileDescriptor.class.getDeclaredField("descriptor");
            field.setAccessible(true);
            return field.getInt(fdObj);
        } catch (Throwable t) {
            try {
                java.lang.reflect.Field field = java.io.FileDescriptor.class.getDeclaredField("fd");
                field.setAccessible(true);
                return field.getInt(fdObj);
            } catch (Throwable ignored) {}
        }
        return -1;
    }

    private static java.io.FileDescriptor wrapFd(int fd) {
        java.io.FileDescriptor f = new java.io.FileDescriptor();
        try {
            java.lang.reflect.Field field = java.io.FileDescriptor.class.getDeclaredField("descriptor");
            field.setAccessible(true);
            field.setInt(f, fd);
        } catch (Throwable t) {
            try {
                java.lang.reflect.Field field = java.io.FileDescriptor.class.getDeclaredField("fd");
                field.setAccessible(true);
                field.setInt(f, fd);
            } catch (Throwable ignored) {}
        }
        return f;
    }
}