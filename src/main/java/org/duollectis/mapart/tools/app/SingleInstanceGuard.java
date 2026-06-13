package org.duollectis.mapart.tools.app;

import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Защита от запуска нескольких экземпляров приложения.
 *
 * <p>Механизм: первый экземпляр захватывает {@link ServerSocket} на localhost:порт
 * и слушает входящие соединения в фоновом потоке. Если второй экземпляр пытается
 * запуститься — он подключается, отправляет {@code FOCUS} и ждёт ответа {@code ACK}.
 * Если ответ получен — приложение уже работает, второй экземпляр завершается.
 * Если ответа нет (порт занят сторонней программой) — второй экземпляр ждёт
 * освобождения порта и занимает его сам.
 */
@UtilityClass
public class SingleInstanceGuard {

	private static final int PORT = 47832;
	private static final String FOCUS_SIGNAL = "FOCUS";
	private static final String ACK_SIGNAL = "ACK";
	private static final int CONNECT_TIMEOUT_MS = 200;
	private static final int READ_TIMEOUT_MS = 500;
	private static final int ACQUIRE_TIMEOUT_MS = 6_000;
	private static final int RETRY_INTERVAL_MS = 300;

	private static ServerSocket serverSocket;

	/**
	 * Явно освобождает серверный сокет, чтобы следующий экземпляр мог занять порт
	 * немедленно, не дожидаясь завершения JVM. Должен вызываться при закрытии окна,
	 * до {@code System.exit()}, пока shutdown hook Discord RPC ещё работает.
	 */
	public static void release() {
		if (serverSocket == null) {
			return;
		}

		try {
			serverSocket.close();
		} catch (IOException ignored) {}
	}

	/**
	 * Пытается занять серверный сокет на localhost.
	 *
	 * <p>Если порт свободен — этот экземпляр первый, запускает listener-поток
	 * и возвращает {@code true}. Если порт занят нашим процессом — отправляет
	 * сигнал {@code FOCUS}, получает {@code ACK} и возвращает {@code false}.
	 * Если порт занят сторонней программой (нет {@code ACK}) — ждёт освобождения
	 * и занимает его сам.
	 *
	 * @return {@code true} — первый экземпляр, можно продолжать запуск;
	 *         {@code false} — приложение уже запущено, сигнал отправлен
	 */
	public static boolean tryAcquire() {
		if (tryBindPort()) {
			return true;
		}

		if (sendFocusAndWaitAck()) {
			return false;
		}

		// Порт занят, но ACK не получен — сторонняя программа или умирающий процесс.
		// Ждём освобождения порта и занимаем его сами.
		return waitAndBind();
	}

	/**
	 * Регистрирует колбэк, который будет вызван при получении сигнала {@code FOCUS}
	 * от второго экземпляра. Должен вызываться после успешного {@link #tryAcquire()}.
	 *
	 * @param onFocus действие для вывода окна на передний план (выполняется в EDT)
	 */
	public static void registerFocusHandler(Runnable onFocus) {
		Thread listener = new Thread(() -> {
			while (!serverSocket.isClosed()) {
				try (Socket client = serverSocket.accept()) {
					InputStream input = client.getInputStream();
					OutputStream output = client.getOutputStream();

					byte[] buf = input.readNBytes(FOCUS_SIGNAL.length());
					String signal = new String(buf);

					if (FOCUS_SIGNAL.equals(signal)) {
						output.write(ACK_SIGNAL.getBytes());
						output.flush();
						javax.swing.SwingUtilities.invokeLater(onFocus);
					}
				} catch (IOException ignored) {}
			}
		}, "single-instance-listener");

		listener.setDaemon(true);
		listener.start();
	}

	private static boolean tryBindPort() {
		try {
			serverSocket = new ServerSocket(PORT, 1, InetAddress.getLoopbackAddress());
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	private static boolean sendFocusAndWaitAck() {
		try (Socket socket = new Socket()) {
			socket.connect(
				new InetSocketAddress(InetAddress.getLoopbackAddress(), PORT),
				CONNECT_TIMEOUT_MS
			);
			socket.setSoTimeout(READ_TIMEOUT_MS);

			OutputStream output = socket.getOutputStream();
			InputStream input = socket.getInputStream();

			output.write(FOCUS_SIGNAL.getBytes());
			output.flush();

			byte[] ack = input.readNBytes(ACK_SIGNAL.length());
			return ACK_SIGNAL.equals(new String(ack));
		} catch (IOException e) {
			return false;
		}
	}

	private static boolean waitAndBind() {
		long deadline = System.currentTimeMillis() + ACQUIRE_TIMEOUT_MS;

		while (System.currentTimeMillis() < deadline) {
			try {
				Thread.sleep(RETRY_INTERVAL_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}

			if (tryBindPort()) {
				return true;
			}
		}

		return false;
	}
}
