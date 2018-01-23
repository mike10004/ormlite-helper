package com.github.mike10004.nativehelper.subprocess;

import com.github.mike10004.nativehelper.subprocess.AbstractDestroyAttempt.ProcessWaiter;
import com.github.mike10004.nativehelper.subprocess.DestroyAttempt.KillAttempt;
import com.github.mike10004.nativehelper.subprocess.DestroyAttempt.TermAttempt;
import com.github.mike10004.nativehelper.subprocess.DestroyAttempts.KillAttemptImpl;
import com.github.mike10004.nativehelper.subprocess.DestroyAttempts.TermAttemptImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

class BasicProcessDestructor implements ProcessDestructor {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(BasicProcessDestructor.class);

    private final Process process;
    private final ProcessTracker processTracker;

    public BasicProcessDestructor(Process process, ProcessTracker processTracker) {
        this.process = requireNonNull(process);
        this.processTracker = requireNonNull(processTracker);
    }

    @Override
    public TermAttempt sendTermSignal() {
        if (isAlreadyTerminated()) {
            return DestroyAttempts.terminated();
        }
        sendSignal(Process::destroy);
        return createTermAttempt();
    }

    private DestroyResult makeCurrentResult() {
        return isAlreadyTerminated() ? DestroyResult.TERMINATED : DestroyResult.STILL_ALIVE;
    }

    private boolean isAlreadyTerminated() {
        try {
            return process.waitFor(0, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            log.error("BUG: waiting for process termination with timeout=0 should never be interrupted");
            return false;
        }
    }

    private ProcessWaiter waiter() {
        return AbstractDestroyAttempt.ProcessWaiter.jre(process);
    }

    protected TermAttempt createTermAttempt() {
        DestroyResult result = makeCurrentResult();
        if (result == DestroyResult.TERMINATED) {
            return DestroyAttempts.terminated();
        }
        return new TermAttemptImpl(this, waiter(), result);
    }

    protected KillAttempt createKillAttempt() {
        DestroyResult result = makeCurrentResult();
        if (result == DestroyResult.TERMINATED) {
            return DestroyAttempts.terminated();
        }
        return new KillAttemptImpl(result, waiter());
    }

    @Override
    public KillAttempt sendKillSignal() {
        if (isAlreadyTerminated()) {
            return DestroyAttempts.terminated();
        }
        sendSignal(Process::destroyForcibly);
        return createKillAttempt();
    }

    private void sendSignal(Consumer<? super Process> signaller) {
        signaller.accept(process);
    }
}
