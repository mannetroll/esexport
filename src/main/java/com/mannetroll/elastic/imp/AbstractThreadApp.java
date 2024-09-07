package com.mannetroll.elastic.imp;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author mannetroll
 */
public abstract class AbstractThreadApp {
	private static final Logger LOG = LogManager.getLogger(AbstractThreadApp.class);
	// Create blocking queue with size equal to number of workers.
	private BlockingQueue<Job> jobs;
	private Semaphore semaphore;
	private ExecutorService executor;
	private int THREADS;

	public void startWorkers(int THREADS) {
		this.THREADS = THREADS;
		this.jobs = new ArrayBlockingQueue<Job>(THREADS);
		this.executor = Executors.newFixedThreadPool(THREADS);
		this.semaphore = new Semaphore(THREADS);
		LOG.info("THREADS: " + THREADS);
		for (int i = 0; i < THREADS; i++) {
			executor.submit(new Worker());
		}
	}

	public void addJob(Job job) throws InterruptedException {
		jobs.put(job);
		LOG.info("Acquire: semaphores: " + semaphore.availablePermits());
		semaphore.acquire();
	}

	public void finish() throws InterruptedException {
		LOG.info("availablePermits: " + semaphore.availablePermits());
		final int running = THREADS - semaphore.availablePermits();
		LOG.info("Waiting 1 hour for threads to finish... " + running);
		semaphore.tryAcquire(THREADS, 60, TimeUnit.MINUTES);
		LOG.info("availablePermits: " + semaphore.availablePermits());
	}

	protected class Worker implements Runnable {
		@Override
		public void run() {
			try {
				while (true) {
					LOG.info("Take: " + Thread.currentThread().getName());
					Job job = jobs.take();
					if (job == null) {
						// null means workers must shutdown
						LOG.info("Shutdown: " + Thread.currentThread().getName());
						return;
					}
					job.execute();
					LOG.info("Done: " + Thread.currentThread().getName());
					// Notify master thread that a job was done.
					semaphore.release();
					LOG.info("Released: semaphores: " + semaphore.availablePermits());
				}
			} catch (InterruptedException | IOException e) {
				LOG.error(e.getMessage(), e);
			}
		}
	}

	public abstract class Job {
		public abstract void execute() throws IOException;
	}

}
