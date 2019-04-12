/*
 * Copyright 2019 Datadog
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datadog.profiling.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

import com.datadog.profiling.controller.BadConfigurationException;
import com.datadog.profiling.controller.ProfilingSystem;
import com.datadog.profiling.controller.RecordingData;
import com.datadog.profiling.controller.RecordingDataListener;
import com.datadog.profiling.controller.UnsupportedEnvironmentException;

/**
 * Unit tests for testing the {@link ProfilingSystem}.
 */
public class ProfilingSystemTest {

	/**
	 * Ensuring that it can be created and shutdown without problems, if not started.
	 * 
	 * @throws InterruptedException
	 * @throws BadConfigurationException
	 */
	@Test
	public void testProfilingSystemCreation()
			throws UnsupportedEnvironmentException, IOException, InterruptedException, BadConfigurationException {
		final CountDownLatch latch = new CountDownLatch(1);
		RecordingDataListener listener = new RecordingDataListener() {
			@Override
			public void onNewData(RecordingData data) {
				latch.countDown();
			}
		};
		ProfilingSystem system = new ProfilingSystem(listener, Duration.ZERO, Duration.ofMillis(200),
				Duration.ofMillis(100));
		latch.await(4, TimeUnit.SECONDS);
		system.shutdown();
		assertEquals("Got recording data even though the system was never started!", 1, latch.getCount());
	}

	/**
	 * Ensuring it is not possible to configure the profiling system to have greater recording
	 * lengths than the dump periodicity.
	 * 
	 * @throws InterruptedException
	 */
	@Test
	public void testProfilingSystemCreationBadConfig()
			throws UnsupportedEnvironmentException, IOException, InterruptedException {
		RecordingDataListener listener = new RecordingDataListener() {
			@Override
			public void onNewData(RecordingData data) {
				// Don't care...
			}
		};
		try {
			ProfilingSystem system = new ProfilingSystem(listener, Duration.ZERO, Duration.ofMillis(200),
					Duration.ofMillis(400));
			system.shutdown();
		} catch (Exception e) {
			return;
		}

		Assert.fail("An exception should have been thrown at this point!");
	}

	/**
	 * Ensuring that it can be started, and recording data for a few profiling recordings captured.
	 * 
	 * @throws InterruptedException
	 * @throws BadConfigurationException 
	 */
	@Test
	public void testProfilingSystem() throws UnsupportedEnvironmentException, IOException, InterruptedException, BadConfigurationException {
		final CountDownLatch latch = new CountDownLatch(2);
		final List<RecordingData> results = new ArrayList<>();

		RecordingDataListener listener = new RecordingDataListener() {
			@Override
			public void onNewData(RecordingData data) {
				results.add(data);
				latch.countDown();
			}
		};
		ProfilingSystem system = new ProfilingSystem(listener, Duration.ZERO, Duration.ofMillis(200),
				Duration.ofMillis(25));
		system.start();
		latch.await(30, TimeUnit.SECONDS);
		assertTrue("Should have received more data!", results.size() >= 2);
		for (RecordingData data : results) {
			assertTrue("RecordingData should be available before sent out!", data.isAvailable());
		}
		system.shutdown();
		for (RecordingData data : results) {
			data.release();
		}
	}

	/**
	 * Ensuring that it can be started, and recording data for the continuous recording captured.
	 * 
	 * @throws InterruptedException
	 * @throws BadConfigurationException 
	 */
	@Test
	public void testContinuous() throws UnsupportedEnvironmentException, IOException, InterruptedException, BadConfigurationException {
		final CountDownLatch latch = new CountDownLatch(2);
		final List<RecordingData> results = new ArrayList<>();

		RecordingDataListener listener = new RecordingDataListener() {
			@Override
			public void onNewData(RecordingData data) {
				results.add(data);
				latch.countDown();
			}
		};
		final ProfilingSystem system = new ProfilingSystem(listener, Duration.ofDays(1), Duration.ofDays(1),
				Duration.ofSeconds(2));
		system.start();
		Runnable continuousTrigger = new Runnable() {
			@Override
			public void run() {
				for (int i = 0; i < 3; i++) {
					try {
						system.triggerSnapshot();
						try {
							Thread.sleep(200);
						} catch (InterruptedException e) {
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		};
		new Thread(continuousTrigger, "Continuous trigger").start();
		latch.await(30, TimeUnit.SECONDS);
		assertTrue("Should have received more data!", results.size() >= 2);
		for (RecordingData data : results) {
			assertFalse("Should not be getting profiling recordings!", data.getName().startsWith("dd-profiling"));
			assertTrue("RecordingData should be available before sent out!", data.isAvailable());
		}
		system.shutdown();
		for (RecordingData data : results) {
			data.release();
		}
	}
}
