package lexfo.scalpel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Supplier;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class IO {

	private static final ObjectMapper mapper = new ObjectMapper();

	private static final ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();

	@FunctionalInterface
	public static interface IOCallable<T> {
		T call() throws IOException, InterruptedException;
	}

	// Wrapper to catch IOExceptions and rethrow them as RuntimeExceptions
	public static final <T> T ioWrap(IOCallable<T> supplier) {
		try {
			return supplier.call();
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public static final <T> T ioWrap(
		IOCallable<T> supplier,
		Supplier<T> defaultSupplier
	) {
		try {
			return supplier.call();
		} catch (IOException | InterruptedException e) {
			return defaultSupplier.get();
		}
	}

	public static <T> T readJSON(File file, Class<T> clazz) {
		return ioWrap(() -> mapper.readValue(file, clazz));
	}

	public static <T> T readJSON(String value, Class<T> clazz) {
		return ioWrap(() -> mapper.readValue(value, clazz));
	}

	public static void writeJSON(File file, Object obj) {
		ioWrap(() -> {
			writer.writeValue(file, obj);

			// Add a newline at the end of the file
			new FileWriter(file, true).append('\n').close();
			return null;
		});
	}

	public static void writeFile(String path, String content) {
		ioWrap(() -> {
			FileWriter writer = new FileWriter(path);
			writer.write(content);
			writer.close();
			return null;
		});
	}
}
