package com.sniptext.domain.template.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** 템플릿 동시성 테스트용. 여러 작업을 같은 순간에 출발시키고 넘긴 순서대로 결과를 모은다 */
final class ConcurrentRunner {

	private ConcurrentRunner() {
	}

	@SafeVarargs
	static List<String> runAtOnce(Callable<String>... tasks) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(tasks.length);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<String>> futures = new ArrayList<>();
			for (Callable<String> task : tasks) {
				futures.add(executor.submit(() -> {
					start.await();
					return task.call();
				}));
			}
			start.countDown();
			List<String> results = new ArrayList<>();
			for (Future<String> future : futures) {
				results.add(future.get(10, TimeUnit.SECONDS));
			}
			return results;
		} finally {
			executor.shutdownNow();
		}
	}

}
