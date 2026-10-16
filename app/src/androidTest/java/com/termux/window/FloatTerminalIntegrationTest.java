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
    private TermuxFloatView floating;

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
                floating = (TermuxFloatView) window.get(service);
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
        if ("true".equals(InstrumentationRegistry.getArguments().getString("gboardSubmittedContext"))) {
            onMain(() -> { input.commitText("                  ", 1); enter(); });
        }
        boolean alternate = "true".equals(InstrumentationRegistry.getArguments().getString("gboardAlternateScreen"));
        if (alternate) onMain(() -> session.write("printf '\\033[?1049h'\r"));
        capture();
        if (alternate) onMain(() -> assertTrue(session.getEmulator().isAlternateBufferActive()));
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

    private void assertEmptyEditorBackspacesPreserveHangul(boolean alternate) throws Exception {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 34);
        onMain(() -> { input.commitText("   ", 1); enter(); });
        if (alternate) onMain(() -> session.write("printf '\\033[?1049h'\r"));
        capture();
        onMain(() -> {
            assertEquals(alternate, session.getEmulator().isAlternateBufferActive());
            assertEquals(3, input.documentOffset());
            for (int i = 0; i < 8; i++) {
                input.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
                input.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
            }
            assertEquals(0, input.documentOffset());
            input.commitText("ㅇ", 1);
            assertTrue(input.replaceText(0, 1, "아", 1, null));
            assertTrue(input.replaceText(0, 1, "안", 1, null));
            enter();
        });
        assertCaptured("안");
    }

    @Test public void emptyPromptBackspacesPreserveTheNextHangulSyllable() throws Exception {
        assertEmptyEditorBackspacesPreserveHangul(false);
    }

    @Test public void alternateScreenBackspacesPreserveTheNextHangulSyllable() throws Exception {
        assertEmptyEditorBackspacesPreserveHangul(true);
    }

    private boolean keyboardVisible() {
        android.app.UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        for (android.view.accessibility.AccessibilityWindowInfo window : automation.getWindows()) {
            if (window.getType() == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD) return true;
        }
        return false;
    }

    private void awaitKeyboard(boolean visible) throws InterruptedException {
        long deadline = SystemClock.uptimeMillis() + 10000;
        while (keyboardVisible() != visible) {
            if (SystemClock.uptimeMillis() > deadline) fail("Float keyboard visibility did not become " + visible);
            Thread.sleep(50);
        }
    }

    private void overlayTap() {
        onMain(() -> {
            int[] origin = new int[2];
            view.getLocationOnScreen(origin);
            int[] parent = new int[2];
            floating.getLocationOnScreen(parent);
            long time = SystemClock.uptimeMillis();
            for (int action : new int[]{android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP}) {
                android.view.MotionEvent event = android.view.MotionEvent.obtain(time, time + action * 20,
                        action, origin[0] + 10, origin[1] + 10, 0);
                event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
                event.setLocation(origin[0] - parent[0] + 10, origin[1] - parent[1] + 10);
                try { assertTrue(floating.dispatchTouchEvent(event)); } finally { event.recycle(); }
            }
        });
    }

    @Test public void overlaySingleTapOpensKeyboardAndDoubleTapSendsOnlyOneClick() throws Exception {
        android.app.UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        android.accessibilityservice.AccessibilityServiceInfo info = automation.getServiceInfo();
        int previous = info.flags;
        info.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        automation.setServiceInfo(info);
        try {
            onMain(floating::showTouchKeyboard);
            awaitKeyboard(true);
            for (boolean alternate : new boolean[]{false, true}) {
                for (int mode : new int[]{0, 1000, 1002, 1003}) {
                    String output = "\033[?1000l\033[?1002l\033[?1003l\033[?1006h"
                            + (alternate ? "\033[?1049h" : "\033[?1049l")
                            + (mode == 0 ? "" : "\033[?" + mode + "h");
                    capture();
                    onMain(() -> {
                        byte[] bytes = output.getBytes(StandardCharsets.UTF_8);
                        session.getEmulator().append(bytes, bytes.length);
                        floating.hideTouchKeyboard();
                    });
                    awaitKeyboard(false);
                    Thread.sleep(500);
                    overlayTap();
                    Thread.sleep(70);
                    overlayTap();
                    Thread.sleep(android.view.ViewConfiguration.getDoubleTapTimeout() + 200);
                    assertFalse("A Float double tap opened the keyboard", keyboardVisible());
                    overlayTap();
                    awaitKeyboard(true);
                    onMain(this::enter);
                    assertCaptured(mode == 0 ? "" : "\033[<0;1;1M\033[<0;1;1m");
                }
            }
        } finally {
            info.flags = previous;
            automation.setServiceInfo(info);
        }
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
