/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.testretry.internal;

import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class RetryTestResultProcessor implements TestResultProcessor {

    private final TestResultProcessor delegate;

    private final int maxFailures;
    private boolean lastRetry;

    private Map<Object, TestDescriptorInternal> activeDescriptorsById = new ConcurrentHashMap<>();
    private Set<TestName> failedTests = ConcurrentHashMap.newKeySet();
    private Set<TestName> nonExecutedFailedTests = ConcurrentHashMap.newKeySet();
    private Object rootTestDescriptorId;
    private Map<TestName, Throwable> failureTraces = new HashMap<>();

    RetryTestResultProcessor(TestResultProcessor delegate, int maxFailures) {
        this.delegate = delegate;
        this.maxFailures = maxFailures;
    }

    @Override
    public void started(TestDescriptorInternal descriptor, TestStartEvent testStartEvent) {
        nonExecutedFailedTests.remove(new TestName(descriptor.getClassName(), descriptor.getName()));

        if (rootTestDescriptorId == null) {
            rootTestDescriptorId = descriptor.getId();
            activeDescriptorsById.put(descriptor.getId(), descriptor);
            delegate.started(descriptor, testStartEvent);
        } else if (!descriptor.getId().equals(rootTestDescriptorId)) {
            if (!descriptor.isComposite()) {
                activeDescriptorsById.put(descriptor.getId(), descriptor);
            }
            delegate.started(descriptor, testStartEvent);
        }
    }

    @Override
    public void completed(Object testId, TestCompleteEvent testCompleteEvent) {
        activeDescriptorsById.remove(testId);

        if (!testId.equals(rootTestDescriptorId) || lastRun()) {
            delegate.completed(testId, testCompleteEvent);
        }
    }

    @Override
    public void output(Object testId, TestOutputEvent testOutputEvent) {
        delegate.output(testId, testOutputEvent);
    }

    @Override
    public void failure(Object testId, Throwable throwable) {
        TestDescriptorInternal descriptor = activeDescriptorsById.get(testId);
        if (descriptor != null) {
            TestName failedTestName = new TestName(descriptor.getClassName(), descriptor.getName());
            failedTests.add(failedTestName);
            failureTraces.put(failedTestName, throwable);
        }
        delegate.failure(testId, throwable);
    }

    private boolean lastRun() {
        return failedTests.isEmpty() || lastRetry || (maxFailures > 0 && failedTests.size() >= maxFailures);
    }

    public RoundResult getResult() {
        return new RoundResult(copy(failedTests), copy(nonExecutedFailedTests), copy(failureTraces), lastRun());
    }

    private Map<TestName, Throwable> copy(Map<TestName, Throwable> failureTraces) {
        return failureTraces.isEmpty() ? Collections.emptyMap() : new HashMap<>(failureTraces);
    }

    @NotNull
    private Set<TestName> copy(Set<TestName> nonExecutedFailedTests) {
        return nonExecutedFailedTests.isEmpty() ? Collections.emptySet() : new HashSet<>(nonExecutedFailedTests);
    }

    public void reset(boolean lastRetry) {
        if (lastRun()) {
            throw new IllegalStateException("processor has completed");
        }
        nonExecutedFailedTests.clear();
        nonExecutedFailedTests.addAll(failedTests);
        failedTests.clear();
        activeDescriptorsById.clear();
        this.lastRetry = lastRetry;
    }

    static final class RoundResult {

        final Set<TestName> failedTests;
        final Set<TestName> nonRetriedTests;
        final Map<TestName, Throwable> failureDetails;
        final boolean lastRound;

        public RoundResult(Set<TestName> failedTests, Set<TestName> nonRetriedTests, Map<TestName, Throwable> failureDetails, boolean lastRound) {
            this.failedTests = failedTests;
            this.nonRetriedTests = nonRetriedTests;
            this.failureDetails = failureDetails;
            this.lastRound = lastRound;
        }
    }
}
