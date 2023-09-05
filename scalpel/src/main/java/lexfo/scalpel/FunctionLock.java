package lexfo.scalpel;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class FunctionLock {

	private final ReentrantLock lock = new ReentrantLock(false);

	public <T> T invoke(Supplier<T> operation) {
		lock.lock();
		try {
			return operation.get();
		} finally {
			lock.unlock();
		}
	}

	public void run(Runnable operation) {
		lock.lock();
		try {
			operation.run();
		} finally {
			lock.unlock();
		}
	}
}
