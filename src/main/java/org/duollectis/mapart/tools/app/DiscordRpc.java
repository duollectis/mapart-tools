package org.duollectis.mapart.tools.app;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Минималистичный Discord Rich Presence клиент без внешних зависимостей.
 * Поддерживает Linux/macOS (Unix socket) и Windows (Named Pipe).
 * Если Discord не запущен — все вызовы молча игнорируются.
 *
 * <p>Приложение меняет поля {@link #currentDetails} и {@link #currentState} напрямую.
 * Polling-цикл каждые {@link #SEND_INTERVAL_MS} мс сравнивает их с последними
 * отправленными значениями и отправляет SET_ACTIVITY только при изменении.
 * При shutdown — shutdown hook дожидается окончания текущего интервала и отправляет
 * CLEAR_ACTIVITY. Консоль при этом не зависает.
 */
@UtilityClass
public class DiscordRpc {

	private static final String APP_ID = "1514957646690783232";
	private static final String LARGE_IMAGE_KEY = "mapart_logo";
	private static final String LARGE_IMAGE_TEXT = "MapArt Tools";

	private static final int OPCODE_HANDSHAKE = 0;
	private static final int OPCODE_FRAME = 1;
	private static final int OPCODE_CLOSE = 2;

	// Интервал polling-цикла — Discord блокирует при слишком частых SET_ACTIVITY
	private static final long SEND_INTERVAL_MS = 5_000L;
	// Пауза после READY перед первым SET_ACTIVITY
	private static final int READY_DELAY_MS = 500;

	// volatile — читается/пишется из разных потоков; замена через compareAndSet не нужна,
	// т.к. все мутации происходят под synchronized-методами или в одном потоке init
	private static volatile IpcTransport transport;
	private static final AtomicBoolean connected = new AtomicBoolean();
	private static final AtomicBoolean enabled = new AtomicBoolean(true);

	// volatile — записывается в EDT, читается в polling-потоке
	private static volatile long sessionStart;

	// Текущие поля presence — приложение меняет их напрямую
	private static final AtomicReference<String> currentDetails = new AtomicReference<>("MapArt Tools");
	private static final AtomicReference<String> currentState = new AtomicReference<>("Idle");

	// Последние отправленные значения — для сравнения в polling-цикле
	private static final AtomicReference<String> lastSentDetails = new AtomicReference<>(null);
	private static final AtomicReference<String> lastSentState = new AtomicReference<>(null);

	// Время последней успешной отправки SET_ACTIVITY (мс)
	private static final AtomicLong lastSentAt = new AtomicLong(0);

	// Polling-поток, отправляющий presence каждые SEND_INTERVAL_MS
	private static Thread pollingThread;

	/**
	 * Инициализирует подключение к Discord IPC в фоновом потоке.
	 * Перед новым подключением закрывает предыдущее, чтобы не плодить зомби-соединения
	 * при быстром включении/выключении тумблера.
	 */
	public static synchronized void init() {
		if (!enabled.get()) {
			return;
		}

		shutdown();

		sessionStart = System.currentTimeMillis() / 1000L;
		Thread thread = new Thread(DiscordRpc::connect, "discord-rpc-init");
		thread.setDaemon(true);
		thread.start();
	}

	/**
	 * Включает или выключает Discord RPC.
	 * При выключении немедленно разрывает соединение.
	 * При включении — инициирует новое подключение в фоне.
	 *
	 * @param value {@code true} — включить, {@code false} — выключить
	 */
	public static void setEnabled(boolean value) {
		enabled.set(value);

		if (value) {
			init();
		} else {
			shutdown();
		}
	}

	/** Устанавливает поле details в Discord presence. */
	public static void setDetails(String details) {
		currentDetails.set(details);
	}

	/** Устанавливает поле state в Discord presence. */
	public static void setState(String state) {
		currentState.set(state);
	}

	/** Обновляет оба поля presence одновременно. */
	public static void setPresence(String details, String state) {
		currentDetails.set(details);
		currentState.set(state);
	}

	/** Сбрасывает presence на состояние простоя. */
	public static void setIdle() {
		setPresence("MapArt Tools", "Idle");
	}

	/** Обновляет статус: идёт конвертация изображения. */
	public static void setConverting(int percent) {
		setPresence("Converting image", "Progress: " + percent + "%");
	}

	/** Обновляет статус: идёт экспорт схематики. */
	public static void setExporting() {
		setPresence("Exporting schematic", "Writing files...");
	}

	/** Обновляет статус: идёт импорт схематики. */
	public static void setImporting() {
		setPresence("Importing schematic", "Reading files...");
	}

	/**
	 * Закрывает текущее соединение с Discord IPC асинхронно.
	 * Используется при отключении RPC через тоггл или перед переподключением.
	 * Не завершает процесс — только останавливает polling и закрывает сокет.
	 */
	public static synchronized void shutdown() {
		IpcTransport toClose = detachTransport();

		if (toClose == null) {
			return;
		}

		Thread worker = new Thread(() -> {
			try {
				clearActivity(toClose);
				sendFrameTo(toClose, OPCODE_CLOSE, "{}");
				toClose.close();
			} catch (Exception ignored) {}
		}, "discord-rpc-close");

		worker.setDaemon(true);
		worker.start();
	}

	/**
	 * Регистрирует shutdown hook для корректного завершения Discord IPC при выходе.
	 * Должен вызываться при закрытии окна ДО вызова {@code System.exit()}.
	 * Hook дожидается окончания текущего 5-секундного интервала, отправляет
	 * CLEAR_ACTIVITY и закрывает сокет. Консоль освобождается мгновенно.
	 */
	public static synchronized void registerShutdownHook() {
		IpcTransport toClose = detachTransport();

		if (toClose == null) {
			return;
		}

		long elapsed = System.currentTimeMillis() - lastSentAt.get();
		long remaining = Math.max(0L, SEND_INTERVAL_MS - elapsed);

		Thread hook = new Thread(() -> {
			try {
				if (remaining > 0) {
					Thread.sleep(remaining);
				}

				clearActivity(toClose);
				sendFrameTo(toClose, OPCODE_CLOSE, "{}");
				toClose.close();
			} catch (Exception ignored) {}
		}, "discord-rpc-shutdown-hook");

		Runtime.getRuntime().addShutdownHook(hook);
	}

	/**
	 * Атомарно снимает текущий транспорт с «боевого» поля, останавливает polling
	 * и сбрасывает состояние отправки. Вызывается строго под монитором класса.
	 *
	 * @return снятый транспорт, либо {@code null} если соединения не было
	 */
	private static IpcTransport detachTransport() {
		connected.set(false);
		stopPolling();
		resetSentState();

		IpcTransport detached = transport;
		transport = null;

		return detached;
	}

	/**
	 * Выполняет подключение к Discord IPC, handshake и запуск polling-цикла.
	 * Не синхронизирован намеренно — содержит блокирующие I/O и sleep,
	 * которые заблокировали бы монитор класса на время подключения.
	 * Защита от гонок обеспечивается проверками {@code enabled} и {@code connected}
	 * вокруг каждой критической секции.
	 *
	 * <p>Перед запуском polling проверяем, что {@code transport} всё ещё указывает
	 * на наш {@code newTransport} — это защита от двойного polling при быстром
	 * double-toggle: если за время sleep пришёл новый {@code init()}, он уже
	 * заменил {@code transport} на свой объект, и мы тихо выходим.
	 */
	private static void connect() {
		try {
			IpcTransport newTransport = openTransport();

			if (!enabled.get()) {
				newTransport.close();
				return;
			}

			synchronized (DiscordRpc.class) {
				if (!enabled.get()) {
					newTransport.close();
					return;
				}

				transport = newTransport;
			}

			sendHandshakeTo(newTransport);

			JsonObject response = readFrameFrom(newTransport);
			String event = response != null && response.has("evt")
				? response.get("evt").getAsString()
				: "";

			if ("READY".equals(event) && enabled.get()) {
				connected.set(true);
				Thread.sleep(READY_DELAY_MS);

				synchronized (DiscordRpc.class) {
					// Если за время sleep пришёл новый init() или shutdown() —
					// transport уже другой объект или null. Запускать polling нельзя:
					// это породит два конкурирующих polling-потока.
					// Сбрасываем connected, чтобы не оставлять инвариант нарушенным.
					if (enabled.get() && transport == newTransport) {
						startPolling();
					} else {
						connected.set(false);
					}
				}
			}
		} catch (Exception e) {
			// Discord недоступен — молча игнорируем
		}
	}

	/**
	 * Запускает polling-цикл: каждые {@link #SEND_INTERVAL_MS} мс сравнивает
	 * текущие поля presence с последними отправленными и отправляет SET_ACTIVITY
	 * только если что-то изменилось.
	 */
	private static synchronized void startPolling() {
		stopPolling();

		pollingThread = new Thread(() -> {
			while (!Thread.currentThread().isInterrupted() && connected.get()) {
				try {
					Thread.sleep(SEND_INTERVAL_MS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}

				if (!connected.get()) {
					break;
				}

				String details = currentDetails.get();
				String state = currentState.get();

				boolean changed = !Objects.equals(details, lastSentDetails.get())
					|| !Objects.equals(state, lastSentState.get());

				if (!changed) {
					continue;
				}

				try {
					sendFrame(OPCODE_FRAME, buildPresenceJson(details, state));
					lastSentAt.set(System.currentTimeMillis());
					lastSentDetails.set(details);
					lastSentState.set(state);
				} catch (Exception e) {
					connected.set(false);
				}
			}
		}, "discord-rpc-polling");

		pollingThread.setDaemon(true);
		pollingThread.start();
	}

	private static synchronized void stopPolling() {
		if (pollingThread != null) {
			pollingThread.interrupt();
			pollingThread = null;
		}
	}

	private static void resetSentState() {
		lastSentDetails.set(null);
		lastSentState.set(null);
	}

	private static IpcTransport openTransport() throws IOException {
		String os = System.getProperty("os.name", "").toLowerCase();

		return os.contains("win")
			? openWindowsPipe()
			: openUnixSocket();
	}

	private static IpcTransport openUnixSocket() throws IOException {
		String xdgRuntime = System.getenv("XDG_RUNTIME_DIR");
		String tmpDir = System.getProperty("java.io.tmpdir", "/tmp");

		String[] bases = xdgRuntime != null
			? new String[]{xdgRuntime, xdgRuntime + "/snap.discord", tmpDir, "/tmp"}
			: new String[]{tmpDir, "/tmp"};

		for (String base : bases) {
			if (base == null) {
				continue;
			}

			for (int i = 0; i < 10; i++) {
				Path socketPath = Path.of(base + "/discord-ipc-" + i);

				try {
					SocketChannel ch = SocketChannel.open(UnixDomainSocketAddress.of(socketPath));
					ch.configureBlocking(true);
					return new UnixTransport(ch);
				} catch (IOException ignored) {}
			}
		}

		throw new IOException("Discord IPC socket не найден");
	}

	/**
	 * На Windows Discord слушает Named Pipe {@code \\.\pipe\discord-ipc-N}.
	 * Java умеет открывать Named Pipe через {@link RandomAccessFile} — это единственный
	 * способ без нативного кода или сторонних библиотек.
	 */
	private static IpcTransport openWindowsPipe() throws IOException {
		for (int i = 0; i < 10; i++) {
			String pipePath = "\\\\.\\pipe\\discord-ipc-" + i;

			try {
				RandomAccessFile pipe = new RandomAccessFile(pipePath, "rw");
				return new WindowsPipeTransport(pipe);
			} catch (IOException ignored) {}
		}

		throw new IOException("Discord Named Pipe не найден");
	}

	private static void sendHandshakeTo(IpcTransport target) throws IOException {
		JsonObject payload = new JsonObject();
		payload.addProperty("v", 1);
		payload.addProperty("client_id", APP_ID);
		sendFrameTo(target, OPCODE_HANDSHAKE, payload.toString());
	}

	private static void clearActivity(IpcTransport target) throws IOException {
		JsonObject args = new JsonObject();
		args.addProperty("pid", ProcessHandle.current().pid());

		JsonObject payload = new JsonObject();
		payload.addProperty("cmd", "SET_ACTIVITY");
		payload.add("args", args);
		payload.addProperty("nonce", UUID.randomUUID().toString());

		sendFrameTo(target, OPCODE_FRAME, payload.toString());
	}

	private static String buildPresenceJson(String details, String state) {
		JsonObject timestamps = new JsonObject();
		timestamps.addProperty("start", sessionStart);

		JsonObject assets = new JsonObject();
		assets.addProperty("large_image", LARGE_IMAGE_KEY);
		assets.addProperty("large_text", LARGE_IMAGE_TEXT);

		JsonObject activity = new JsonObject();
		activity.addProperty("details", details);
		activity.addProperty("state", state);
		activity.add("timestamps", timestamps);
		activity.add("assets", assets);

		JsonObject args = new JsonObject();
		args.addProperty("pid", ProcessHandle.current().pid());
		args.add("activity", activity);

		JsonObject payload = new JsonObject();
		payload.addProperty("cmd", "SET_ACTIVITY");
		payload.add("args", args);
		payload.addProperty("nonce", UUID.randomUUID().toString());

		return payload.toString();
	}

	private static void sendFrame(int opcode, String json) throws IOException {
		IpcTransport current = transport;

		if (current == null) {
			return;
		}

		sendFrameTo(current, opcode, json);
	}

	private static void sendFrameTo(IpcTransport target, int opcode, String json) throws IOException {
		byte[] data = json.getBytes(StandardCharsets.UTF_8);
		ByteBuffer buffer = ByteBuffer.allocate(8 + data.length);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		buffer.putInt(opcode);
		buffer.putInt(data.length);
		buffer.put(data);
		target.write(buffer.array());
	}

	// Читает фрейм из явно переданного транспорта, а не из статического поля,
	// чтобы избежать NPE при гонке с shutdown() во время handshake.
	private static JsonObject readFrameFrom(IpcTransport source) {
		try {
			byte[] header = source.read(8);

			if (header == null) {
				return null;
			}

			ByteBuffer headerBuf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
			headerBuf.getInt(); // opcode — не используем
			int length = headerBuf.getInt();

			byte[] body = source.read(length);

			if (body == null) {
				return null;
			}

			String json = new String(body, StandardCharsets.UTF_8);
			return JsonParser.parseString(json).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Транспортный слой
	// ─────────────────────────────────────────────────────────────────────────

	private interface IpcTransport {
		void write(byte[] data) throws IOException;
		byte[] read(int length) throws IOException;
		void close() throws IOException;
	}

	private static final class UnixTransport implements IpcTransport {

		private final SocketChannel channel;
		private final InputStream input;
		private final OutputStream output;

		UnixTransport(SocketChannel channel) {
			this.channel = channel;
			this.input = Channels.newInputStream(channel);
			this.output = Channels.newOutputStream(channel);
		}

		@Override
		public void write(byte[] data) throws IOException {
			output.write(data);
		}

		@Override
		public byte[] read(int length) throws IOException {
			byte[] buf = new byte[length];
			int offset = 0;

			while (offset < length) {
				int n = input.read(buf, offset, length - offset);

				if (n < 0) {
					return null;
				}

				offset += n;
			}

			return buf;
		}

		@Override
		public void close() throws IOException {
			channel.close();
		}
	}

	private static final class WindowsPipeTransport implements IpcTransport {

		private final RandomAccessFile pipe;

		WindowsPipeTransport(RandomAccessFile pipe) {
			this.pipe = pipe;
		}

		@Override
		public void write(byte[] data) throws IOException {
			pipe.write(data);
		}

		@Override
		public byte[] read(int length) throws IOException {
			byte[] buf = new byte[length];
			pipe.readFully(buf);
			return buf;
		}

		@Override
		public void close() throws IOException {
			pipe.close();
		}
	}
}
