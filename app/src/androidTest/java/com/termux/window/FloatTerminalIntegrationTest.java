package com.termux.window;

import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.termux.terminal.NativeTerminal;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalInputConnection;
import com.termux.view.TerminalView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FloatTerminalIntegrationTest {
    private TermuxFloatService service;
    private TerminalView view;
    private TerminalSession session;
    private TerminalInputConnection input;
    private File capture;

    private void onMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }

    private void await(String title) throws InterruptedException {
        long timeout = InstrumentationRegistry.getArguments().containsKey("gboardExpected") ? 120000 : 30000;
        long deadline = SystemClock.uptimeMillis() + timeout;
        while (true) {
            AtomicBoolean ready = new AtomicBoolean();
            onMain(() -> ready.set(session != null && session.getEmulator() != null
                    && (title == null ? view.hasFocus() : title.equals(session.getTitle()))));
            if (ready.get()) return;
            if (SystemClock.uptimeMillis() > deadline) fail("Float terminal did not become ready");
            Thread.sleep(50);
        }
    }

    @Before public void setUp() throws Exception {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        onMain(() -> context.startService(new Intent(context, TermuxFloatService.class)));
        long deadline = SystemClock.uptimeMillis() + 10000;
        while (service == null) {
            onMain(() -> {
                try {
                    for (android.app.Service candidate : services()) {
                        if (candidate instanceof TermuxFloatService) service = (TermuxFloatService) candidate;
                    }
                } catch (Exception error) {
                    throw new IllegalStateException("Cannot find Float service", error);
                }
            });
            if (service != null) break;
            if (SystemClock.uptimeMillis() > deadline) fail("Float service did not start");
            Thread.sleep(50);
        }
        onMain(() -> {
            try {
                Field window = TermuxFloatService.class.getDeclaredField("mFloatingWindow");
                window.setAccessible(true);
                TermuxFloatView floating = (TermuxFloatView) window.get(service);
                assertNotNull(floating);
                view = floating.getTerminalView();
                session = service.getCurrentSession();
                view.requestFocus();
                capture = File.createTempFile("muxy-float-", ".txt", service.getFilesDir());
            } catch (Exception error) {
                throw new IllegalStateException("Cannot inspect Float service", error);
            }
        });
        await(null);
        onMain(() -> input = (TerminalInputConnection) view.onCreateInputConnection(new EditorInfo()));
    }

    private static java.util.List<android.app.Service> services() throws Exception {
        Class<?> thread = Class.forName("android.app.ActivityThread");
        Object current = thread.getMethod("currentActivityThread").invoke(null);
        Field field = thread.getDeclaredField("mServices");
        field.setAccessible(true);
        java.util.Map<?, ?> values = (java.util.Map<?, ?>) field.get(current);
        java.util.List<android.app.Service> result = new java.util.ArrayList<>();
        for (Object value : values.values()) result.add((android.app.Service) value);
        return result;
    }

    @After public void tearDown() {
        onMain(() -> {
            if (input != null) input.closeConnection();
            if (service != null) {
                service.onDestroy();
                service.stopSelf();
            }
        });
        if (capture != null && capture.exists() && !capture.delete()) fail("Cannot remove Float capture");
    }

    private void capture() throws Exception {
        onMain(() -> session.write("stty iutf8; printf '\\033]2;float-ready\\007'; IFS= read -r value; "
                + "printf '%s' \"$value\" > '" + capture.getAbsolutePath()
                + "'; printf '\\033]2;float-done\\007'\r"));
        await("float-ready");
    }

    private void enter() {
        input.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        input.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
    }

    private void assertCaptured(String expected) throws Exception {
        await("float-done");
        try (FileInputStream stream = new FileInputStream(capture);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
            assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), bytes.toByteArray());
        }
    }

    @Test public void liveGboardTypingReachesTheFloatPty() throws Exception {
        String expected = InstrumentationRegistry.getArguments().getString("gboardExpected");
        org.junit.Assume.assumeNotNull(expected);
        capture();
        onMain(() -> {
            android.view.inputmethod.InputMethodManager manager = (android.view.inputmethod.InputMethodManager)
                    service.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            manager.restartInput(view);
            manager.showSoftInput(view, 0);
        });
        android.util.Log.i("GboardLiveTest", "READY muxy-float");
        assertCaptured(expected);
    }

    @Test public void nativeCoreAndCjkCandidatesReachTheRealFloatPty() throws Exception {
        capture();
        onMain(() -> {
            assertNotNull(NativeTerminal.clusterLayout("한日本👩‍💻", true));
            input.setComposingText("にほん", 1);
            input.setComposingText("日本", 1);
            input.commitText("日本中文한글", 1);
            enter();
        });
        assertCaptured("日本中文한글");
    }

    @Test public void repeatedDeletionAndArrowsPreserveTheNextHangulReplacement() throws Exception {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 34);
        capture();
        onMain(() -> {
            input.setComposingText("안녕", 1);
            input.finishComposingText();
            for (int i = 0; i < 4; i++) input.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
            input.commitText("ㅇ", 1);
            assertTrue(input.replaceText(0, 1, "아", 1, null));
            assertTrue(input.replaceText(0, 1, "안", 1, null));
            assertTrue(view.handleKeyCode(KeyEvent.KEYCODE_DPAD_LEFT, 0));
            int start = input.documentOffset();
            input.commitText("ㅎ", 1);
            assertTrue(input.replaceText(start, start + 1, "하", 1, null));
            assertTrue(input.replaceText(start, start + 1, "한", 1, null));
            enter();
        });
        assertCaptured("안\033[D한");
    }

    @Test public void stylingUpdatesNativeColorsAndClosingDisposesTheCore() {
        onMain(() -> {
            TerminalEmulator emulator = session.getEmulator();
            int original = com.termux.terminal.TerminalColors.COLOR_SCHEME.mDefaultColors[0];
            try {
                com.termux.terminal.TerminalColors.COLOR_SCHEME.mDefaultColors[0] = 0xff123456;
                emulator.resetColors();
                assertEquals(0xff123456, emulator.mColors.mCurrentColors[0]);
            } finally {
                com.termux.terminal.TerminalColors.COLOR_SCHEME.mDefaultColors[0] = original;
                emulator.resetColors();
            }
            service.onDestroy();
            assertTrue(emulator.isClosed());
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        onMain(() -> assertTrue(input.isClosed()));
    }
}
