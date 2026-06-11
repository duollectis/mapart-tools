package org.duollectis.mapart.tools.gui.util;

import lombok.experimental.UtilityClass;
import org.duollectis.mapart.tools.gui.widget.AppLogPane;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Перехватчик стандартных потоков вывода {@code System.out} и {@code System.err}.
 *
 * <p>После вызова {@link #attach(AppLogPane)} все записи, поступающие в оба потока,
 * дублируются в переданный виджет {@link AppLogPane}. Строки стектрейса
 * (начинающиеся с {@code \tat} или {@code \tCaused by:}) накапливаются и
 * отправляются в {@link AppLogPane#appendException} единым свёрнутым блоком.
 * Оригинальные потоки при этом продолжают работать в штатном режиме.
 */
@UtilityClass
public class AppLogger {

	private static AppLogPane targetPane;

	/**
	 * Подключает виджет лога и устанавливает перехватчики на {@code System.out} и {@code System.err}.
	 *
	 * @param pane виджет, в который будут дублироваться все записи
	 */
	public static void attach(AppLogPane pane) {
		targetPane = pane;

		PrintStream originalOut = System.out;
		PrintStream originalErr = System.err;

		System.setOut(new LoggingPrintStream(originalOut, false));
		System.setErr(new LoggingPrintStream(originalErr, true));
	}

	// ── Внутренний перехватчик потока ─────────────────────────────────────────

	private static final class LoggingPrintStream extends PrintStream {

		private final boolean isError;

		/** Буфер для накопления строк стектрейса текущего исключения. */
		private String pendingExceptionHeader;
		private final List<String> pendingStackLines = new ArrayList<>();

		LoggingPrintStream(OutputStream original, boolean isError) {
			super(original, true);
			this.isError = isError;
		}

		@Override
		public void println(String line) {
			super.println(line);
			routeLine(line == null ? "null" : line);
		}

		@Override
		public void println(Object obj) {
			String line = String.valueOf(obj);
			super.println(line);
			routeLine(line);
		}

		@Override
		public void println() {
			super.println();
		}

		private void routeLine(String line) {
			if (targetPane == null) {
				return;
			}

			if (isStackTraceLine(line)) {
				pendingStackLines.add(line);
				return;
			}

			flushPendingException();

			if (isError && isExceptionHeader(line)) {
				pendingExceptionHeader = line;
				return;
			}

			targetPane.appendLine(line);
		}

		private void flushPendingException() {
			if (pendingExceptionHeader == null) {
				return;
			}

			targetPane.appendException(pendingExceptionHeader, new ArrayList<>(pendingStackLines));
			pendingExceptionHeader = null;
			pendingStackLines.clear();
		}

		private static boolean isStackTraceLine(String line) {
			String trimmed = line.stripLeading();
			return trimmed.startsWith("at ")
				|| trimmed.startsWith("Caused by:")
				|| trimmed.startsWith("... ");
		}

		private static boolean isExceptionHeader(String line) {
			return line.contains("Exception")
				|| line.contains("Error:")
				|| line.endsWith("Error");
		}
	}
}
