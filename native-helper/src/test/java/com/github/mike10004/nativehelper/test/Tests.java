package com.github.mike10004.nativehelper.test;

import com.github.mike10004.nativehelper.Program;
import com.github.mike10004.nativehelper.ProgramWithOutputStringsResult;
import com.github.mike10004.nativehelper.test.Poller.PollOutcome;
import com.github.mike10004.nativehelper.test.Poller.StopReason;
import com.google.common.base.Suppliers;
import com.google.common.io.Files;
import org.junit.Test;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkState;

public class Tests {

    private Tests() {}

    public static File getTestSourcesDir() {
        return new File(getProperties().getProperty("project.basedir"), "src/test");
    }

    public static File getPythonTestSourcesDir() {
        File f = new File(getTestSourcesDir(), "python");
        checkState(f.isDirectory(), "not a directory: %s", f);
        return f;
    }

    public static File getPythonFile(String relativePath) {
        return getPythonTestSourcesDir().toPath().resolve(relativePath).toFile();
    }

    private static final Supplier<Properties> propertiesSupplier = Suppliers.memoize(() -> {
        Properties p = new Properties();
        String resourcePath = "/test.properties";
        try (InputStream in = Tests.class.getResourceAsStream(resourcePath)) {
            checkState(in != null, "not found: classpath:" + resourcePath);
            p.load(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        checkState(!p.isEmpty(), "no properties loaded");
        return p;
    });

    public static Properties getProperties() {
        return propertiesSupplier.get();
    }

    public static File pyReadInput() {
        return getPythonFile("nht_read_input.py");
    }

    public static String joinPlus(String delimiter, Iterable<String> items) {
        return String.join(delimiter, items) + delimiter;
    }

    public static File pyCat() {
        return getPythonFile("nht_cat.py");
    }

    public static String readWhenNonempty(File file) throws InterruptedException {
        PollOutcome<String> outcome = new Poller<String>() {

            @Override
            protected PollAnswer<String> check(int pollAttemptsSoFar) {
                if (file.length() > 0) {
                    try {
                        return resolve(Files.asCharSource(file, StandardCharsets.US_ASCII).read());
                    } catch (IOException e) {
                        e.printStackTrace(System.err);
                        return abortPolling();
                    }
                }
                return continuePolling();
            }
        }.poll(250, 20);
        if (outcome.reason == StopReason.RESOLVED) {
            return outcome.content;
        }
        throw new IllegalStateException("polling for nonempty file failed: " + file);
    }

    public static CatProcState getProcessState(int pid) throws IOException {
        String stat;
        try {
            stat = Files.asCharSource(new File("/proc/" + pid + "/stat"), StandardCharsets.UTF_8).read();
        } catch (FileNotFoundException ignore) {
            return CatProcState.NOT_FOUND;
        }
        Matcher m = Pattern.compile("^\\S+\\s+\\(.+\\)\\s+(\\S+)\\s+.*$").matcher(stat);
        checkState(m.find(), "does not match pattern: %s", stat);
        return CatProcState.fromStatChar(m.group(1));
    }

    public static TestProcessState killIfRunning(int pid) throws IOException {
        CatProcState state = getProcessState(pid);
        if (state == CatProcState.NOT_FOUND) {
            return TestProcessState.WAS_NOT_RUNNING;
        }
        System.out.format("killing %s %s%n", pid, state);
        ProgramWithOutputStringsResult result = Program.running("kill")
                .arg("-9")
                .arg(String.valueOf(pid))
                .outputToStrings()
                .execute();
        if (result.getExitCode() != 0) {
            if (result.getExitCode() == 1 && result.getStderrString().contains("No such process")) {
                return TestProcessState.WAS_NOT_RUNNING;
            }
            System.out.format("kill -9 exit code %s%n", result.getExitCode());
            System.err.println(result.getStderrString());
            throw new IllegalStateException("kill failed: " + result);
        }
        state = getProcessState(pid);
        if (state != CatProcState.NOT_FOUND) {
            throw new IllegalStateException("pid " + pid + " still running: " + state);
        }
        return TestProcessState.WAS_RUNNING;
    }

    public enum CatProcState {
        NOT_FOUND,
        RUNNING,
        SLEEPING,
        UNINTERRUPTIBLE_WAIT,
        ZOMBIE,
        TRACED_OR_STOPPED;

        public static CatProcState fromStatChar(@Nullable String statChar) {
            if (statChar == null) {
                return NOT_FOUND;
            }
            switch (statChar) {
                case "R": return RUNNING;
                case "S": return SLEEPING;
                case "D": return UNINTERRUPTIBLE_WAIT;
                case "T": return TRACED_OR_STOPPED;
                case "Z": return ZOMBIE;
            }
            throw new IllegalArgumentException("stat char " + statChar);
        }
    }

    public enum TestProcessState {
        WAS_RUNNING,
        WAS_NOT_RUNNING
    }

    public interface CheckedSupplier<T> {
        T get() throws IOException;
    }

    public static class ProcessToBeKilled implements java.io.Closeable {

        private final CheckedSupplier<Integer> pidProvider;
        private final PidFailureReaction reaction;

        public ProcessToBeKilled(File pidFile) {
            this(pidFile, PidFailureReaction.RAISE);
        }

        public ProcessToBeKilled(File pidFile, PidFailureReaction reaction) {
            this(() -> {
                String pidStr = Files.asCharSource(pidFile, StandardCharsets.US_ASCII).read().trim();
                return pidStr.isEmpty() ? null : Integer.parseInt(pidStr);
            }, reaction);
        }

        public ProcessToBeKilled(CheckedSupplier<Integer> pidProvider, PidFailureReaction reaction) {
            this.pidProvider = pidProvider;
            this.reaction = reaction;
        }

        public enum PidFailureReaction {
            IGNORE, RAISE
        }

        @Override
        public void close() throws IOException {
            @Nullable Integer pid = pidProvider.get();
            if (pid == null) {
                if (PidFailureReaction.RAISE == reaction) {
                    throw new IllegalStateException("failed to get pid");
                }
                return;
            }
            TestProcessState state = killIfRunning(pid);
            if (state == TestProcessState.WAS_RUNNING) {
                System.out.format("(process %s)%n", state.name().toLowerCase());
            }
        }
    }
}
